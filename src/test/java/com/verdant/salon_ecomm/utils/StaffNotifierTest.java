package com.verdant.salon_ecomm.utils;

import com.verdant.salon_ecomm.dtos.notification.NotificationCreateDto;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.accounts.AccountStatus;
import com.verdant.salon_ecomm.models.enums.notification.NotificationPriority;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.ReferenceType;
import com.verdant.salon_ecomm.repositories.UserRepository;
import com.verdant.salon_ecomm.services.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffNotifierTest {

    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;
    @InjectMocks StaffNotifier staffNotifier;

    private final UUID refId = UUID.randomUUID();
    private final NotificationType type = NotificationType.values()[0];
    private final ReferenceType refType = ReferenceType.values()[0];

    @Test
    void notifiesOnlyActiveStaffInOneBulkInsert() {
        List<UUID> staff = List.of(UUID.randomUUID(), UUID.randomUUID());
        when(userRepository.findIdsByRoleInAndStatus(StaffNotifier.STAFF_ROLES, AccountStatus.ACTIVE)).thenReturn(staff);
        User actor = User.builder().id(UUID.randomUUID()).fullName("Ana Admin").build();

        staffNotifier.notify(refType, refId, type, "Title", "Message", actor);

        ArgumentCaptor<NotificationCreateDto> template = ArgumentCaptor.forClass(NotificationCreateDto.class);
        verify(notificationService).createForUsers(eq(staff), template.capture());
        verify(notificationService, never()).create(any());
        assertThat(template.getValue().title()).isEqualTo("Title");
        assertThat(template.getValue().priority()).isEqualTo(NotificationPriority.INFO);
        assertThat(template.getValue().actorId()).isEqualTo(actor.getId());
        assertThat(template.getValue().actorName()).isEqualTo("Ana Admin");
    }

    @Test
    void noStaffMeansNoInsert() {
        when(userRepository.findIdsByRoleInAndStatus(any(), any())).thenReturn(List.of());

        staffNotifier.notify(refType, refId, type, "T", "M", (User) null);

        verifyNoInteractions(notificationService);
    }

    @Test
    void bulkFailureFallsBackToOneByOneAndOneBadRowDoesNotStopTheRest() {
        UUID a = UUID.randomUUID(), bad = UUID.randomUUID(), c = UUID.randomUUID();
        when(userRepository.findIdsByRoleInAndStatus(any(), any())).thenReturn(List.of(a, bad, c));
        doThrow(new RuntimeException("bulk failed")).when(notificationService).createForUsers(any(), any());
        doThrow(new RuntimeException("bad row")).when(notificationService)
            .create(argThat(dto -> bad.equals(dto.userId())));

        assertThatCode(() -> staffNotifier.notify(refType, refId, type, "T", "M", null, null))
            .doesNotThrowAnyException();

        ArgumentCaptor<NotificationCreateDto> sent = ArgumentCaptor.forClass(NotificationCreateDto.class);
        verify(notificationService, times(3)).create(sent.capture());
        assertThat(sent.getAllValues()).extracting(NotificationCreateDto::userId).containsExactly(a, bad, c);
    }

    @Test
    void failingStaffLookupNeverThrows() {
        when(userRepository.findIdsByRoleInAndStatus(any(), any())).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> staffNotifier.notify(refType, refId, type, "T", "M", (User) null))
            .doesNotThrowAnyException();
        verifyNoInteractions(notificationService);
    }
}
