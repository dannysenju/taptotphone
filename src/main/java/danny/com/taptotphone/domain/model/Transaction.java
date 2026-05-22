package danny.com.taptotphone.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class Transaction {
    private final UUID id;
    private final PAN pan;
    private String encryptedPan;
    private final BigDecimal amount;
    private final String currency;
    private String responseCode;
    private String approvalCode;
    private final String merchantId;
    private final String terminalId;
    private final String transmissionDateTime;
    private final String systemTraceAuditNumber;
    private TransactionStatus status;
    private final Instant createdAt;
    private Instant processedAt;

    public Transaction(UUID id, PAN pan, BigDecimal amount, String currency, String merchantId, 
                       String terminalId, String transmissionDateTime, String systemTraceAuditNumber) {
        this.id = id != null ? id : UUID.randomUUID();
        this.pan = pan;
        this.amount = amount;
        this.currency = currency;
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.transmissionDateTime = transmissionDateTime;
        this.systemTraceAuditNumber = systemTraceAuditNumber;
        this.status = TransactionStatus.PENDING;
        this.createdAt = Instant.now();
    }

    // Constructor with all fields for reconstruction (e.g. from database)
    public Transaction(UUID id, PAN pan, String encryptedPan, BigDecimal amount, String currency,
                       String responseCode, String approvalCode, String merchantId, String terminalId,
                       String transmissionDateTime, String systemTraceAuditNumber, TransactionStatus status,
                       Instant createdAt, Instant processedAt) {
        this.id = id;
        this.pan = pan;
        this.encryptedPan = encryptedPan;
        this.amount = amount;
        this.currency = currency;
        this.responseCode = responseCode;
        this.approvalCode = approvalCode;
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.transmissionDateTime = transmissionDateTime;
        this.systemTraceAuditNumber = systemTraceAuditNumber;
        this.status = status;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public void approve(String approvalCode) {
        this.status = TransactionStatus.APPROVED;
        this.responseCode = "00";
        this.approvalCode = approvalCode;
        this.processedAt = Instant.now();
    }

    public void decline(String responseCode) {
        this.status = TransactionStatus.DECLINED;
        this.responseCode = responseCode;
        this.processedAt = Instant.now();
    }

    public void fail(String responseCode) {
        this.status = TransactionStatus.FAILED;
        this.responseCode = responseCode;
        this.processedAt = Instant.now();
    }

    public void setEncryptedPan(String encryptedPan) {
        this.encryptedPan = encryptedPan;
    }

    // Getters
    public UUID getId() { return id; }
    public PAN getPan() { return pan; }
    public String getEncryptedPan() { return encryptedPan; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getResponseCode() { return responseCode; }
    public String getApprovalCode() { return approvalCode; }
    public String getMerchantId() { return merchantId; }
    public String getTerminalId() { return terminalId; }
    public String getTransmissionDateTime() { return transmissionDateTime; }
    public String getSystemTraceAuditNumber() { return systemTraceAuditNumber; }
    public TransactionStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
}
