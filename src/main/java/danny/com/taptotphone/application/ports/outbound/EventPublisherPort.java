package danny.com.taptotphone.application.ports.outbound;

import danny.com.taptotphone.domain.model.Transaction;

public interface EventPublisherPort {
    void publishTransactionCreated(Transaction transaction);
    void publishTransactionProcessed(Transaction transaction);
}
