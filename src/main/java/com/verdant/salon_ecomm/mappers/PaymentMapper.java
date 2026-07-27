package com.verdant.salon_ecomm.mappers;

import com.stripe.model.PaymentIntent;
import com.verdant.salon_ecomm.dtos.payment.PaymentIntentDto;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

@Component
public class PaymentMapper {

    public PaymentIntentDto toDto(PaymentIntent intent) {
        String currency = intent.getCurrency() != null ? intent.getCurrency().toUpperCase(Locale.ROOT) : null;
        return new PaymentIntentDto(
            intent.getId(),
            intent.getClientSecret(),
            intent.getStatus(),
            toMajorUnits(intent.getAmount(), currency),
            currency
        );
    }

    // Stripe returns amounts in the smallest currency unit (e.g. cents for
    // USD, but 0 minor digits for JPY, 3 for KWD/BHD). Derive the exponent
    // from the currency itself rather than assuming 2 everywhere, so this
    // stays correct for non-2-decimal currencies. PaymentService's inverse
    // conversion (movePointRight) must use the same fraction-digit source.
    private BigDecimal toMajorUnits(Long minorUnits, String currencyCode) {
        if (minorUnits == null) return null;
        int fractionDigits = currencyCode != null
            ? Currency.getInstance(currencyCode).getDefaultFractionDigits()
            : 2;
        return BigDecimal.valueOf(minorUnits).movePointLeft(fractionDigits).setScale(fractionDigits, RoundingMode.HALF_UP);
    }
}