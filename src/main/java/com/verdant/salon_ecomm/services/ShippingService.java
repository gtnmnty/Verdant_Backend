package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class ShippingService {

    private final Map<DeliveryOption, BigDecimal> deliveryFees = new EnumMap<>(DeliveryOption.class);

    // Fees come from application.properties (shipping.fee.*), in the store currency.
    public ShippingService(
        @Value("${shipping.fee.standard:0.00}") BigDecimal standardFee,
        @Value("${shipping.fee.express:5.00}") BigDecimal expressFee,
        @Value("${shipping.fee.same-day:10.00}") BigDecimal sameDayFee
    ) {
        deliveryFees.put(DeliveryOption.STANDARD, standardFee);
        deliveryFees.put(DeliveryOption.EXPRESS, expressFee);
        deliveryFees.put(DeliveryOption.SAME_DAY, sameDayFee);
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
