package danny.com.taptotphone.domain.model;

import java.time.Instant;
import java.util.UUID;

public class MPocAttestation {
    private final UUID id;
    private final String terminalId;
    private final String deviceHardwareId;
    private final String attestationStatus; // TRUSTED, UNTRUSTED, SUSPENDED
    private final Instant attestationDateTime;
    private final String signature;
    private final String hceToken;

    public MPocAttestation(UUID id, String terminalId, String deviceHardwareId, 
                           String attestationStatus, Instant attestationDateTime, 
                           String signature, String hceToken) {
        this.id = id != null ? id : UUID.randomUUID();
        this.terminalId = terminalId;
        this.deviceHardwareId = deviceHardwareId;
        this.attestationStatus = attestationStatus;
        this.attestationDateTime = attestationDateTime != null ? attestationDateTime : Instant.now();
        this.signature = signature;
        this.hceToken = hceToken;
    }

    public UUID getId() { return id; }
    public String getTerminalId() { return terminalId; }
    public String getDeviceHardwareId() { return deviceHardwareId; }
    public String getAttestationStatus() { return attestationStatus; }
    public Instant getAttestationDateTime() { return attestationDateTime; }
    public String getSignature() { return signature; }
    public String getHceToken() { return hceToken; }

    public boolean isTrusted() {
        return "TRUSTED".equalsIgnoreCase(attestationStatus);
    }
}
