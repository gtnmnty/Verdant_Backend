package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ShippingService {

    // Calendar days from the order being placed until it arrives.
    public record DeliveryWindow(int minDays, int maxDays) {}

    // Earliest and latest expected arrival for an order placed at a given time.
    public record DeliveryEstimate(OffsetDateTime min, OffsetDateTime max) {}

    private final Map<DeliveryOption, BigDecimal> deliveryFees = new EnumMap<>(DeliveryOption.class);
    private final Map<DeliveryOption, DeliveryWindow> deliveryWindows = new EnumMap<>(DeliveryOption.class);

    // Fees and delivery times come from application.properties (shipping.fee.*, shipping.days.*), in the store currency.
    public ShippingService(
        @Value("${shipping.fee.standard:0.00}") BigDecimal standardFee,
        @Value("${shipping.fee.express:5.00}") BigDecimal expressFee,
        @Value("${shipping.fee.same-day:10.00}") BigDecimal sameDayFee,
        @Value("${shipping.days.standard.min:5}") int standardMinDays,
        @Value("${shipping.days.standard.max:7}") int standardMaxDays,
        @Value("${shipping.days.express.min:2}") int expressMinDays,
        @Value("${shipping.days.express.max:3}") int expressMaxDays,
        @Value("${shipping.days.same-day.min:0}") int sameDayMinDays,
        @Value("${shipping.days.same-day.max:0}") int sameDayMaxDays
    ) {
        deliveryFees.put(DeliveryOption.STANDARD, standardFee);
        deliveryFees.put(DeliveryOption.EXPRESS, expressFee);
        deliveryFees.put(DeliveryOption.SAME_DAY, sameDayFee);

        deliveryWindows.put(DeliveryOption.STANDARD, window(standardMinDays, standardMaxDays));
        deliveryWindows.put(DeliveryOption.EXPRESS, window(expressMinDays, expressMaxDays));
        deliveryWindows.put(DeliveryOption.SAME_DAY, window(sameDayMinDays, sameDayMaxDays));
    }

    // A misconfigured window (negative, or max below min) is fixed here instead of failing at startup.
    private static DeliveryWindow window(int minDays, int maxDays) {
        int min = Math.max(0, minDays);
        return new DeliveryWindow(min, Math.max(min, maxDays));
    }

    public DeliveryWindow deliveryWindow(DeliveryOption option) {
        return deliveryWindows.getOrDefault(option, deliveryWindows.get(DeliveryOption.STANDARD));
    }

    // Estimated arrival range for one delivery option, counted from when the order was placed.
    // Null when the order has no placement time or no delivery option.
    public DeliveryEstimate estimateDelivery(DeliveryOption option, OffsetDateTime placedAt) {
        if (option == null || placedAt == null) {
            return null;
        }
        DeliveryWindow window = deliveryWindow(option);
        return new DeliveryEstimate(placedAt.plusDays(window.minDays()), placedAt.plusDays(window.maxDays()));
    }

     // One fee per distinct delivery option: items sharing an option ship together and pay it once, and each
     // different option is a separate shipment that adds its own fee (express + same-day = 5 + 10).
     // Was the highest fee only. There is no free-shipping threshold. Mirrored by the frontend's
     // deliveryFeeFor() (cart/_components/data.ts) — keep the two in sync.
     public BigDecimal calculateDeliveryFee(List<DeliveryOption> deliveryOptionsInOrder) {
         if (deliveryOptionsInOrder == null || deliveryOptionsInOrder.isEmpty()) {
             return BigDecimal.ZERO;
         }
         return deliveryOptionsInOrder.stream()
             .distinct()
             .map(option -> deliveryFees.getOrDefault(option, BigDecimal.ZERO))
             .reduce(BigDecimal.ZERO, BigDecimal::add);
     }
}
