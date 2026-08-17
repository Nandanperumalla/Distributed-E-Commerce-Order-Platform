package com.demo.orderplatform.order.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * Prices are deliberately absent: the client says what it wants, the catalog
 * says what it costs. Letting a client post its own prices is how you get
 * one-cent televisions.
 */
public record PlaceOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<Line> lines) {

    public record Line(@NotBlank String sku, @Positive int quantity) {
    }
}
