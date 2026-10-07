package com.verdant.salon_ecomm.services;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class ShippingService {

    private static final Map<DeliveryOption, BigDecimal> DELIVERY_FEE = Map.of(
        DeliveryOption.STANDARD, BigDecimal.ZERO,
        DeliveryOption.EXPRESS, new BigDecimal("5.00"),
        DeliveryOption.SAME_DAY, new BigDecimal("10.00")
    );

     // One fee per order, not per item: takes the highest-surcharge delivery option
     // present among the order's items (so an order where every item shares one
     // option is trivially "counted as one"). Base fee is waived once subtotal
     // clears the threshold; the per-option surcharge is never waived.
     public BigDecimal calculateDeliveryFee(List<DeliveryOption> deliveryOptionsInOrder) {
         if (deliveryOptionsInOrder == null || deliveryOptionsInOrder.isEmpty()) {
             return BigDecimal.ZERO;
         }
         return deliveryOptionsInOrder.stream()
             .map(DELIVERY_FEE::get)
             .max(Comparator.naturalOrder())
             .orElse(BigDecimal.ZERO);
     }
}