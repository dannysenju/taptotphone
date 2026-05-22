package danny.com.taptotphone.infrastructure.adapters.outbound.persistence;

import danny.com.taptotphone.domain.model.PAN;
import danny.com.taptotphone.domain.model.Transaction;
import danny.com.taptotphone.domain.model.TransactionStatus;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class TransactionJpaEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "masked_pan", nullable = false)
    private String maskedPan;

    @Column(name = "encrypted_pan", nullable = false)
    private String encryptedPan;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "response_code", length = 2)
    private String responseCode;

    @Column(name = "approval_code", length = 6)
    private String approvalCode;

    @Column(name = "merchant_id", nullable = false, length = 15)
    private String merchantId;

    @Column(name = "terminal_id", nullable = false, length = 8)
    private String terminalId;

    @Column(name = "transmission_date_time", nullable = false, length = 10)
    private String transmissionDateTime;

    @Column(name = "stan", nullable = false, length = 6)
    private String systemTraceAuditNumber;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    public TransactionJpaEntity() {
    }

    // Convert from Domain to JPA
    public static TransactionJpaEntity fromDomain(Transaction domain) {
        TransactionJpaEntity entity = new TransactionJpaEntity();
        entity.setId(domain.getId());
        entity.setMaskedPan(domain.getPan().masked());
        entity.setEncryptedPan(domain.getEncryptedPan());
        entity.setAmount(domain.getAmount());
        entity.setCurrency(domain.getCurrency());
        entity.setResponseCode(domain.getResponseCode());
        entity.setApprovalCode(domain.getApprovalCode());
        entity.setMerchantId(domain.getMerchantId());
        entity.setTerminalId(domain.getTerminalId());
        entity.setTransmissionDateTime(domain.getTransmissionDateTime());
        entity.setSystemTraceAuditNumber(domain.getSystemTraceAuditNumber());
        entity.setStatus(domain.getStatus());
        entity.setCreatedAt(domain.getCreatedAt());
        entity.setProcessedAt(domain.getProcessedAt());
        return entity;
    }

    // Convert from JPA to Domain (using decrypter at port mapping time or inside adapter)
    public Transaction toDomain(String decryptedPan) {
        return new Transaction(
                this.id,
                new PAN(decryptedPan),
                this.encryptedPan,
                this.amount,
                this.currency,
                this.responseCode,
                this.approvalCode,
                this.merchantId,
                this.terminalId,
                this.transmissionDateTime,
                this.systemTraceAuditNumber,
                this.status,
                this.createdAt,
                this.processedAt
        );
    }

    // Manual Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getMaskedPan() { return maskedPan; }
    public void setMaskedPan(String maskedPan) { this.maskedPan = maskedPan; }

    public String getEncryptedPan() { return encryptedPan; }
    public void setEncryptedPan(String encryptedPan) { this.encryptedPan = encryptedPan; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getResponseCode() { return responseCode; }
    public void setResponseCode(String responseCode) { this.responseCode = responseCode; }

    public String getApprovalCode() { return approvalCode; }
    public void setApprovalCode(String approvalCode) { this.approvalCode = approvalCode; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }

    public String getTransmissionDateTime() { return transmissionDateTime; }
    public void setTransmissionDateTime(String transmissionDateTime) { this.transmissionDateTime = transmissionDateTime; }

    public String getSystemTraceAuditNumber() { return systemTraceAuditNumber; }
    public void setSystemTraceAuditNumber(String systemTraceAuditNumber) { this.systemTraceAuditNumber = systemTraceAuditNumber; }

    public TransactionStatus getStatus() { return status; }
    public void setStatus(TransactionStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
}
