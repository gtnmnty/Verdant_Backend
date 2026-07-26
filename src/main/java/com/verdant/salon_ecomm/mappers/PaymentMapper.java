package com.verdant.salon_ecomm.mappers;

import com.stripe.model.PaymentIntent;
import com.verdant.salon_ecomm.dtos.payment.PaymentIntentDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PaymentMapper {

    public PaymentIntentDto toDto(PaymentIntent intent) {
        return new PaymentIntentDto(
            intent.getId(),
            intent.getClientSecret(),
            intent.getStatus(),
            toMajorUnits(intent.getAmount()),
            intent.getCurrency() != null ? intent.getCurrency().toUpperCase() : null
        );
    }

    // Stripe returns amounts in the smallest currency unit (e.g. cents).
    // Convert back to a decimal so BigDecimal stays the currency type
    // everywhere outside the Stripe SDK boundary.
    private BigDecimal toMajorUnits(Long minorUnits) {
        if (minorUnits == null) return null;
        return BigDecimal.valueOf(minorUnits).movePointLeft(2).setScale(2, RoundingMode.HALF_UP);
    }
}
