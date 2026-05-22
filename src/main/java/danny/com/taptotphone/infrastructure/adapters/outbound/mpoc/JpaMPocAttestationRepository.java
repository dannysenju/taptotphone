package danny.com.taptotphone.infrastructure.adapters.outbound.mpoc;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface JpaMPocAttestationRepository extends JpaRepository<MPocAttestationJpaEntity, UUID> {
    Optional<MPocAttestationJpaEntity> findFirstByTerminalIdOrderByAttestationDateTimeDesc(String terminalId);
}
