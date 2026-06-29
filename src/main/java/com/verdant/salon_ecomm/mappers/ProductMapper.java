package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.entities.Product;
import jakarta.validation.Valid;
import org.mapstruct.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ProductMapper {

    ProductResponse toDto(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isActive", constant = "true")   // was "active" — field is "isActive"
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(@Valid CreateProductRequest product);

    @Mapping(target = "primaryImageUrl", expression = "java(primaryImage(product))")
    @Mapping(target = "category", source = "itemCatalog")
    ProductResponse.Summary toSummary(Product product);

    @Mapping(target = "imageUrls", expression = "java(toList(product.getImageUrls()))")
    @Mapping(target = "category", source = "itemCatalog")
    ProductResponse.Detail toDetail(Product product);

    @Mapping(target = "stock", source = "stockQuantity")
    @Mapping(target = "active", source = "isActive")
    @Mapping(target = "totalSold", ignore = true)
    @Mapping(target = "category", source = "itemCatalog")
    ProductResponse.Admin toAdmin(Product product);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateProductRequest request, @MappingTarget Product product);

    default Instant map(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    default String primaryImage(Product product) {
        if (product.getImageUrls() == null || product.getImageUrls().length == 0) return null;
        return product.getImageUrls()[0];
    }

    default List<String> toList(String[] arr) {
        return arr == null ? List.of() : Arrays.asList(arr);
    }
}