package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.promo_code.PromoCodeDto;
import com.verdant.salon_ecomm.dtos.promo_code.PromoCodeValidationResult;
import com.verdant.salon_ecomm.dtos.promo_code.admin.AdminUpsertPromoCodeInput;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.PromoCodeStatus;
import com.verdant.salon_ecomm.services.PromoCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PromoCodeResolver {

    private final PromoCodeService promoCodeService;

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public PromoCodeValidationResult previewPromoCode(@Argument String code, @Argument BigDecimal subtotal) {
        return promoCodeService.validate(getCurrentUserId(), code, subtotal);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public List<PromoCodeDto> adminPromoCodes() {
        return promoCodeService.getAll();
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public PromoCodeDto adminUpsertPromoCode(@Argument AdminUpsertPromoCodeInput input) {
        return promoCodeService.upsert(getCurrentUserId(), input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public Boolean adminSetPromoCodeStatus(@Argument UUID id, @Argument PromoCodeStatus status) {
        promoCodeService.setStatus(id, status);
        return true;
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof User user) {
            return user.getId();
        }
        throw new IllegalStateException("No authenticated user found");
    }
}