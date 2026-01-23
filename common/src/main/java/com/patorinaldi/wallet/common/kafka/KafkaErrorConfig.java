package com.patorinaldi.wallet.common.kafka;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.messaging.converter.MessageConversionException;
import jakarta.validation.ValidationException;
import org.apache.kafka.clients.consumer.ConsumerRecord;

@Configuration
public class KafkaErrorConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorConfig.class);

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        // Publish to DLT after retries exhausted
        DeadLetterPublishingRecoverer recoverer =
            new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    String dltTopic = record.topic() + ".DLT";
                    log.error("Sending message to DLT: topic={}, partition={}, offset={}, dltTopic={}, error={}",
                        record.topic(), record.partition(), record.offset(), dltTopic,
                        ex.getMessage());
                    return new TopicPartition(dltTopic, record.partition());
                });

        // Retry with exponential backoff (1s, 2s, 4s, 8s - max 10s total)
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(10000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);

        // Add retry listener for logging retry attempts
        handler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
                log.warn("Kafka message processing failed: topic={}, partition={}, offset={}, attempt={}, error={}",
                    record.topic(), record.partition(), record.offset(), deliveryAttempt, ex.getMessage());
            }

            @Override
            public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
                log.info("Kafka message recovered after retries: topic={}, partition={}, offset={}",
                    record.topic(), record.partition(), record.offset());
            }

            @Override
            public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
                log.error("Kafka message recovery failed: topic={}, partition={}, offset={}, originalError={}, recoveryError={}",
                    record.topic(), record.partition(), record.offset(),
                    original.getMessage(), failure.getMessage());
            }
        });

        // Don't retry these - send directly to DLT
        handler.addNotRetryableExceptions(
            DeserializationException.class,
            MessageConversionException.class,
            ValidationException.class
        );

        return handler;
    }
}
