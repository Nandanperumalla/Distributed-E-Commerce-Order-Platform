package com.demo.orderplatform.order.service;

public final class CatalogExceptions {

    /** The client asked for a SKU the catalog has never heard of. */
    public static class UnknownSku extends RuntimeException {
        public UnknownSku(String sku) {
            super("unknown sku: " + sku);
        }
    }

    /** inventory-service is down or slow. Pricing is synchronous, so we say 503. */
    public static class CatalogUnavailable extends RuntimeException {
        public CatalogUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private CatalogExceptions() {
    }
}
