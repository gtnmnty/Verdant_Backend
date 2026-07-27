package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.notification.*;
import com.verdant.salon_ecomm.models.enums.notification.NotificationReadFilter;
import com.verdant.salon_ecomm.models.enums.notification.NotificationSortField;
import com.verdant.salon_ecomm.models.enums.notification.NotificationType;
import com.verdant.salon_ecomm.models.enums.notification.SortDirection;
import com.verdant.salon_ecomm.entities.User;
import com.verdant.salon_ecomm.services.NotificationPublisher;
import com.verdant.salon_ecomm.services.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class NotificationResolver {

    private final NotificationService notificationService;
    private final NotificationPublisher notificationPublisher;

    // ── Queries ────────────────────────────────────────────

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public NotificationGraphQLTypes.NotificationConnection notifications(
        @Argument NotificationFilterInputArgs filter,
        @Argument Integer first,
        @Argument String after,
        @AuthenticationPrincipal User principal
    ) {
        UUID userId = principal.getId();

        int size = first != null ? first : 20;
        int page = decodeCursorToPage(after, size);

        NotificationQueryDto queryDto = toQueryDto(filter, page, size);
        NotificationPageDto result = notificationService.getNotifications(userId, queryDto);

        List<NotificationGraphQLTypes.NotificationEdge> edges = result.content().stream()
            .map(dto -> new NotificationGraphQLTypes.NotificationEdge(
                encodeCursor(result.page(), result.content().indexOf(dto)), dto))
            .toList();

        boolean hasNextPage = (result.page() + 1) < result.totalPages();
        String endCursor = edges.isEmpty() ? null : edges.getLast().cursor();

        return new NotificationGraphQLTypes.NotificationConnection(
            edges, new NotificationGraphQLTypes.PageInfo(hasNextPage, endCursor), result.unreadCount()
        );
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public List<NotificationGroupDto> notificationsGroupedByDate(
        @Argument NotificationFilterInputArgs filter,
        @Argument Integer first,
        @Argument String after,
        @AuthenticationPrincipal User principal
    ) {
        UUID userId = principal.getId();

        int size = first != null ? first : 20;
        int page = decodeCursorToPage(after, size);

        NotificationQueryDto queryDto = toQueryDto(filter, page, size);
        return notificationService.getNotificationsGroupedByDate(userId, queryDto);
    }

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public long notificationUnreadCount(@AuthenticationPrincipal User principal) {
        return notificationService.getUnreadCount(principal.getId());
    }

    // ── Mutations ──────────────────────────────────────────

    @MutationMapping
    @PreAuthorize("hasAnyRole('RECEPTIONIST','MANAGER','OWNER','ADMIN')")
    public NotificationResponseDto createNotification(
        @Argument("input") NotificationCreateDto input,
        @AuthenticationPrincipal User principal
    ) {
        // Recipient (userId) and content come from the input, but actor identity
        // is always derived from the authenticated caller - never trust a
        // client-supplied actorId/actorName, or any authenticated staff member
        // could spoof being someone else in the notification's origin.
        NotificationCreateDto sanitized = new NotificationCreateDto(
            input.userId(),
            input.type(),
            input.title(),
            input.message(),
            input.referenceType(),
            input.referenceId(),
            input.priority(),
            principal.getId(),
            principal.getFullName()
        );

        NotificationResponseDto created = notificationService.create(sanitized);
        notificationPublisher.publish(sanitized.userId(), created);
        return created;
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public NotificationGraphQLTypes.MarkAsReadResult markNotificationAsRead(
        @Argument UUID id, @AuthenticationPrincipal User principal
    ) {
        List<UUID> updatedIds = notificationService.markAsRead(principal.getId(), List.of(id));
        return new NotificationGraphQLTypes.MarkAsReadResult(updatedIds);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public NotificationGraphQLTypes.MarkAsReadResult markNotificationsAsRead(
        @Argument List<UUID> ids, @AuthenticationPrincipal User principal
    ) {
        List<UUID> updatedIds = notificationService.markAsRead(principal.getId(), ids);
        return new NotificationGraphQLTypes.MarkAsReadResult(updatedIds);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public NotificationGraphQLTypes.MarkAsReadResult markAllNotificationsAsRead(
        @AuthenticationPrincipal User principal
    ) {
        notificationService.markAllAsRead(principal.getId());
        return new NotificationGraphQLTypes.MarkAsReadResult(List.of());
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public NotificationGraphQLTypes.DeleteNotificationsResult deleteNotification(
        @Argument UUID id, @AuthenticationPrincipal User principal
    ) {
        List<UUID> deletedIds = notificationService.delete(principal.getId(), List.of(id));
        return new NotificationGraphQLTypes.DeleteNotificationsResult(deletedIds);
    }

    @MutationMapping
    @PreAuthorize("isAuthenticated()")
    public NotificationGraphQLTypes.DeleteNotificationsResult deleteNotifications(
        @Argument List<UUID> ids, @AuthenticationPrincipal User principal
    ) {
        List<UUID> deletedIds = notificationService.delete(principal.getId(), ids);
        return new NotificationGraphQLTypes.DeleteNotificationsResult(deletedIds);
    }

    // ── Subscription ───────────────────────────────────────

    @SubscriptionMapping
    @PreAuthorize("isAuthenticated()")
    public Flux<NotificationResponseDto> notificationReceived(@AuthenticationPrincipal User principal) {
        return notificationPublisher.streamFor(principal.getId());
    }

    // ── Helpers ──────────────────────────────────────────

    public record NotificationFilterInputArgs(
        NotificationReadFilter readFilter,
        String search,
        List<NotificationType> types
    ) {
    }

    private NotificationQueryDto toQueryDto(NotificationFilterInputArgs filter, int page, int size) {
        return new NotificationQueryDto(
            filter != null ? filter.readFilter() : null,
            filter != null ? filter.search() : null,
            filter != null ? filter.types() : null,
            NotificationSortField.CREATED_AT,
            SortDirection.DESC,
            page,
            size
        );
    }

    private String encodeCursor(int page, int indexInPage) {
        return Base64.getEncoder().encodeToString((page + ":" + indexInPage).getBytes());
    }

    private int decodeCursorToPage(String cursor, int size) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        String decoded = new String(Base64.getDecoder().decode(cursor));
        String pageStr = decoded.split(":")[0];
        return Integer.parseInt(pageStr) + 1;
    }
}