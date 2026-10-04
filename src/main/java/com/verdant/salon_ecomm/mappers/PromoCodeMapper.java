package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.promo_code.PromoCodeDto;
import com.verdant.salon_ecomm.models.entities.promo_code.PromoCode;
import org.springframework.stereotype.Component;

@Component
public class PromoCodeMapper {

    public PromoCodeDto toDto(PromoCode promoCode) {
        return new PromoCodeDto(
            promoCode.getId(),
            promoCode.getCode(),
            promoCode.getDiscountPercent(),
            promoCode.getMinOrderAmount(),
            promoCode.getMaxUsesPerUser(),
            promoCode.getStatus(),
            promoCode.getStartsAt(),
            promoCode.getEndsAt(),
            promoCode.getDescription()
        );
    }
}