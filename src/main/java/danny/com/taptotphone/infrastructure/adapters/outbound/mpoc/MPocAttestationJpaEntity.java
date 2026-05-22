package danny.com.taptotphone.infrastructure.adapters.outbound.mpoc;

import danny.com.taptotphone.domain.model.MPocAttestation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mpoc_attestations")
public class MPocAttestationJpaEntity {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "terminal_id", nullable = false, length = 8)
    private String terminalId;

    @Column(name = "device_hardware_id", nullable = false)
    private String deviceHardwareId;

    @Column(name = "attestation_status", nullable = false, length = 15)
    private String attestationStatus;

    @Column(name = "attestation_date_time", nullable = false)
    private Instant attestationDateTime;

    @Column(nullable = false, length = 1024)
    private String signature;

    @Column(name = "hce_token", nullable = false, length = 512)
    private String hceToken;

    public MPocAttestationJpaEntity() {
    }

    public static MPocAttestationJpaEntity fromDomain(MPocAttestation domain) {
        MPocAttestationJpaEntity entity = new MPocAttestationJpaEntity();
        entity.setId(domain.getId());
        entity.setTerminalId(domain.getTerminalId());
        entity.setDeviceHardwareId(domain.getDeviceHardwareId());
        entity.setAttestationStatus(domain.getAttestationStatus());
        entity.setAttestationDateTime(domain.getAttestationDateTime());
        entity.setSignature(domain.getSignature());
        entity.setHceToken(domain.getHceToken());
        return entity;
    }

    public MPocAttestation toDomain() {
        return new MPocAttestation(
                this.id,
                this.terminalId,
                this.deviceHardwareId,
                this.attestationStatus,
                this.attestationDateTime,
                this.signature,
                this.hceToken
        );
    }

    // Manual Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTerminalId() { return terminalId; }
    public void setTerminalId(String terminalId) { this.terminalId = terminalId; }

    public String getDeviceHardwareId() { return deviceHardwareId; }
    public void setDeviceHardwareId(String deviceHardwareId) { this.deviceHardwareId = deviceHardwareId; }

    public String getAttestationStatus() { return attestationStatus; }
    public void setAttestationStatus(String attestationStatus) { this.attestationStatus = attestationStatus; }

    public Instant getAttestationDateTime() { return attestationDateTime; }
    public void setAttestationDateTime(Instant attestationDateTime) { this.attestationDateTime = attestationDateTime; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getHceToken() { return hceToken; }
    public void setHceToken(String hceToken) { this.hceToken = hceToken; }
}
