package com.demo.orderplatform.inventory.domain;

import java.io.Serializable;

/**
 * Serializable because this is what gets cached in Redis on the read path.
 */
public record StockItem(String sku, String name, int available, long priceCents) implements Serializable {
}
