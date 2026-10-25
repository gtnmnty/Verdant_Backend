package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.exceptions.BusinessRuleException;
import com.verdant.salon_ecomm.dtos.promocode.PromoCodeDto;
import com.verdant.salon_ecomm.dtos.promocode.PromoCodeValidationResult;
import com.verdant.salon_ecomm.dtos.promocode.admin.AdminUpsertPromoCodeInput;
import com.verdant.salon_ecomm.dtos.promocode.events.PromoCodeAppliedToOrderEvent;
import com.verdant.salon_ecomm.dtos.promocode.events.PromoCodeCreatedEvent;
import com.verdant.salon_ecomm.mappers.PromoCodeMapper;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.promocode.PromoCode;
import com.verdant.salon_ecomm.models.entities.promocode.PromoCodeRedemption;
import com.verdant.salon_ecomm.models.enums.PromoCodeStatus;
import com.verdant.salon_ecomm.repositories.PromoCodeRedemptionRepository;
import com.verdant.salon_ecomm.repositories.PromoCodeRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeRedemptionRepository promoCodeRedemptionRepository;
    private final UserRepository userRepository;
    private final PromoCodeMapper promoCodeMapper;
    private final ApplicationEventPublisher eventPublisher;

    // ── Validation ──

    // Preview only (validatePromoCode query, cart / checkout page). Nothing is reserved, so no lock;
    // the order itself re-checks with validateForOrder().
    @Transactional(readOnly = true)
    public PromoCodeValidationResult validate(UUID userId, String code, BigDecimal subtotal) {
        PromoCode promoCode = promoCodeRepository.findByCode(code.trim().toUpperCase())
            .orElseThrow(() -> BusinessRuleException.invalid("Invalid promo code"));
        return checkEligibility(promoCode, userId, subtotal);
    }

    /**
     * Validates eligibility and computes the discount WITHOUT writing a redemption row.
     * Caller (OrderService) must call recordRedemption(...) only after the order is saved,
     * so a failed order never consumes the user's usage count.
     *
     * M-11: the promo row is locked (FOR UPDATE) until the order transaction commits. Before, two
     * parallel placeOrder calls both counted 0 earlier uses and both inserted a redemption, so a
     * one-per-customer code could be used several times. Now the second checkout waits for the
     * first to commit, then its count sees the first redemption and is rejected.
     * MANDATORY: the lock only helps inside the order's transaction (it must cover the insert in
     * recordRedemption), so calling this without one is a bug and fails fast.
     * Tradeoff: checkouts using the SAME code are serialized for the length of an order transaction;
     * other codes and code-less orders are unaffected. No deadlock with stock locks: both order paths
     * reserve stock before this call and take no product lock after it.
     * A DB unique index on (promo_code_id, user_id) was not added: it would only fit single-use codes,
     * and max_uses_per_user is a per-code setting.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public PromoCodeValidationResult validateForOrder(UUID userId, String code, BigDecimal subtotal) {
        PromoCode promoCode = promoCodeRepository.findByCodeForUpdate(code.trim().toUpperCase())
            .orElseThrow(() -> BusinessRuleException.invalid("Invalid promo code"));
        return checkEligibility(promoCode, userId, subtotal);
    }

    private PromoCodeValidationResult checkEligibility(PromoCode promoCode, UUID userId, BigDecimal subtotal) {
        if (promoCode.getStatus() != PromoCodeStatus.ACTIVE) {
            throw BusinessRuleException.conflict("This promo code is not active");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (promoCode.getStartsAt() != null && now.isBefore(promoCode.getStartsAt())) {
            throw BusinessRuleException.conflict("This promo code is not active yet");
        }
        if (promoCode.getEndsAt() != null && now.isAfter(promoCode.getEndsAt())) {
            throw BusinessRuleException.conflict("This promo code has expired");
        }
        if (promoCode.getMinOrderAmount() != null && subtotal.compareTo(promoCode.getMinOrderAmount()) < 0) {
            throw BusinessRuleException.conflict(
                "This promo code requires a minimum order of " + promoCode.getMinOrderAmount());
        }
        long usesSoFar = promoCodeRedemptionRepository.countByPromoCodeAndUser(promoCode.getId(), userId);
        if (usesSoFar >= promoCode.getMaxUsesPerUser()) {
            throw BusinessRuleException.conflict("You've already used this promo code the maximum number of times");
        }

        BigDecimal discount = subtotal
            .multiply(promoCode.getDiscountPercent())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        // Never let the discount exceed the subtotal it's computed from (guards a >100% misconfiguration slipping past the DB check).
        discount = discount.min(subtotal);

        return new PromoCodeValidationResult(discount, promoCodeMapper.toDto(promoCode));
    }

    // Writes the actual redemption row. Must run in the same transaction as order creation.
    @Transactional
    public PromoCodeRedemption recordRedemption(UUID userId, String code, BigDecimal discountAmount, Order order) {
        PromoCode promoCode = promoCodeRepository.findByCode(code.trim().toUpperCase())
            .orElseThrow(() -> new ResourceNotFoundException("Promo code not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PromoCodeRedemption redemption = PromoCodeRedemption.builder()
            .promoCode(promoCode)
            .user(user)
            .order(order)
            .discountAmount(discountAmount)
            .build();
        redemption = promoCodeRedemptionRepository.save(redemption);

        eventPublisher.publishEvent(new PromoCodeAppliedToOrderEvent(order, user, redemption));
        return redemption;
    }

    // ── Admin CRUD ───────────────────────────────────────────────

    @Transactional
    public PromoCodeDto upsert(UUID actorId, AdminUpsertPromoCodeInput input) {
        User actor = userRepository.findById(actorId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        PromoCode promoCode = input.id() != null
            ? promoCodeRepository.findById(input.id())
            .orElseThrow(() -> new ResourceNotFoundException("Promo code not found"))
            : PromoCode.builder().status(PromoCodeStatus.ACTIVE).createdBy(actor).build();

        // M-10: needs both dates, so it can't be a field annotation on the input record.
        if (input.startsAt() != null && input.endsAt() != null && !input.endsAt().isAfter(input.startsAt())) {
            throw BusinessRuleException.invalid("End date must be after the start date");
        }

        promoCode.setCode(input.code().trim().toUpperCase());
        promoCode.setDiscountPercent(input.discountPercent());
        promoCode.setMinOrderAmount(input.minOrderAmount());
        promoCode.setMaxUsesPerUser(input.maxUsesPerUser() != null ? input.maxUsesPerUser() : 1);
        promoCode.setStartsAt(input.startsAt());
        promoCode.setEndsAt(input.endsAt());
        promoCode.setDescription(input.description());

        boolean isNew = promoCode.getId() == null;
        promoCode = promoCodeRepository.save(promoCode);

        if (isNew) {
            eventPublisher.publishEvent(new PromoCodeCreatedEvent(promoCode, actor));
        }
        return promoCodeMapper.toDto(promoCode);
    }

    @Transactional
    public void setStatus(UUID promoCodeId, PromoCodeStatus status) {
        PromoCode promoCode = promoCodeRepository.findById(promoCodeId)
            .orElseThrow(() -> new ResourceNotFoundException("Promo code not found"));
        promoCode.setStatus(status);
        promoCodeRepository.save(promoCode);
    }

    public List<PromoCodeDto> getAll() {
        return promoCodeRepository.findAll().stream().map(promoCodeMapper::toDto).toList();
    }
}