package com.demo.orderplatform.order.config;

import com.demo.orderplatform.common.event.InventoryRejected;
import com.demo.orderplatform.common.event.InventoryReserved;
import com.demo.orderplatform.common.event.PaymentAuthorized;
import com.demo.orderplatform.common.event.PaymentDeclined;
import com.demo.orderplatform.common.event.Topics;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * One topic carries exactly one event type, so each listener gets its own
 * container factory with a deserializer pinned to that type. Type headers are
 * switched off on purpose: the wire format stays plain JSON that any consumer
 * can read, not something coupled to our Java package names.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    private final String bootstrapServers;
    private final String groupId;
    private final ObjectMapper objectMapper;

    public KafkaConfig(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
                       @Value("${spring.kafka.consumer.group-id}") String groupId,
                       ObjectMapper objectMapper) {
        this.bootstrapServers = bootstrapServers;
        this.groupId = groupId;
        this.objectMapper = objectMapper;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        cfg.put(ProducerConfig.ACKS_CONFIG, "all");
        cfg.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        cfg.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        cfg.put(ProducerConfig.LINGER_MS_CONFIG, 5);

        JsonSerializer<Object> valueSerializer = new JsonSerializer<>(objectMapper);
        valueSerializer.setAddTypeInfo(false);
        return new DefaultKafkaProducerFactory<>(cfg, new StringSerializer(), valueSerializer);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public NewTopic ordersCreatedTopic() {
        return TopicBuilder.name(Topics.ORDER_CREATED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryReleaseTopic() {
        return TopicBuilder.name(Topics.INVENTORY_RELEASE).partitions(3).replicas(1).build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryReserved> inventoryReservedFactory(
            KafkaTemplate<String, Object> template) {
        return listenerFactory(InventoryReserved.class, template);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InventoryRejected> inventoryRejectedFactory(
            KafkaTemplate<String, Object> template) {
        return listenerFactory(InventoryRejected.class, template);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentAuthorized> paymentAuthorizedFactory(
            KafkaTemplate<String, Object> template) {
        return listenerFactory(PaymentAuthorized.class, template);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PaymentDeclined> paymentDeclinedFactory(
            KafkaTemplate<String, Object> template) {
        return listenerFactory(PaymentDeclined.class, template);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> listenerFactory(
            Class<T> payloadType, KafkaTemplate<String, Object> template) {

        Map<String, Object> cfg = new HashMap<>();
        cfg.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        cfg.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        cfg.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        cfg.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        cfg.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);

        JsonDeserializer<T> json = new JsonDeserializer<>(payloadType, objectMapper, false);
        json.setUseTypeHeaders(false);

        DefaultKafkaConsumerFactory<String, T> consumerFactory =
                new DefaultKafkaConsumerFactory<>(cfg, new StringDeserializer(),
                        new ErrorHandlingDeserializer<>(json));

        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        // Three quick retries, then park the record on <topic>.DLT rather than
        // spinning on it forever and stalling the partition behind it.
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(template), new FixedBackOff(500L, 3L)));
        return factory;
    }
}
