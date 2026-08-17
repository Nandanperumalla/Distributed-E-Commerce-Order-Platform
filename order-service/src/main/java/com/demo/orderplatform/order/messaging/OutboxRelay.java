package com.demo.orderplatform.order.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper objectMapper;

    public OutboxRelay(JdbcTemplate jdbc, KafkaTemplate<String, Object> kafka, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelay = 1000)
    public void relayEvents() {
        List<OutboxEvent> events = jdbc.query("""
                SELECT id, topic, record_key, payload_type, payload::text AS payload_str
                FROM outbox
                ORDER BY created_at ASC
                LIMIT 100
                """, (rs, rowNum) -> new OutboxEvent(
                (UUID) rs.getObject("id"),
                rs.getString("topic"),
                rs.getString("record_key"),
                rs.getString("payload_type"),
                rs.getString("payload_str")
        ));

        if (events.isEmpty()) {
            return;
        }

        log.debug("relaying {} events from outbox", events.size());

        for (OutboxEvent event : events) {
            try {
                Class<?> clazz = Class.forName(event.payloadType);
                Object payload = objectMapper.readValue(event.payloadStr, clazz);

                kafka.send(event.topic, event.recordKey, payload).get();

                jdbc.update("DELETE FROM outbox WHERE id = ?", event.id);
                log.debug("relayed and deleted event {} for key {}", event.id, event.recordKey);

            } catch (Exception e) {
                log.error("failed to relay event {}", event.id, e);
                break; // Stop batch to preserve ordering
            }
        }
    }

    private record OutboxEvent(UUID id, String topic, String recordKey, String payloadType, String payloadStr) {
    }
}
