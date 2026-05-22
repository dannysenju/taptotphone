# 🔌 Módulo: Integración jPOS e ISO 8583

Este documento detalla la integración del motor financiero **jPOS (versión 2.0.4)** en un ecosistema de microservicios con **Spring Boot 3.3.0** y **Java 21**, describiendo la estructura del empaquetador XML, la decodificación de tramas y la asignación de campos financieros.

---

## 1. Ciclo de Vida de jPOS en Spring Boot

El servidor TCP embebido de jPOS es administrado directamente por el contenedor de inversión de control (IoC) de Spring Boot mediante la clase `JposServerAdapter`.

- **Auto-Bootstrapping**: Al completarse la inicialización de la aplicación (`@EventListener(ApplicationReadyEvent.class)`), se instancia y arranca el `ISOServer` de jPOS en el puerto configurado (por defecto `6000`).
- **Loom Integration**: En lugar de bloquear hilos pesados del sistema operativo, el bucle principal de escucha TCP y la ejecución de la lógica transaccional se levantan sobre **Virtual Threads** nativos de Java 21, garantizando una alta escalabilidad con mínima huella de memoria:
  ```java
  Thread.startVirtualThread(() -> {
      log.info("jPOS ISOServer active and listening on TCP port 6000");
      isoServer.run();
  });
  ```

---

## 2. Configuración del Empaquetador XML (GenericPackager)

Para desestructurar la trama de bytes que llega por la conexión TCP en campos y estructuras manejables de Java, el sistema utiliza un archivo de especificación XML del protocolo ISO-8583 (edición 1987).

El archivo está guardado en `src/main/resources/packager/iso87a.xml` y define las clases de empaquetado concretas de jPOS para cada campo:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE isopackager SYSTEM "genericpackager.dtd">
<isopackager>
  <!-- MTI (Message Type Identifier) -->
  <isofield id="0" length="4" name="MESSAGE TYPE IDENTIFIER" class="org.jpos.iso.IFA_NUMERIC"/>
  <!-- Primary Bitmap -->
  <isofield id="1" length="16" name="BITMAP" class="org.jpos.iso.IFA_BITMAP"/>
  <!-- Primary Account Number (PAN) -->
  <isofield id="2" length="19" name="PRIMARY ACCOUNT NUMBER" class="org.jpos.iso.IFA_NUMERIC"/>
  <!-- Processing Code -->
  <isofield id="3" length="6" name="PROCESSING CODE" class="org.jpos.iso.IFA_NUMERIC"/>
  <!-- Amount, Transaction (En centavos) -->
  <isofield id="4" length="12" name="AMOUNT, TRANSACTION" class="org.jpos.iso.IFA_NUMERIC"/>
  <!-- System Trace Audit Number (STAN) -->
  <isofield id="11" length="6" name="SYSTEM TRACE AUDIT NUMBER" class="org.jpos.iso.IFA_NUMERIC"/>
  <!-- Track 2 Data (Datos del chip / banda) -->
  <isofield id="35" length="37" name="TRACK 2 DATA" class="org.jpos.iso.IFA_LLLCHAR"/>
  <!-- Authorization Identification Response Code -->
  <isofield id="38" length="6" name="AUTHORIZATION IDENTIFICATION RESPONSE" class="org.jpos.iso.IFA_CHAR"/>
  <!-- Response Code (RC - Ej. '00' aprobado) -->
  <isofield id="39" length="2" name="RESPONSE CODE" class="org.jpos.iso.IFA_CHAR"/>
  <!-- Card Acceptor Terminal Identification (Terminal ID) -->
  <isofield id="41" length="8" name="CARD ACCEPTOR TERMINAL IDENTIFICATION" class="org.jpos.iso.IFA_CHAR"/>
  <!-- Card Acceptor Identification Code (Merchant ID) -->
  <isofield id="42" length="15" name="CARD ACCEPTOR IDENTIFICATION CODE" class="org.jpos.iso.IFA_CHAR"/>
  <!-- Currency Code, Transaction (ISO 4217 - Ej: '840' USD) -->
  <isofield id="49" length="3" name="CURRENCY CODE, TRANSACTION" class="org.jpos.iso.IFA_NUMERIC"/>
</isopackager>
```

---

## 3. Recepción y Construcción del Mensaje (ISOMsg)

El adaptador de entrada implementa la interfaz `ISORequestListener` de jPOS. Al llegar un mensaje de solicitud de autorización (`0200`):

1. **Extracción Segura de PAN**: El PAN de la tarjeta puede venir en el Campo 2 (`PRIMARY ACCOUNT NUMBER`) o dentro del Campo 35 (`TRACK 2 DATA`) decodificado con separadores estándar (`=` o `D`). El adaptador contiene lógica robusta para desestructurarlo de ambos orígenes.
2. **Conversión de Tipos**: El Campo 4 (Monto) viene representado en centavos como un entero relleno de ceros de 12 posiciones (ej. `000000015000` = `$150.00`). El adaptador lo convierte a `BigDecimal` dividiéndolo por 100 de forma precisa.
3. **Mapeo a Objeto de Dominio**: Instancia un Value Object `PAN` (que valida la estructura matemática mediante la fórmula de Luhn) y crea la entidad de negocio `Transaction`:

```java
// Extracción de datos del ISOMsg recibido
String panStr = extractPan(msg);
BigDecimal amount = new BigDecimal(msg.getString(4)).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
String terminalId = msg.getString(41);

PAN pan = new PAN(panStr);
Transaction tx = new Transaction(UUID.randomUUID(), pan, amount, "840", "MERCHANT01", terminalId, "123000", "000100");
```

---

## 4. Ruteo síncrono y mapeo MUX (ASCIIChannel)

Para enviar la respuesta financiera síncronamente al dispositivo móvil emisor que inició la comunicación:

- **ASCIIChannel**: Establece una longitud de cabecera de 4 bytes ASCII indicando cuántos bytes siguen en la trama TCP, permitiendo al socket segmentar los paquetes de datos de manera limpia en flujos de red continuos.
- **Correlación de Respuestas**: Cuando la lógica de negocio finaliza de validar la transacción mediante los puertos correspondientes:
  1. Clona el mensaje original mediante `(ISOMsg) msg.clone()`.
  2. Ejecuta `response.setResponseMTI()`, convirtiendo automáticamente el MTI de solicitud `0200` a una respuesta `0210`.
  3. Mapea el código de respuesta del negocio en el Campo 39 (`00` aprobado, `14` tarjeta inválida, `96` error de sistema).
  4. Agrega el código de autorización generado en el Campo 38.
  5. Envía de regreso la respuesta mediante la llamada de socket segura `source.send(response)`.

```java
// Código de envío en el adaptador
response.set(39, processedTx.getResponseCode()); // RC
if (processedTx.getApprovalCode() != null) {
    response.set(38, processedTx.getApprovalCode()); // Código de aprobación de 6 dígitos
}
source.send(response); // Transmisión síncrona TCP por el ASCIIChannel
```
*Esta integración completa garantiza transacciones asiladas en microsegundos aprovechando las bondades del motor transaccional jPOS sobre la robustez moderna de Spring Boot.*