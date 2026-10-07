package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import com.verdant.salon_ecomm.services.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class ShippingResolver {

    private final ShippingService shippingService;

    @QueryMapping
    public BigDecimal calculateShippingFee(@Argument List<DeliveryOption> deliveryOptions) {
        return shippingService.calculateDeliveryFee(deliveryOptions);
    }
}