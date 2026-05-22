package danny.com.taptotphone.application.ports.outbound;

import danny.com.taptotphone.domain.model.MPocAttestation;

public interface MPocAttestationPort {
    /**
     * Sends device hardware details and HCE tokens to isolated cloud attestation validator.
     * Returns true if device is TRUSTED and not compromised.
     */
    boolean validateDevice(String terminalId, String deviceHardwareId, String hceToken);
    
    void saveAttestation(MPocAttestation attestation);
}
