package com.verdant.salon_ecomm.dtos.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class CartDto {
    private List<CartItemDto> items;
    private Integer totalItems;
    private BigDecimal subtotal;
}