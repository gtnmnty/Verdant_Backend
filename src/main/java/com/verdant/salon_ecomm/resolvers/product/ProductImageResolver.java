package com.verdant.salon_ecomm.resolvers.product;

import com.verdant.salon_ecomm.entities.MediaImage;
import com.verdant.salon_ecomm.entities.Product;
import com.verdant.salon_ecomm.models.enums.ItemType;
import com.verdant.salon_ecomm.repositories.MediaImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ProductImageResolver {
  private final MediaImageRepository mediaImageRepository;

  @BatchMapping(typeName = "Product")
  public Map<Product, List<MediaImage>> images(List<Product> products) {
    List<UUID> productIds = products.stream()
        .map(Product::getId)
        .toList();

    List<MediaImage> allImages = mediaImageRepository
        .findByEntityTypeAndEntityIdInOrderBySortOrderAsc(ItemType.PRODUCT, productIds);

    Map<UUID, List<MediaImage>> imagesByProductId = allImages.stream()
        .collect(Collectors.groupingBy(MediaImage::getEntityId));

    return products.stream()
        .collect(Collectors.toMap(
                product -> product,
                product -> imagesByProductId.getOrDefault(product.getId(), List.of())
        ));
  }
}