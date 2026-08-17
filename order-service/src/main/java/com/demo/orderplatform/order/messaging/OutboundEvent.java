package com.demo.orderplatform.order.messaging;

/**
 * Raised as a Spring application event inside a transaction and turned into a
 * Kafka record only once that transaction commits. Nobody downstream ever sees
 * an order that was rolled back.
 */
public record OutboundEvent(String topic, String key, Object payload) {
}
