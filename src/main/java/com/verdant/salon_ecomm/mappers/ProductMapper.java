package com.verdant.salon_ecomm.mappers;

import com.verdant.salon_ecomm.dtos.product.CreateProductRequest;
import com.verdant.salon_ecomm.dtos.product.ProductResponse;
import com.verdant.salon_ecomm.dtos.product.UpdateProductRequest;
import com.verdant.salon_ecomm.entities.Product;
import jakarta.validation.Valid;
import org.mapstruct.*;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ProductMapper {
    ProductResponse toDto(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(@Valid CreateProductRequest product);

    @Mapping(target = "primaryImageUrl", expression = "java(primaryImage(product))")
    @Mapping(target = "inStock", expression = "java(product.getStock() > 0)")
    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "reviewCount", ignore = true)
    ProductResponse.Summary toSummary(Product product);

    @Mapping(target = "averageRating", ignore = true)
    @Mapping(target = "reviewCount", ignore = true)
    ProductResponse.Detail toDetail(Product product);

    @Mapping(target = "totalSold", ignore = true)
    ProductResponse.Admin toAdmin(Product product);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateProductRequest request, @MappingTarget Product product);

    default String primaryImage(Product product) {
        if (product.getImageUrls() == null || product.getImageUrls().length == 0) return null;
        return product.getImageUrls()[0];
    }
}
