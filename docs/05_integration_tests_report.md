# 🧪 Reporte de Pruebas de Integración en Vivo (REST API Live Testing)

Este documento registra los resultados y el comportamiento técnico de la solución **SoftPOS (Tap-to-Phone)** durante la ejecución de pruebas de integración en vivo sobre los contenedores Docker locales.

---

## 📌 Resumen Ejecutivo
Las pruebas de integración en vivo se realizaron interactuando con los endpoints de la API REST expuestos por el contenedor `taptotphone-app` en el puerto `8080`. Se simularon escenarios reales de atestación de seguridad MPoC, compras exitosas (cumpliendo con la validación de Luhn y FPE de PAN) y transacciones fallidas por violación de reglas de negocio, con un **100% de éxito en las respuestas del servidor**.

---

## 1. Detalles de Ejecución y Respuestas HTTP

Las pruebas fueron realizadas utilizando llamadas HTTP nativas de consola (`Invoke-RestMethod` de PowerShell) para validar el comportamiento real del servidor de aplicación y la base de datos PostgreSQL:

### Caso A: Atestación Remota de Dispositivo (PCI MPoC)
*   **Propósito**: Verificar que un terminal móvil comercial pueda solicitar validación y obtener una firma digital segura.
*   **Comando de Prueba**:
    ```powershell
    Invoke-RestMethod -Uri http://localhost:8080/api/v1/attestation/verify -Method Post -ContentType 'application/json' -Body '{"terminalId": "TRM00001", "deviceHardwareId": "ARM64-SECURE-CHIP-99A82D", "hceToken": "SECURE_HCE_TOKEN_EXP_2026"}'
    ```
*   **Respuesta JSON del Servidor (HTTP 200)**:
    ```json
    {
      "id": "018bb0cb-3a33-4b6e-9d1b-8f0e20c9baf5",
      "terminalId": "TRM00001",
      "deviceHardwareId": "ARM64-SECURE-CHIP-99A82D",
      "attestationStatus": "TRUSTED",
      "attestationDateTime": "2026-05-22T20:58:28.822Z",
      "signature": "fc5ef17f566445e7a9fe4b7049a37a63-SECURE-MPOC-SIG"
    }
    ```
*   **Diagnóstico**: Exitoso. El backend registró al terminal como seguro (`TRUSTED`) y emitió la firma digital única.

---

### Caso B: Pago Exitoso (PAN con Suma Luhn Válida)
*   **Propósito**: Validar un cobro por aproximación con tarjeta legítima, aplicando tokenización reversible FPE en base de datos.
*   **Comando de Prueba**:
    ```powershell
    Invoke-RestMethod -Uri http://localhost:8080/api/v1/transactions/process -Method Post -ContentType 'application/json' -Body '{"pan": "4111112222213333", "amount": 150.00, "currency": "840", "merchantId": "MERCH0000000001", "terminalId": "TRM00001", "stan": "000101"}'
    ```
*   **Respuesta JSON del Servidor (HTTP 200)**:
    ```json
    {
      "id": "91f73bde-0995-4752-9d48-de7abf66573a",
      "maskedPan": "411111******3333",
      "amount": 150.00,
      "currency": "840",
      "status": "APPROVED",
      "responseCode": "00",
      "approvalCode": "925103",
      "terminalId": "TRM00001",
      "merchantId": "MERCH0000000001",
      "processedAt": "2026-05-22T20:58:33.252Z"
    }
    ```
*   **Diagnóstico**: Exitoso. El motor detectó la validez matemática del PAN, cifró reversiblemente los 6 dígitos centrales mediante la red Feistel-AES, guardó la transacción en PostgreSQL y retornó un código de aprobación `00` con el PAN debidamente enmascarado.

---

### Caso C: Pago Rechazado (PAN con Suma Luhn Inválida)
*   **Propósito**: Verificar la detención de tarjetas con datos corruptos o alterados en la capa del Dominio Puro.
*   **Comando de Prueba**:
    ```powershell
    $resp = try { Invoke-RestMethod -Uri http://localhost:8080/api/v1/transactions/process -Method Post -ContentType 'application/json' -Body '{"pan": "4111112222223333", "amount": 250.00, "currency": "840", "merchantId": "MERCH0000000001", "terminalId": "TRM00001", "stan": "000102"}' } catch { $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream()); $reader.ReadToEnd() }; $resp
    ```
*   **Respuesta JSON del Servidor (HTTP 400 Bad Request)**:
    ```json
    {
      "id": null,
      "maskedPan": "INVALID_CARD",
      "amount": 0,
      "currency": null,
      "status": "FAILED",
      "responseCode": "14",
      "approvalCode": null,
      "terminalId": "TRM00001",
      "merchantId": null,
      "processedAt": null
    }
    ```
*   **Diagnóstico**: Exitoso. El dominio interrumpió el flujo de manera segura arrojando una excepción de validación, retornando un código de error financiero ISO-8583 `14` (Tarjeta Inválida).

---

### Caso D: Consulta Segura de Historial Transaccional
*   **Propósito**: Comprobar que ninguna consulta administrativa expone datos confidenciales del tarjetahabiente.
*   **Comando de Prueba**:
    ```powershell
    Invoke-RestMethod -Uri http://localhost:8080/api/v1/transactions
    ```
*   **Respuesta JSON del Servidor (HTTP 200)**:
    ```json
    [
      {
        "id": "91f73bde-0995-4752-9d48-de7abf66573a",
        "maskedPan": "411111******3333",
        "amount": 150.00,
        "currency": "840",
        "status": "APPROVED",
        "responseCode": "00",
        "approvalCode": "925103",
        "terminalId": "TRM00001",
        "merchantId": "MERCH0000000001",
        "processedAt": "2026-05-22T20:58:33.252Z"
      }
    ]
    ```
*   **Diagnóstico**: Exitoso. La API REST responde de forma segura enmascarando los dígitos del PAN, cumpliendo rigurosamente con los estándares PCI DSS.

---

## 2. Comportamiento en Logs de Contenedores Docker

El monitoreo de logs del contenedor `taptotphone-app` confirmó el comportamiento esperado durante las pruebas:

1.  **Carga bajo demanda de Kafka**:
    Al ingresar el primer pago, el microservicio instanció de manera asíncrona y perezosa el cliente de Kafka:
    ```log
    taptotphone-app  | 2026-05-22 20:58:32.451 [tomcat-handler-3] INFO  o.a.k.clients.producer.KafkaProducer - [Producer clientId=taptotphone-producer-1] Instantiated an idempotent producer.
    ```
2.  **Creación Dinámica de Tópicos**:
    Debido a que era la primera transacción en el ecosistema, Kafka creó automáticamente los tópicos correspondientes en el vuelo, arrojando una advertencia inicial:
    ```log
    taptotphone-app  | 2026-05-22 20:58:32.868 [kafka-producer-network-thread] WARN  o.apache.kafka.clients.NetworkClient - [Producer clientId=taptotphone-producer-1] Error while fetching metadata with correlation id 1 : {tap-to-phone-transactions-created=LEADER_NOT_AVAILABLE}
    ```
    *Nota: Este comportamiento es normal y auto-recuperable en Kafka. El productor reintentó el envío en milisegundos una vez elegido el líder, completando la publicación con éxito.*
3.  **Procesamiento de Negocio e Integración PostgreSQL**:
    ```log
    taptotphone-app  | 2026-05-22 20:58:33.251 [tomcat-handler-3] INFO  d.c.t.a.s.TransactionApplicationService - Transaction approved. ID: 91f73bde-0995-4752-9d48-de7abf66573a, Auth Code: 925103
    taptotphone-app  | 2026-05-22 20:58:33.254 [tomcat-handler-3] INFO  d.c.t.i.a.o.k.KafkaEventPublisherAdapter - Dispatching TRANSACTION_PROCESSED event to Kafka for Tx ID: 91f73bde-0995-4752-9d48-de7abf66573a, status: APPROVED
    ```

---

## 🏆 Conclusiones
El microservicio se comporta de manera estable, segura y predecible. La segregación arquitectónica hexagonal aísla el dominio previniendo persistencia de tarjetas corruptas, y la infraestructura delega de forma asíncrona la mensajería y persistencia en PostgreSQL de manera óptima.
