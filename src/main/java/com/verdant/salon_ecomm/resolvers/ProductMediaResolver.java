package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.MediaImageDto;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.services.MediaImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ProductMediaResolver {

    private final MediaImageService mediaImageService;

    @BatchMapping(typeName = "Product")
    public Map<Product, MediaImageDto> primaryImage(List<Product> products) {
        List<UUID> productIds = products.stream().map(Product::getId).toList();

        Map<UUID, MediaImageDto> byProductId =
            mediaImageService.getPrimaryImagesByEntityIds(ItemType.PRODUCT, productIds);

        return products.stream()
            .collect(Collectors.toMap(p -> p, p -> byProductId.get(p.getId())));
    }
}