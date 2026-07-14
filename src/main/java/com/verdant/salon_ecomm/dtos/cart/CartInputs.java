package com.verdant.salon_ecomm.dtos.cart;

import com.verdant.salon_ecomm.models.enums.DeliveryOption;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

public class CartInputs {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddToCartInput {
        private UUID productId;
        private Integer quantity;
        private DeliveryOption deliveryOption;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateQuantityInput {
        private UUID cartItemId;
        private Integer quantity;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateDeliveryOptionInput {
        private UUID cartItemId;
        private DeliveryOption deliveryOption;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RemoveCartItemsInput {
        private List<UUID> cartItemIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectedCartItemsInput {
        private List<UUID> cartItemIds;
    }
}
