package com.demo.orderplatform.common.event;

/** One SKU and how many of it. */
public record OrderLine(String sku, int quantity, long unitPriceCents) {
}
