package com.verdant.salon_ecomm.services;

import com.stripe.StripeClient;
import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.verdant.salon_ecomm.config.StripeConfig;
import com.verdant.salon_ecomm.models.entities.GiftCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;

//Stripe calls for gift cards, in their own bean so @Retryable goes through a real proxy
//(replaces GiftCardService's self-injected @Lazy proxy) and so no network call runs inside a DB transaction.
@Component
@RequiredArgsConstructor
@Slf4j
public class GiftCardStripeGateway {

    private final StripeClient stripeClient;
    private final StripeConfig stripeConfig;

    @Retryable(
        retryFor = { ApiConnectionException.class, ApiException.class },
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public PaymentIntent createPaymentIntent(GiftCard giftCard, String stripeCustomerId) throws StripeException {
        String currency = stripeConfig.getCurrency();
        int fractionDigits = Currency.getInstance(currency.toUpperCase(Locale.ROOT)).getDefaultFractionDigits();
        long minorUnits = giftCard.getInitialAmount()
            .movePointRight(fractionDigits)
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
            .setAmount(minorUnits)
            .setCurrency(currency)
            .setCustomer(stripeCustomerId)
            .putMetadata("type", "gift_card")
            .putMetadata("gift_card_id", giftCard.getId().toString())
            .setAutomaticPaymentMethods(
                PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build()
            )
            .build();

        RequestOptions options = RequestOptions.builder()
            .setIdempotencyKey("gift_card_" + giftCard.getId() + "_payment_intent")
            .build();

        return stripeClient.paymentIntents().create(params, options);
    }

    /** Best effort - used when an unpaid purchase is abandoned so the customer can't pay for a dead card later. */
    public void cancelPaymentIntent(String paymentIntentId) {
        if (paymentIntentId == null) return;
        try {
            stripeClient.paymentIntents().cancel(paymentIntentId);
        } catch (StripeException ex) {
            log.warn("Could not cancel PaymentIntent {}: {}", paymentIntentId, ex.getMessage());
        }
    }
}
