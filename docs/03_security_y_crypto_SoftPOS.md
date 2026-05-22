# 🔒 Módulo: Especificación de Seguridad y Criptografía (SoftPOS y PCI MPoC)

Este documento consolida los lineamientos arquitectónicos y de seguridad para la solución Tap to Phone, detallando la criptografía y las defensas implementadas contra fraude y exposición de datos de tarjetahabientes.

---

## 1. Arquitectura de Seguridad y Tokenización SoftPOS

La conversión de un dispositivo comercial COTS (Commercial off-the-shelf) en un terminal de pago requiere mitigar riesgos de seguridad física mediante controles lógicos estrictos:

- **Host Card Emulation (HCE):** El SDK móvil interactúa directamente con el chip de la tarjeta mediante comandos APDU (ISO 7816-4) emulando un lector contactless físico. Los datos confidenciales se transmiten de manera inmediata al backend mediante canales seguros (TLS 1.3 con Certificate Pinning).
- **Atestación de Integridad MPoC:** Verificación constante de la integridad del dispositivo mediante llamadas a APIs de atestación del sistema operativo móvil (Google Play Integrity API). Esto detecta emuladores, ganchos de depuración (Frida, Xposed), desbloqueo de cargador de arranque (bootloader unlocked) y privilegios de ROOT.
- **Cifrado Cloud Validation:** Dado que el dispositivo móvil no cuenta con un chip criptográfico seguro dedicado al comercio (Secure Element de hardware adquirente), el backend realiza el descifrado y validación en la nube para garantizar que ninguna clave criptográfica maestra resida en el celular.

---

## 2. Estándares de Cumplimiento (PCI CPoC vs. MPoC)

Las aplicaciones de pago deben cumplir con rigurosos marcos regulatorios antes de operar comercialmente:

| Característica / Requerimiento | PCI CPoC (Contactless Payments on COTS) | PCI MPoC (Mobile Payments on COTS) |
| :--- | :--- | :--- |
| **Soporte de PIN** | Únicamente transacciones sin PIN (monto bajo o firma). | **Soporta PIN ingresado en la pantalla del celular.** |
| **Arquitectura de Software** | Monitoreo y atestación constante en la nube. | Entorno de captura de PIN aislado y protegido en la App. |
| **Mecanismo de Defensa** | Detección básica de manipulación de binarios. | Telemetría activa y capacidad de **revocación remota inmediata** de la App. |
| **Estado en esta App** | Superado ampliamente. | **Cumplido**: Cuenta con endpoint de atestación de dispositivo para firmas criptográficas. |

---

## 3. Cifrado que Preserva el Formato (FPE)

Para cumplir con **PCI DSS** (evitando almacenar el PAN original en texto claro) sin alterar las bases de datos transaccionales heredadas, implementamos cifrado **FPE (Format-Preserving Encryption)**.

### Características del FPE en este Core:
- **Estructura Reversible**: Convierte un número de tarjeta de 16 dígitos en otro número de tarjeta de 16 dígitos perfectamente válido matemáticamente (incluyendo el dígito verificador Luhn).
- **Esquema de Red Feistel (4 Rondas)**:
  - Divide los 6 dígitos centrales (rango `000000` a `999999`) del PAN en dos bloques de 3 dígitos (L y R).
  - En cada ronda, utiliza una función interna (`roundFunction`) que aplica **AES-ECB-NoPadding** con una clave maestra de 256 bits y el número de ronda para permutar los bloques matemáticamente en base 1000.
  - Conserva el BIN (primeros 6 dígitos) para ruteo de adquirencia y los últimos 4 dígitos para visualización en estados de cuenta (`masked_pan`), encriptando únicamente la parte central sensible.

### Flujo de Datos del PAN:
```
PAN Original:   411111 222221 3333  (Luhn Válido)
                  │      │     │
                  │   ┌──┴──┐  │ (AES Feistel Cipher - 4 rounds)
                  ▼   ▼     ▼  ▼
PAN Guardado:   411111 182736 3333  (FPE Encrypted)
PAN en Logs:    411111 ****** 3333  (Masked PAN - Seguro)
```

---

## 4. Diseño del Puente de Red (HSM Integration)

En un entorno real, las operaciones criptográficas críticas de validación de tarjetas de crédito se derivan a un **HSM (Hardware Security Module)** adquirente para mantener el cumplimiento PCI.

### Lógica de Conexión HSM (Producción):
El backend interactúa con el HSM mediante sockets TCP con comandos hexadecimales patentados por fabricantes como **Thales payShield 10K** o **AWS Payment Cryptography**:

```java
// Ejemplo conceptual del adaptador de HSM para validar ARQC (Tag 9F26)
public boolean validateEmvCryptogram(String pan, String arqc, String atc, String transactionData) {
    String hsmCommand = "KQ" // Comando Thales para validación de ARQC
        + "01"               // ID de clave de derivación (MDK)
        + pan                // Primary Account Number
        + atc                // Application Transaction Counter
        + arqc               // Criptograma generado por la tarjeta
        + transactionData;   // Datos de la transacción (Monto, Moneda, etc.)
    
    // Envío por Socket seguro al HSM (Puerto 9999)
    byte[] response = hsmSocketClient.sendAndReceive(HexFormat.of().parseHex(hsmCommand));
    
    // Parseo de respuesta del HSM: "KR" + "00" (00 indica ARQC exitoso)
    String responseCode = new String(response, 2, 2, StandardCharsets.US_ASCII);
    return "00".equals(responseCode);
}
```

---

## 5. Arquitectura de Mensajería (Esquemas Kafka)

El bus de eventos Apache Kafka asegura que los eventos transaccionales se transmitan a microservicios autorizados de forma asíncrona. Los esquemas de tópicos aseguran el cumplimiento PCI al transmitir únicamente el **Masked PAN** (`411111******3333`) y el identificador de la transacción:

### Tópicos Kafka Configurados:
1. **`transactions-created`**: Publica el evento cuando se inicia el flujo. Utilizado por motores de prevención de fraude en tiempo real.
2. **`transactions-processed`**: Publica el resultado final (APPROVED/DECLINED) para alimentar el sistema de conciliación bancaria y analítica de datos.

```json
// Esquema del mensaje publicado en 'transactions-processed'
{
  "transactionId": "e15c4d32-cd2b-426b-88a2-f81d11ff9288",
  "maskedPan": "411111******3333",
  "amount": 250.00,
  "currency": "840",
  "status": "APPROVED",
  "responseCode": "00",
  "approvalCode": "582910",
  "terminalId": "TRM00001",
  "processedAt": "2026-05-22T20:25:17Z"
}
```
*Esto garantiza que bajo ninguna circunstancia los consumidores de Kafka tengan acceso al PAN real en claro, reduciendo drásticamente el alcance de la auditoría PCI DSS.*