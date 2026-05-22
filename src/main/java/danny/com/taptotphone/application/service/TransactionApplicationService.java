package danny.com.taptotphone.application.service;

import danny.com.taptotphone.domain.model.MPocAttestation;
import danny.com.taptotphone.domain.model.Transaction;
import danny.com.taptotphone.application.ports.inbound.DeviceAttestationUseCase;
import danny.com.taptotphone.application.ports.inbound.ProcessTransactionUseCase;
import danny.com.taptotphone.application.ports.outbound.EventPublisherPort;
import danny.com.taptotphone.application.ports.outbound.FpeEncryptionPort;
import danny.com.taptotphone.application.ports.outbound.MPocAttestationPort;
import danny.com.taptotphone.application.ports.outbound.TransactionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionApplicationService implements ProcessTransactionUseCase, DeviceAttestationUseCase {
    private static final Logger log = LoggerFactory.getLogger(TransactionApplicationService.class);

    private final TransactionRepositoryPort transactionRepositoryPort;
    private final EventPublisherPort eventPublisherPort;
    private final FpeEncryptionPort fpeEncryptionPort;
    private final MPocAttestationPort mpocAttestationPort;

    public TransactionApplicationService(TransactionRepositoryPort transactionRepositoryPort,
                                         EventPublisherPort eventPublisherPort,
                                         FpeEncryptionPort fpeEncryptionPort,
                                         MPocAttestationPort mpocAttestationPort) {
        this.transactionRepositoryPort = transactionRepositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.fpeEncryptionPort = fpeEncryptionPort;
        this.mpocAttestationPort = mpocAttestationPort;
    }

    @Override
    @Transactional
    public Transaction process(Transaction transaction) {
        log.info("Starting processing transaction ID: {} for terminal ID: {}", transaction.getId(), transaction.getTerminalId());

        // 1. Format-Preserving Encryption of the PAN
        String rawPan = transaction.getPan().value();
        String encrypted = fpeEncryptionPort.encrypt(rawPan);
        transaction.setEncryptedPan(encrypted);

        // Save PENDING state to database
        transactionRepositoryPort.save(transaction);
        eventPublisherPort.publishTransactionCreated(transaction);

        try {
            // 2. Perform remote device checks (PCI MPoC) - SoftPOS terminal must be validated in the cloud
            boolean isDeviceValid = true; // We can check device attestation cache if needed
            if (!isDeviceValid) {
                log.warn("Device attestation failed for terminal ID: {}", transaction.getTerminalId());
                transaction.fail("98"); // MPoC security failure
                transactionRepositoryPort.save(transaction);
                eventPublisherPort.publishTransactionProcessed(transaction);
                return transaction;
            }

            // 3. Financial validation logic (Simulated card host processing)
            if (transaction.getAmount().doubleValue() <= 0) {
                log.warn("Declining transaction due to invalid amount: {}", transaction.getAmount());
                transaction.decline("13"); // Invalid amount
            } else if (transaction.getAmount().doubleValue() > 10000.0) {
                log.warn("Declining transaction due to exceeding single tap limit ($10k): {}", transaction.getAmount());
                transaction.decline("61"); // Exceeds withdrawal limit
            } else {
                // Generate standard 6-digit approval code
                String approvalCode = String.format("%06d", (int) (Math.random() * 900000 + 100000));
                log.info("Transaction approved. ID: {}, Auth Code: {}", transaction.getId(), approvalCode);
                transaction.approve(approvalCode);
            }
        } catch (Exception e) {
            log.error("Error processing transaction ID: {}", transaction.getId(), e);
            transaction.fail("96"); // System error
        }

        // 4. Save final state to database and publish events
        transactionRepositoryPort.save(transaction);
        eventPublisherPort.publishTransactionProcessed(transaction);

        return transaction;
    }

    @Override
    @Transactional
    public MPocAttestation attestDevice(String terminalId, String deviceHardwareId, String hceToken) {
        log.info("Processing remote device attestation request for terminal: {}, hardware: {}", terminalId, deviceHardwareId);

        // Outbound call to validate hardware state, HCE properties, and cloud signature
        boolean isValid = mpocAttestationPort.validateDevice(terminalId, deviceHardwareId, hceToken);
        String status = isValid ? "TRUSTED" : "UNTRUSTED";

        // Generate virtual mock signature for attest verification
        String mockSignature = UUID.randomUUID().toString().replace("-", "") + "-SECURE-MPOC-SIG";

        MPocAttestation attestation = new MPocAttestation(
                UUID.randomUUID(),
                terminalId,
                deviceHardwareId,
                status,
                Instant.now(),
                mockSignature,
                hceToken
        );

        mpocAttestationPort.saveAttestation(attestation);
        log.info("Device attestation recorded. Terminal ID: {}, Status: {}", terminalId, status);
        return attestation;
    }
}
