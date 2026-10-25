package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.dtos.order.OrderItemProductDto;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.services.MediaImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// H-12: an order line's product only exposes its PRIMARY image (the one staff flagged as primary),
// not the whole gallery. Batched: one query for every product in the response, however many
// orders or lines were requested. Products without a primary image map to null (the field is nullable).
@Controller
@RequiredArgsConstructor
public class OrderItemProductResolver {

    private final MediaImageService mediaImageService;

    @BatchMapping(typeName = "OrderItemProduct", field = "primaryImage")
    public Map<OrderItemProductDto, MediaImageDto> primaryImage(List<OrderItemProductDto> products) {
        List<UUID> productIds = products.stream().map(OrderItemProductDto::id).distinct().toList();
        Map<UUID, MediaImageDto> primaryByProductId =
            mediaImageService.getPrimaryImagesByEntityIds(ItemType.PRODUCT, productIds);

        Map<OrderItemProductDto, MediaImageDto> result = new HashMap<>();
        for (OrderItemProductDto product : products) {
            result.put(product, primaryByProductId.get(product.id()));
        }
        return result;
    }
}
