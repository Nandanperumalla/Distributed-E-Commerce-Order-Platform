package com.demo.orderplatform.order.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderEventPublisher.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public OrderEventPublisher(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void publish(OutboundEvent event) {
        try {
            String payloadJson = objectMapper.writeValueAsString(event.payload());
            jdbc.update("""
                    INSERT INTO outbox (id, topic, record_key, payload_type, payload)
                    VALUES (?, ?, ?, ?, ?::jsonb)
                    """,
                    UUID.randomUUID(),
                    event.topic(),
                    event.key(),
                    event.payload().getClass().getName(),
                    payloadJson);
            log.debug("saved {} for key {} to outbox", event.topic(), event.key());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("failed to serialize outbound event payload", e);
        }
    }
}
