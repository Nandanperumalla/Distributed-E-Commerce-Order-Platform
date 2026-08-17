package com.demo.orderplatform.common.event;

/**
 * Every topic on the bus, in one place. One event type per topic keeps
 * deserialization unambiguous and lets each consumer group scale on its own.
 */
public final class Topics {

    /** order-service -> inventory-service. A customer placed an order. */
    public static final String ORDER_CREATED = "orders.created";

    /** inventory-service -> order-service, payment-service. Stock is held. */
    public static final String INVENTORY_RESERVED = "inventory.reserved";

    /** inventory-service -> order-service. Not enough stock; order is dead. */
    public static final String INVENTORY_REJECTED = "inventory.rejected";

    /** order-service -> inventory-service. Compensating command after a failed payment. */
    public static final String INVENTORY_RELEASE = "inventory.release";

    /** payment-service -> order-service. Funds captured. */
    public static final String PAYMENT_AUTHORIZED = "payment.authorized";

    /** payment-service -> order-service. Funds refused. */
    public static final String PAYMENT_DECLINED = "payment.declined";

    private Topics() {
    }
}
