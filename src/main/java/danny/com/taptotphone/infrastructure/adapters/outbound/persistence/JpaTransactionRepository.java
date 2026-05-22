package danny.com.taptotphone.infrastructure.adapters.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaTransactionRepository extends JpaRepository<TransactionJpaEntity, UUID> {
    Optional<TransactionJpaEntity> findByTerminalIdAndSystemTraceAuditNumber(String terminalId, String systemTraceAuditNumber);
}
