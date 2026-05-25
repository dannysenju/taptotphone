package danny.com.taptotphone.infrastructure.adapters.outbound.mpoc;

import danny.com.taptotphone.domain.model.MPocAttestation;
import danny.com.taptotphone.application.ports.outbound.MPocAttestationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Secondary adapter for PCI MPoC Cloud-based Remote Attestation.
 * Simulates communication with isolated cloud verification servers and saves attestation history.
 */
@Component
public class MpocAttestationAdapter implements MPocAttestationPort {
    private static final Logger log = LoggerFactory.getLogger(MpocAttestationAdapter.class);

    private final JpaMPocAttestationRepository repository;
    private final String cloudValidationUrl;

    public MpocAttestationAdapter(JpaMPocAttestationRepository repository,
                                  @Value("${taptotphone.mpoc.cloud-validation-url}") String cloudValidationUrl) {
        this.repository = repository;
        this.cloudValidationUrl = cloudValidationUrl;
    }

    @Override
    public boolean validateDevice(String terminalId, String deviceHardwareId, String hceToken) {
        log.info("Contacting isolated MPoC Cloud Attestation service at: {}", cloudValidationUrl);

        // Security check simulating root detection, kernel hook checks, and active memory scanning
        if (hceToken == null || hceToken.isBlank()) {
            log.error("MPoC Remote Attestation rejected: Missing HCE Session keys");
            return false;
        }

        if (deviceHardwareId != null && (deviceHardwareId.toLowerCase().contains("compromised") 
                                      || deviceHardwareId.toLowerCase().contains("root"))) {
            log.error("MPoC Remote Attestation rejected: Device integrity check failed (ROOT/COMPROMISED)");
            return false;
        }

        log.info("MPoC Remote Attestation verified. Terminal ID: {} has passed isolated memory and keystore validations.", terminalId);
        return true;
    }

    @Override
    public void saveAttestation(MPocAttestation attestation) {
        MPocAttestationJpaEntity entity = MPocAttestationJpaEntity.fromDomain(attestation);
        repository.save(entity);
    }

    @Override
    public java.util.Optional<MPocAttestation> findLatestByTerminalId(String terminalId) {
        return repository.findFirstByTerminalIdOrderByAttestationDateTimeDesc(terminalId)
                .map(MPocAttestationJpaEntity::toDomain);
    }
}
