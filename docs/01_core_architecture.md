# 📐 01_core_architecture (Arquitectura Core y Configuración Técnica)

Este documento detalla las especificaciones de la arquitectura hexagonal, dependencias, optimización de concurrencia y despliegue del motor transaccional SoftPOS Core.

---

## 1. Arquitectura de Código y Dependencias

El sistema se rige bajo los principios de la **Arquitectura Hexagonal Estricta (DDD)**, aislando el núcleo transaccional y las reglas de negocio financiero de las tecnologías de persistencia y comunicación externa.

```
danny.com.taptotphone
│
├── domain (Núcleo de Dominio Puro)
│   └── model (Entidades y Objetos de Valor - Ej. PAN, Transaction)
│
├── application (Casos de Uso e Interfaces)
│   ├── ports
│   │   ├── inbound (Ej. ProcessTransactionUseCase, DeviceAttestationUseCase)
│   │   └── outbound (Ej. TransactionRepositoryPort, EventPublisherPort, FpeEncryptionPort)
│   └── service (Orquestador de Aplicación - TransactionApplicationService)
│
└── infrastructure (Adaptadores de Tecnología Específicos)
    └── adapters
        ├── inbound (jPOS TCP Server, Spring Web REST Controllers)
        └── outbound (PostgreSQL Data JPA, Apache Kafka, Feistel FPE Cipher, MPoc Cloud Simulator)
```

### Dependencias Principales (`pom.xml`):
- **Spring Boot 3.3.0** (Web, Data JPA, Kafka)
- **Java 21** como base de compilación nativa (Virtual Threads y Records).
- **jPOS 2.0.4** para la mensajería financiera ISO-8583 TCP.
- **PostgreSQL Driver** & **HikariCP** para persistencia de alta velocidad.

---

## 2. Optimizaciones de Concurrencia (Virtual Threads - Project Loom)

Para soportar miles de terminales móviles enviando transacciones simultáneas sin agotar los recursos de hardware, la aplicación utiliza **Virtual Threads (Project Loom)**. 

### Configuración en `application.yml`:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

### Ejecución en el Servidor jPOS:
Al recibir una conexión en el puerto `6000`, el servidor delega el procesamiento completo de la trama y la lógica de negocio a un hilo virtual ultra-liviano de Java 21:
```java
Thread.startVirtualThread(() -> {
    // Procesamiento de trama ISO-8583
    // Cifrado FPE del PAN
    // Persistencia y comunicación externa
});
```
Esto elimina la necesidad de pre-asignar enormes pools de hilos tradicionales de sistema operativo (`Platform Threads`), optimizando drásticamente la latencia y la memoria.

---

## 3. Contenerización y Orquestación

La aplicación está diseñada para desplegarse de manera escalable e inmutable mediante contenedores Docker.

### Dockerfile Optimizado (Multi-Stage Build):
```dockerfile
# Stage 1: Compilación
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Imagen de Ejecución Ligera
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/taptotphone-*.jar app.jar
EXPOSE 8080 6000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Orquestación Local:
A través de `docker-compose.yml`, se configuran las dependencias del ecosistema para auto-reparación en fallas:
- **`postgres:15-alpine`**: Expuesto en puerto `5432` con volumen persistente `pgdata`.
- **`cp-kafka / cp-zookeeper`**: Bus de eventos asíncronos expuesto en puerto `9092`.
- **`taptotphone-app`**: El microservicio core con dependencias de inicio saludable (`service_healthy`) de la base de datos.

---

## 4. Seguridad y Cumplimiento SoftPOS

### 4.1. Arquitectura de Seguridad PCI MPoC
El estándar **PCI MPoC (Mobile Payments on COTS)** exige mitigar el riesgo de ejecutar software de pago en dispositivos comerciales.
- **Atestación Remota**: El celular Android ejecuta comprobaciones locales (Play Integrity) y envía el token criptográfico al backend, que lo valida usando el adaptador `MPocAttestationPort` y lo firma digitalmente.
- **Tokenización en Base de Datos**: Los datos del PAN real nunca se almacenan en texto claro y no se imprimen en logs de consola, cumpliendo con la sección de protección de tarjetahabientes de PCI DSS.

### 4.2. Cifrado Preservador de Formato (FPE)
- Conserva el BIN (primeros 6 dígitos) y los últimos 4 dígitos intactos.
- Cifra secuencialmente los 6 dígitos centrales mediante una Red Feistel de 4 rondas con clave secreta AES-256.
- Permite almacenar una cadena numérica válida de 16 dígitos en base de datos PostgreSQL, protegiendo el PAN real de accesos indebidos pero manteniendo la integridad referencial y de ruteo financiero legacy.

---

## 5. Mensajería ISO 8583 (Configuración jPOS)

El canal de comunicación por excelencia de las redes financieras.

### Canales y Puertos:
- Escucha TCP abierta en puerto `6000`.
- **`org.jpos.iso.channel.ASCIIChannel`**: Utiliza 4 caracteres ASCII al inicio de la trama para indicar la longitud de la carga útil del mensaje financiero.
- **Generic Packager (`iso87a.xml`)**: El backend carga dinámicamente un packager XML que desglosa campos estructurados conforme al estándar ISO 8583 de 1987.
- **Mapeo MUX por Campo 41**: El adaptador utiliza el Campo 41 (Terminal ID) como clave de correlación para asociar flujos síncronos de petición-respuesta y evitar cruce de tramas concurrentes.