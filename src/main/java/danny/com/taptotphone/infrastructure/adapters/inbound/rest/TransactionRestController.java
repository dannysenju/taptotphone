package danny.com.taptotphone.infrastructure.adapters.inbound.rest;

import danny.com.taptotphone.domain.model.PAN;
import danny.com.taptotphone.domain.model.Transaction;
import danny.com.taptotphone.application.ports.inbound.ProcessTransactionUseCase;
import danny.com.taptotphone.application.ports.outbound.TransactionRepositoryPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Primary Inbound REST adapter allowing administrative status checks and manual transaction triggering.
 * Enforces security guidelines by exposing only masked card details.
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionRestController {

    private final ProcessTransactionUseCase processUseCase;
    private final TransactionRepositoryPort repositoryPort;

    public TransactionRestController(ProcessTransactionUseCase processUseCase, 
                                     TransactionRepositoryPort repositoryPort) {
        this.processUseCase = processUseCase;
        this.repositoryPort = repositoryPort;
    }

    @PostMapping("/process")
    public ResponseEntity<TransactionResponseDto> processTransaction(@RequestBody TransactionRequestDto request) {
        try {
            PAN pan = new PAN(request.pan());
            Transaction tx = new Transaction(
                    UUID.randomUUID(),
                    pan,
                    request.amount(),
                    request.currency() != null ? request.currency() : "840",
                    request.merchantId() != null ? request.merchantId() : "TEST_MERCHANT",
                    request.terminalId() != null ? request.terminalId() : "REST_TRM",
                    Instant.now().toString().substring(5, 15).replace("-", "").replace(":", ""), // Mock Field 7
                    request.stan() != null ? request.stan() : String.format("%06d", (int) (Math.random() * 900000 + 100000))
            );

            Transaction processed = processUseCase.process(tx);
            return ResponseEntity.ok(TransactionResponseDto.fromDomain(processed));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new TransactionResponseDto(
                    null, "INVALID_CARD", BigDecimal.ZERO, null, "FAILED", "14", null, request.terminalId(), null, null
            ));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionResponseDto> getTransactionById(@PathVariable UUID id) {
        return repositoryPort.findById(id)
                .map(tx -> ResponseEntity.ok(TransactionResponseDto.fromDomain(tx)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<TransactionResponseDto>> getAllTransactions() {
        List<TransactionResponseDto> list = repositoryPort.findAll().stream()
                .map(TransactionResponseDto::fromDomain)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // DTO records
    public record TransactionRequestDto(
            String pan,
            BigDecimal amount,
            String currency,
            String merchantId,
            String terminalId,
            String stan
    ) {}

    public record TransactionResponseDto(
            UUID id,
            String maskedPan,
            BigDecimal amount,
            String currency,
            String status,
            String responseCode,
            String approvalCode,
            String terminalId,
            String merchantId,
            Instant processedAt
    ) {
        public static TransactionResponseDto fromDomain(Transaction tx) {
            return new TransactionResponseDto(
                    tx.getId(),
                    tx.getPan().masked(),
                    tx.getAmount(),
                    tx.getCurrency(),
                    tx.getStatus().name(),
                    tx.getResponseCode(),
                    tx.getApprovalCode(),
                    tx.getTerminalId(),
                    tx.getMerchantId(),
                    tx.getProcessedAt()
            );
        }
    }
}
