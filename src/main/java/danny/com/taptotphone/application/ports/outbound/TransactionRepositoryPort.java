package danny.com.taptotphone.application.ports.outbound;

import danny.com.taptotphone.domain.model.Transaction;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface TransactionRepositoryPort {
    Transaction save(Transaction transaction);
    Optional<Transaction> findById(UUID id);
    List<Transaction> findAll();
    Optional<Transaction> findByTerminalIdAndSystemTraceAuditNumber(String terminalId, String systemTraceAuditNumber);
}
