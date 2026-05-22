package danny.com.taptotphone.application.ports.inbound;

import danny.com.taptotphone.domain.model.Transaction;

public interface ProcessTransactionUseCase {
    Transaction process(Transaction transaction);
}
