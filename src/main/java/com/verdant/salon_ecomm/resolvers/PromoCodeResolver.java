package com.verdant.salon_ecomm.resolvers;

import jakarta.validation.Valid;
import com.verdant.salon_ecomm.dtos.promocode.PromoCodeDto;
import com.verdant.salon_ecomm.dtos.promocode.PromoCodeValidationResult;
import com.verdant.salon_ecomm.dtos.promocode.admin.AdminUpsertPromoCodeInput;
import com.verdant.salon_ecomm.models.enums.PromoCodeStatus;
import com.verdant.salon_ecomm.services.PromoCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.verdant.salon_ecomm.security.SecurityHelper;

@Controller
@RequiredArgsConstructor
public class PromoCodeResolver {

    private final PromoCodeService promoCodeService;
    private final SecurityHelper securityHelper;

    @QueryMapping
    @PreAuthorize("isAuthenticated()")
    public PromoCodeValidationResult previewPromoCode(@Argument String code, @Argument BigDecimal subtotal) {
        return promoCodeService.validate(securityHelper.currentUserId(), code, subtotal);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public List<PromoCodeDto> adminPromoCodes() {
        return promoCodeService.getAll();
    }

    // M-10: @Valid makes Spring GraphQL run the input's bean constraints before this method;
    // a violation becomes a 400 listing the bad fields (GlobalExceptionHandler.handleConstraintViolation).
    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public PromoCodeDto adminUpsertPromoCode(@Argument @Valid AdminUpsertPromoCodeInput input) {
        return promoCodeService.upsert(securityHelper.currentUserId(), input);
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public Boolean adminSetPromoCodeStatus(@Argument UUID id, @Argument PromoCodeStatus status) {
        promoCodeService.setStatus(id, status);
        return true;
    }
}
