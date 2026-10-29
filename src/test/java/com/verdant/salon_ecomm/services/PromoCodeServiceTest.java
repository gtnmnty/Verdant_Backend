package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.promocode.PromoCodeDto;
import com.verdant.salon_ecomm.dtos.promocode.PromoCodeValidationResult;
import com.verdant.salon_ecomm.dtos.promocode.admin.AdminUpsertPromoCodeInput;
import com.verdant.salon_ecomm.dtos.promocode.events.PromoCodeAppliedToOrderEvent;
import com.verdant.salon_ecomm.dtos.promocode.events.PromoCodeCreatedEvent;
import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.mappers.PromoCodeMapper;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.promocode.PromoCode;
import com.verdant.salon_ecomm.models.entities.promocode.PromoCodeRedemption;
import com.verdant.salon_ecomm.models.enums.PromoCodeStatus;
import com.verdant.salon_ecomm.repositories.PromoCodeRedemptionRepository;
import com.verdant.salon_ecomm.repositories.PromoCodeRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromoCodeServiceTest {

    @Mock PromoCodeRepository promoCodeRepository;
    @Mock PromoCodeRedemptionRepository redemptionRepository;
    @Mock UserRepository userRepository;
    @Mock PromoCodeMapper promoCodeMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks PromoCodeService service;

    private final UUID userId = UUID.randomUUID();

    private PromoCode code(String percent) {
        PromoCode p = PromoCode.builder().id(UUID.randomUUID()).code("SAVE10")
            .discountPercent(new BigDecimal(percent)).maxUsesPerUser(1).status(PromoCodeStatus.ACTIVE).build();
        when(promoCodeRepository.findByCode("SAVE10")).thenReturn(Optional.of(p));
        when(promoCodeRepository.findByCodeForUpdate("SAVE10")).thenReturn(Optional.of(p));
        when(promoCodeMapper.toDto(p)).thenReturn(mock(PromoCodeDto.class));
        return p;
    }

    private static void assertConflict(Runnable call, String messagePart) {
        assertThatThrownBy(call::run).isInstanceOf(BusinessRuleException.class)
            .hasMessageContaining(messagePart)
            .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    // ── eligibility and discount ─────────────────────────

    @Test
    void discountIsPercentOfSubtotalRoundedHalfUp() {
        code("12.50");

        PromoCodeValidationResult r = service.validate(userId, "save10", new BigDecimal("19.99"));

        // 19.99 * 12.5% = 2.49875 -> 2.50
        assertThat(r.discountAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void codeIsTrimmedAndUpperCasedBeforeLookup() {
        code("10");

        service.validate(userId, "  save10 ", new BigDecimal("100"));

        verify(promoCodeRepository).findByCode("SAVE10");
    }

    @Test
    void discountNeverExceedsTheSubtotal() {
        code("150");   // misconfigured past the DB check

        assertThat(service.validate(userId, "SAVE10", new BigDecimal("40.00")).discountAmount())
            .isEqualByComparingTo("40.00");
    }

    @Test
    void unknownCodeIsInvalid() {
        when(promoCodeRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validate(userId, "nope", BigDecimal.TEN))
            .isInstanceOf(BusinessRuleException.class)
            .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void inactiveCodeIsRejected() {
        code("10").setStatus(PromoCodeStatus.INACTIVE);

        assertConflict(() -> service.validate(userId, "SAVE10", BigDecimal.TEN), "not active");
    }

    @Test
    void codeBeforeItsStartIsRejected() {
        code("10").setStartsAt(OffsetDateTime.now().plusDays(1));

        assertConflict(() -> service.validate(userId, "SAVE10", BigDecimal.TEN), "not active yet");
    }

    @Test
    void codeAfterItsEndIsRejected() {
        code("10").setEndsAt(OffsetDateTime.now().minusMinutes(1));

        assertConflict(() -> service.validate(userId, "SAVE10", BigDecimal.TEN), "expired");
    }

    @Test
    void codeInsideItsWindowIsAccepted() {
        PromoCode p = code("10");
        p.setStartsAt(OffsetDateTime.now().minusDays(1));
        p.setEndsAt(OffsetDateTime.now().plusDays(1));

        assertThat(service.validate(userId, "SAVE10", new BigDecimal("50")).discountAmount())
            .isEqualByComparingTo("5.00");
    }

    @Test
    void subtotalBelowTheMinimumIsRejectedButExactlyTheMinimumPasses() {
        code("10").setMinOrderAmount(new BigDecimal("50.00"));

        assertConflict(() -> service.validate(userId, "SAVE10", new BigDecimal("49.99")), "minimum order");
        assertThat(service.validate(userId, "SAVE10", new BigDecimal("50.00")).discountAmount())
            .isEqualByComparingTo("5.00");
    }

    @Test
    void usesBeyondThePerCustomerLimitAreRejected() {
        PromoCode p = code("10");
        p.setMaxUsesPerUser(2);
        when(redemptionRepository.countByPromoCodeAndUser(p.getId(), userId)).thenReturn(1L, 2L);

        assertThat(service.validate(userId, "SAVE10", BigDecimal.TEN)).isNotNull();          // 2nd use
        assertConflict(() -> service.validate(userId, "SAVE10", BigDecimal.TEN), "maximum"); // 3rd use
    }

    // M-11: the order path must lock the promo row so parallel checkouts can't both use a one-time code.
    @Test
    void orderValidationLocksThePromoRow() {
        code("10");

        service.validateForOrder(userId, "save10", new BigDecimal("100"));

        verify(promoCodeRepository).findByCodeForUpdate("SAVE10");
        verify(promoCodeRepository, never()).findByCode(any());
    }

    @Test
    void validationNeverWritesARedemption() {
        code("10");

        service.validate(userId, "SAVE10", new BigDecimal("100"));
        service.validateForOrder(userId, "SAVE10", new BigDecimal("100"));

        verify(redemptionRepository, never()).save(any());
        verifyNoInteractions(eventPublisher);
    }

    // ── redemption ───────────────────────────────────────

    @Test
    void recordRedemptionSavesTheRowAndPublishesTheEvent() {
        PromoCode p = code("10");
        User user = User.builder().id(userId).build();
        Order order = new Order();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(redemptionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PromoCodeRedemption r = service.recordRedemption(userId, "save10", new BigDecimal("5.00"), order);

        assertThat(r.getPromoCode()).isSameAs(p);
        assertThat(r.getUser()).isSameAs(user);
        assertThat(r.getOrder()).isSameAs(order);
        assertThat(r.getDiscountAmount()).isEqualByComparingTo("5.00");
        verify(eventPublisher).publishEvent(any(PromoCodeAppliedToOrderEvent.class));
    }

    // ── admin upsert ─────────────────────────────────────

    private AdminUpsertPromoCodeInput input(UUID id, OffsetDateTime starts, OffsetDateTime ends, Integer maxUses) {
        return new AdminUpsertPromoCodeInput(id, "  summer25 ", new BigDecimal("25"), null, maxUses, starts, ends, "d");
    }

    @Test
    void createNormalizesTheCodeDefaultsMaxUsesAndPublishesCreated() {
        User actor = User.builder().id(UUID.randomUUID()).build();
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(promoCodeRepository.save(any())).thenAnswer(inv -> {
            PromoCode saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.upsert(actor.getId(), input(null, null, null, null));

        ArgumentCaptor<PromoCode> saved = ArgumentCaptor.forClass(PromoCode.class);
        verify(promoCodeRepository).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("SUMMER25");
        assertThat(saved.getValue().getMaxUsesPerUser()).isEqualTo(1);
        assertThat(saved.getValue().getStatus()).isEqualTo(PromoCodeStatus.ACTIVE);
        assertThat(saved.getValue().getCreatedBy()).isSameAs(actor);
        verify(eventPublisher).publishEvent(any(PromoCodeCreatedEvent.class));
    }

    @Test
    void updateDoesNotPublishCreated() {
        User actor = User.builder().id(UUID.randomUUID()).build();
        PromoCode existing = PromoCode.builder().id(UUID.randomUUID()).status(PromoCodeStatus.ACTIVE).build();
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(promoCodeRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.upsert(actor.getId(), input(existing.getId(), null, null, 3));

        assertThat(existing.getMaxUsesPerUser()).isEqualTo(3);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void endDateMustBeAfterStartDate() {
        User actor = User.builder().id(UUID.randomUUID()).build();
        when(userRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
        OffsetDateTime t = OffsetDateTime.now();

        assertThatThrownBy(() -> service.upsert(actor.getId(), input(null, t, t, 1)))
            .isInstanceOf(BusinessRuleException.class).hasMessageContaining("End date");
        assertThatThrownBy(() -> service.upsert(actor.getId(), input(null, t, t.minusDays(1), 1)))
            .isInstanceOf(BusinessRuleException.class);
        verify(promoCodeRepository, never()).save(any());
    }
}
