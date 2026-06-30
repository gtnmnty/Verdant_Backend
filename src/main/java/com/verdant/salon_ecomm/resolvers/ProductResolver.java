package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.dtos.product.*;
import com.verdant.salon_ecomm.models.enums.CollectionSort;
import com.verdant.salon_ecomm.services.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
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
    return productService.getProducts(category, search, sort, page, pageSize);
  }

  @QueryMapping
  public AdminProductDto product(@Argument String id) {
    return productService.getAdminProduct(UUID.fromString(id));
  }

  @MutationMapping
  @PreAuthorize("hasRole('ADMIN')")
  public AdminProductDto createProduct(@Argument CreateProductInput input) {
    return productService.createProduct(input);
  }

  @MutationMapping
  @PreAuthorize("hasRole('ADMIN')")
  public AdminProductDto updateProduct(@Argument UpdateProductInput input) {
    return productService.updateProduct(input);
  }

  @MutationMapping
  @PreAuthorize("hasRole('ADMIN')")
  public boolean deleteProduct(@Argument String id) {
    return productService.deleteProduct(UUID.fromString(id));
  }

  @QueryMapping
  @PreAuthorize("hasRole('ADMIN')")
  public AdminProductPage adminProducts(
          @Argument String category,
          @Argument String search,
          @Argument CollectionSort sort,
          @Argument Boolean isActive,
          @Argument int page,
          @Argument int pageSize
  ) {
    return productService.getAdminProducts(category, search, sort, isActive, page, pageSize);
  }

  @QueryMapping
  @PreAuthorize("hasRole('ADMIN')")
  public AdminProductDto adminProduct(@Argument String id) {
    return productService.getAdminProduct(UUID.fromString(id));
  }
}
