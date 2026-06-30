package com.verdant.salon_ecomm.resolvers;

import com.verdant.salon_ecomm.entities.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ProductStockResolver {

  @SchemaMapping(typeName = "Product", field = "inStock")
  public boolean inStock(Product product) {
    return product.getStockQuantity() > 0;
  }
}