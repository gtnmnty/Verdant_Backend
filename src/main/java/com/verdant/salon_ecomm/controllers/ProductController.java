package com.verdant.salon_ecomm.controllers;

import com.verdant.salon_ecomm.dtos.product.CreateProductRequest;
import com.verdant.salon_ecomm.dtos.product.ProductResponse;
import com.verdant.salon_ecomm.dtos.product.UpdateProductRequest;
import com.verdant.salon_ecomm.dtos.user.UserResponse;
import com.verdant.salon_ecomm.exceptions.ResourceNotFoundException;
import com.verdant.salon_ecomm.mappers.ProductMapper;
import com.verdant.salon_ecomm.repositories.ProductRepository;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@AllArgsConstructor
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    @GetMapping
    public List<ProductResponse.Admin> getProducts() {
        return productRepository.findAll()
            .stream()
            .map(productMapper::toAdmin)
            .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable UUID id) {
        var product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        return ResponseEntity.ok(productMapper.toDto(product));
    }

    @PostMapping
    public ResponseEntity<ProductResponse.Detail> createProduct(
        @Valid @RequestBody CreateProductRequest request,
        UriComponentsBuilder uriBuilder
    ) {
        var newProduct = productMapper.toEntity(request);

        var savedNewProduct = productRepository.save(newProduct);
        var uri = uriBuilder.path("/products/{id}").buildAndExpand(savedNewProduct.getId()).toUri();

        return ResponseEntity.created(uri).body(productMapper.toDetail(savedNewProduct));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ProductResponse> deleteUserDetails(@PathVariable UUID id) {
        var product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        productRepository.delete(product);

        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ProductResponse.Detail> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        var product = productRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Product not found"));

        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setItemCatalog(request.getItemCatalog());
        product.setName(request.getName());

        var updatedProduct = productRepository.save(product);
        return ResponseEntity.ok(productMapper.toDetail(updatedProduct));
    }
}
