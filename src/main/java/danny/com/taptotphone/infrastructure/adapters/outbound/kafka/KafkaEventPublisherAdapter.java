package danny.com.taptotphone.infrastructure.adapters.outbound.kafka;

import danny.com.taptotphone.domain.model.Transaction;
import danny.com.taptotphone.application.ports.outbound.EventPublisherPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Secondary adapter to stream real-time financial transaction state events via Apache Kafka.
 * Automatically obfuscates card numbers by publishing only masked PAN representations.
 */
@Component
public class KafkaEventPublisherAdapter implements EventPublisherPort {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisherAdapter.class);

    private static final String TOPIC_CREATED = "tap-to-phone-transactions-created";
    private static final String TOPIC_PROCESSED = "tap-to-phone-transactions-processed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaEventPublisherAdapter(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publishTransactionCreated(Transaction transaction) {
        TransactionEvent event = TransactionEvent.fromDomain(transaction);
        log.info("Dispatching TRANSACTION_CREATED event to Kafka for Tx ID: {}", transaction.getId());
        kafkaTemplate.send(TOPIC_CREATED, transaction.getId().toString(), event);
    }

    @Override
    public void publishTransactionProcessed(Transaction transaction) {
        TransactionEvent event = TransactionEvent.fromDomain(transaction);
        log.info("Dispatching TRANSACTION_PROCESSED event to Kafka for Tx ID: {}, status: {}", transaction.getId(), transaction.getStatus());
        kafkaTemplate.send(TOPIC_PROCESSED, transaction.getId().toString(), event);
    }

    /**
     * DTO Record representing the Kafka event, compliant with Java 21 native constructs.
     */
    public record TransactionEvent(
            UUID id,
            String maskedPan,
            BigDecimal amount,
            String currency,
            String responseCode,
            String approvalCode,
            String merchantId,
            String terminalId,
            String transmissionDateTime,
            String systemTraceAuditNumber,
            String status,
            Instant createdAt,
            Instant processedAt
    ) {
        public static TransactionEvent fromDomain(Transaction tx) {
            return new TransactionEvent(
                    tx.getId(),
                    tx.getPan().masked(),
                    tx.getAmount(),
                    tx.getCurrency(),
                    tx.getResponseCode(),
                    tx.getApprovalCode(),
                    tx.getMerchantId(),
                    tx.getTerminalId(),
                    tx.getTransmissionDateTime(),
                    tx.getSystemTraceAuditNumber(),
                    tx.getStatus().name(),
                    tx.getCreatedAt(),
                    tx.getProcessedAt()
            );
        }
    }
}
