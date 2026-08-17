package com.demo.orderplatform.inventory.domain;

/**
 * Thrown from inside the reservation transaction so Spring rolls back every
 * line that had already been decremented. An order is reserved whole or not at
 * all — we never leave a customer holding two of three items.
 */
public class InsufficientStockException extends RuntimeException {

    private final String sku;

    public InsufficientStockException(String sku, String message) {
        super(message);
        this.sku = sku;
    }

    public String sku() {
        return sku;
    }
}
