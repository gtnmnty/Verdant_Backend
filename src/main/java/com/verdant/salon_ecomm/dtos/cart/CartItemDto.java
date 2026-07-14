package com.verdant.salon_ecomm.dtos.cart;

import com.verdant.salon_ecomm.entities.Product;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class CartItemDto {
    private UUID id;
    private Product product;
    private Integer quantity;
    private String deliveryOption;
}
