package com.example.bankservice.service;

import com.example.bankservice.model.Statistic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class TransactionPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransactionPublisher.class);
    private static final String TOPIC = "transaction-stats";

    private final KafkaTemplate<String, Statistic> kafkaTemplate;

    public TransactionPublisher(KafkaTemplate<String, Statistic> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String transactionType, Statistic statistic) {
        CompletableFuture<SendResult<String, Statistic>> future =
                kafkaTemplate.send(TOPIC, transactionType, statistic);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish statistic for transaction type {}: {}",
                        transactionType, statistic, ex);
            } else {
                var metadata = result.getRecordMetadata();
                log.info("Published statistic: type={}, amount={} -> partition {}, offset {}",
                        statistic.type(),
                        statistic.amount(),
                        metadata.partition(),
                        metadata.offset());
            }
        });
    }
}