package com.verdant.salon_ecomm.resolvers.product;

import jakarta.validation.Valid;
import com.verdant.salon_ecomm.dtos.product.*;
import com.verdant.salon_ecomm.models.entities.Product;
import com.verdant.salon_ecomm.models.entities.User;
import com.verdant.salon_ecomm.models.enums.CollectionSort;
import com.verdant.salon_ecomm.models.enums.CollectionStatus;
import com.verdant.salon_ecomm.models.enums.ItemCatalog;
import com.verdant.salon_ecomm.services.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ProductResolver {

    private final ProductService productService;

    // Acts as resolver for specific fields
    @SchemaMapping(typeName = "Product", field = "catalog")
    public ItemCatalog catalog(Product product) {
        return product.getItemCatalog();
    }


    @QueryMapping
    public ProductPage products(
        @Argument String category,
        @Argument String search,
        @Argument CollectionSort sort,
        @Argument int page,
        @Argument int pageSize
    ) {
        return productService.getProducts(category, search, sort, page, pageSize);
    }

    @QueryMapping
    public Product product(@Argument String id) {
        return productService.getProductDetail(UUID.fromString(id));
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'MANAGER', 'OWNER')")
    public AdminProductPage adminProducts(
        @Argument String category,
        @Argument String search,
        @Argument CollectionSort sort,
        @Argument CollectionStatus status,
        @Argument int page,
        @Argument int pageSize
    ) {
        return productService.getAdminProducts(category, search, sort, status, page, pageSize);
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RECEPTIONIST', 'MANAGER', 'OWNER')")
    public AdminProductDto adminProductDetail(@Argument String id) {
        return productService.getAdminProduct(UUID.fromString(id));
    }

    // M-10: @Valid makes Spring GraphQL run the input's bean constraints before this method;
    // a violation becomes a 400 listing the bad fields (GlobalExceptionHandler.handleConstraintViolation).
    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    // M-29: the caller is passed on as the actor of the product events (audit trail + staff notifications).
    public AdminProductDto createProduct(@Argument @Valid CreateProductInput input, @AuthenticationPrincipal User principal) {
        return productService.createProduct(input, principal.getId());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public AdminProductDto updateProduct(@Argument @Valid UpdateProductInput input, @AuthenticationPrincipal User principal) {
        return productService.updateProduct(input, principal.getId());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    public boolean deleteProduct(@Argument String id, @AuthenticationPrincipal User principal) {
        return productService.deleteProduct(UUID.fromString(id), principal.getId());
    }

    @MutationMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'OWNER')")
    // M-24: AdminProductDto, matching the schema's [AdminProductDto!]! (see ProductService.deleteProducts).
    public List<AdminProductDto> deleteProducts(@Argument List<UUID> ids, @AuthenticationPrincipal User principal) {
        return productService.deleteProducts(ids, principal.getId());
    }
}
