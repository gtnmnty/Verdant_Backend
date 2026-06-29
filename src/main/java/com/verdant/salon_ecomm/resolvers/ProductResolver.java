package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.models.enums.CollectionSort;
import com.verdant.salon_ecomm.services.ProductService;
import com.verdant.salon_ecomm.dtos.product.ProductPage;
import com.verdant.salon_ecomm.dtos.product.AdminProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ProductResolver {

  private final ProductService productService;

  @QueryMapping
  public ProductPage products(
          @Argument String category,
          @Argument String search,
          @Argument CollectionSort sort,
          @Argument Boolean isActive,
          @Argument int page,
          @Argument int pageSize
  ) {
    return productService.getProducts(category, search, sort, isActive, page, pageSize);
  }

  @QueryMapping
  public AdminProductDto product(@Argument String id) {
    return productService.getProduct(UUID.fromString(id));
  }
}
