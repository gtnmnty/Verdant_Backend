package com.verdant.salon_ecomm.models.enums.orders;

// Fixed reasons a customer can give when cancelling an order. OTHER may carry a free-text note.
public enum OrderCancelReason {
    CHANGED_MIND("Changed my mind"),
    WRONG_ADDRESS_OR_ITEM("Wrong address or item"),
    FOUND_BETTER_PRICE("Found a better price"),
    DELIVERY_TOO_SLOW("Delivery is too slow"),
    OTHER("Other");

    private final String label;

    OrderCancelReason(String label) { this.label = label; }

    public String label() { return label; }
}
