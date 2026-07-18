package com.verdant.salon_ecomm.resolvers.product;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.services.MediaImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ProductMediaResolver {

    private final MediaImageService mediaImageService;

    @BatchMapping(typeName = "Product")
    public Map<Product, MediaImageDto> primaryImage(List<Product> products) {
        List<UUID> productIds = products.stream().map(Product::getId).toList();

        Map<UUID, MediaImageDto> byProductId =
            mediaImageService.getPrimaryImagesByEntityIds(ItemType.PRODUCT, productIds);

        Map<Product, MediaImageDto> result = new HashMap<>();
        for(Product product : products) {
            result.put(product, byProductId.get(product.getId()));
        }

        return result;
    }
}