package danny.com.taptotphone.application.ports.inbound;

import danny.com.taptotphone.domain.model.MPocAttestation;

public interface DeviceAttestationUseCase {
    MPocAttestation attestDevice(String terminalId, String deviceHardwareId, String hceToken);
}
