package com.demo.orderplatform.order.service;

/** What inventory-service returns from {@code GET /inventory/{sku}}. */
public record CatalogItem(String sku, String name, int available, long priceCents) {
}
