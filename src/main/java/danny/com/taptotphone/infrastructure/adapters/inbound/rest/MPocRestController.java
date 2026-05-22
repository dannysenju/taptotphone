package danny.com.taptotphone.infrastructure.adapters.inbound.rest;

import danny.com.taptotphone.domain.model.MPocAttestation;
import danny.com.taptotphone.application.ports.inbound.DeviceAttestationUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Primary Inbound REST adapter for PCI MPoC remote device attestation.
 * Terminal devices request secure kernel verification and obtain HCE tokens before executing POS operations.
 */
@RestController
@RequestMapping("/api/v1/attestation")
public class MPocRestController {

    private final DeviceAttestationUseCase attestationUseCase;

    public MPocRestController(DeviceAttestationUseCase attestationUseCase) {
        this.attestationUseCase = attestationUseCase;
    }

    @PostMapping("/verify")
    public ResponseEntity<AttestationResponseDto> verifyDevice(@RequestBody AttestationRequestDto request) {
        if (request.terminalId() == null || request.terminalId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        MPocAttestation attestation = attestationUseCase.attestDevice(
                request.terminalId(),
                request.deviceHardwareId() != null ? request.deviceHardwareId() : "GENERIC_HW_" + UUID.randomUUID(),
                request.hceToken() != null ? request.hceToken() : "HCE_TOKEN_MOCK_" + UUID.randomUUID()
        );

        return ResponseEntity.ok(AttestationResponseDto.fromDomain(attestation));
    }

    // DTO records
    public record AttestationRequestDto(
            String terminalId,
            String deviceHardwareId,
            String hceToken
    ) {}

    public record AttestationResponseDto(
            UUID id,
            String terminalId,
            String deviceHardwareId,
            String attestationStatus,
            Instant attestationDateTime,
            String signature
    ) {
        public static AttestationResponseDto fromDomain(MPocAttestation domain) {
            return new AttestationResponseDto(
                    domain.getId(),
                    domain.getTerminalId(),
                    domain.getDeviceHardwareId(),
                    domain.getAttestationStatus(),
                    domain.getAttestationDateTime(),
                    domain.getSignature()
            );
        }
    }
}
