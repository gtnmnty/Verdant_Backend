package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.dtos.promo_code.PromoCodeDto;
import com.verdant.salon_ecomm.dtos.promo_code.PromoCodeValidationResult;
import com.verdant.salon_ecomm.dtos.promo_code.admin.AdminUpsertPromoCodeInput;
import com.verdant.salon_ecomm.dtos.promo_code.events.PromoCodeAppliedToOrderEvent;
import com.verdant.salon_ecomm.dtos.promo_code.events.PromoCodeCreatedEvent;
import com.verdant.salon_ecomm.mappers.PromoCodeMapper;
import com.verdant.salon_ecomm.models.entities.Order;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.entities.promo_code.PromoCode;
import com.verdant.salon_ecomm.models.entities.promo_code.PromoCodeRedemption;
import com.verdant.salon_ecomm.models.enums.PromoCodeStatus;
import com.verdant.salon_ecomm.repositories.PromoCodeRedemptionRepository;
import com.verdant.salon_ecomm.repositories.PromoCodeRepository;
import com.verdant.salon_ecomm.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
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

    // ── Validation — called from OrderService.placeOrder, same transaction ──

    /**
     * Validates eligibility and computes the discount WITHOUT writing a redemption row.
     * Caller (OrderService) must call recordRedemption(...) only after the order is saved,
     * so a failed order never consumes the user's usage count.
     */
    @Transactional(readOnly = true)
    public PromoCodeValidationResult validate(UUID userId, String code, BigDecimal subtotal) {
        PromoCode promoCode = promoCodeRepository.findByCode(code.trim().toUpperCase())
            .orElseThrow(() -> new IllegalArgumentException("Invalid promo code"));

        if (promoCode.getStatus() != PromoCodeStatus.ACTIVE) {
            throw new IllegalStateException("This promo code is not active");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (promoCode.getStartsAt() != null && now.isBefore(promoCode.getStartsAt())) {
            throw new IllegalStateException("This promo code is not active yet");
        }
        if (promoCode.getEndsAt() != null && now.isAfter(promoCode.getEndsAt())) {
            throw new IllegalStateException("This promo code has expired");
        }
        if (promoCode.getMinOrderAmount() != null && subtotal.compareTo(promoCode.getMinOrderAmount()) < 0) {
            throw new IllegalStateException(
                "This promo code requires a minimum order of " + promoCode.getMinOrderAmount());
        }
        long usesSoFar = promoCodeRedemptionRepository.countByPromoCodeAndUser(promoCode.getId(), userId);
        if (usesSoFar >= promoCode.getMaxUsesPerUser()) {
            throw new IllegalStateException("You've already used this promo code the maximum number of times");
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
            .orElseThrow(() -> new EntityNotFoundException("Promo code not found"));
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

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
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        PromoCode promoCode = input.id() != null
            ? promoCodeRepository.findById(input.id())
            .orElseThrow(() -> new EntityNotFoundException("Promo code not found"))
            : PromoCode.builder().status(PromoCodeStatus.ACTIVE).createdBy(actor).build();

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
            .orElseThrow(() -> new EntityNotFoundException("Promo code not found"));
        promoCode.setStatus(status);
        promoCodeRepository.save(promoCode);
    }

    public List<PromoCodeDto> getAll() {
        return promoCodeRepository.findAll().stream().map(promoCodeMapper::toDto).toList();
    }
}