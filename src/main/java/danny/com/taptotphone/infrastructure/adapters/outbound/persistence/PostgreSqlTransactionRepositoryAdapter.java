package danny.com.taptotphone.infrastructure.adapters.outbound.persistence;

import danny.com.taptotphone.domain.model.Transaction;
import danny.com.taptotphone.application.ports.outbound.FpeEncryptionPort;
import danny.com.taptotphone.application.ports.outbound.TransactionRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Secondary adapter connecting the Domain Repository Port with Spring Data JPA & PostgreSQL.
 * Decrypts the PAN automatically using FPE when mapping back into the Domain boundaries.
 */
@Component
public class PostgreSqlTransactionRepositoryAdapter implements TransactionRepositoryPort {

    private final JpaTransactionRepository jpaRepository;
    private final FpeEncryptionPort fpeEncryptionPort;

    public PostgreSqlTransactionRepositoryAdapter(JpaTransactionRepository jpaRepository, 
                                                FpeEncryptionPort fpeEncryptionPort) {
        this.jpaRepository = jpaRepository;
        this.fpeEncryptionPort = fpeEncryptionPort;
    }

    @Override
    public Transaction save(Transaction transaction) {
        TransactionJpaEntity entity = TransactionJpaEntity.fromDomain(transaction);
        TransactionJpaEntity saved = jpaRepository.save(entity);
        return saved.toDomain(fpeEncryptionPort.decrypt(saved.getEncryptedPan()));
    }

    @Override
    public Optional<Transaction> findById(UUID id) {
        return jpaRepository.findById(id)
                .map(entity -> entity.toDomain(fpeEncryptionPort.decrypt(entity.getEncryptedPan())));
    }

    @Override
    public List<Transaction> findAll() {
        return jpaRepository.findAll().stream()
                .map(entity -> entity.toDomain(fpeEncryptionPort.decrypt(entity.getEncryptedPan())))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Transaction> findByTerminalIdAndSystemTraceAuditNumber(String terminalId, String systemTraceAuditNumber) {
        return jpaRepository.findByTerminalIdAndSystemTraceAuditNumber(terminalId, systemTraceAuditNumber)
                .map(entity -> entity.toDomain(fpeEncryptionPort.decrypt(entity.getEncryptedPan())));
    }
}
