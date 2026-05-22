package danny.com.taptotphone.infrastructure.adapters.inbound.jpos;

import danny.com.taptotphone.domain.model.PAN;
import danny.com.taptotphone.domain.model.Transaction;
import danny.com.taptotphone.application.ports.inbound.ProcessTransactionUseCase;
import org.jpos.iso.*;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.GenericPackager;
import org.jpos.iso.packager.ISO87APackager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Primary inbound adapter that programmatically manages an embedded jPOS ISOServer on TCP Port 6000.
 * Listens for ISO-8583 messages, handles connections, parses Fields (Field 41 as MUX key),
 * and dispatches processing tasks inside native Java 21 Virtual Threads.
 */
@Component
public class JposServerAdapter implements ISORequestListener {
    private static final Logger log = LoggerFactory.getLogger(JposServerAdapter.class);

    private final ProcessTransactionUseCase processTransactionUseCase;
    private final int port;
    private final ResourceLoader resourceLoader;
    private ISOServer isoServer;

    public JposServerAdapter(ProcessTransactionUseCase processTransactionUseCase,
                             @Value("${taptotphone.jpos.port:6000}") int port,
                             ResourceLoader resourceLoader) {
        this.processTransactionUseCase = processTransactionUseCase;
        this.port = port;
        this.resourceLoader = resourceLoader;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startServer() {
        log.info("Bootstrapping embedded jPOS ISO-8583 server on port {}...", port);
        try {
            ISOPackager packager = loadPackager();
            
            // Set up ASCII Channel (10 second timeout)
            ServerChannel channel = new ASCIIChannel(packager);
            
            this.isoServer = new ISOServer(port, channel, null);
            this.isoServer.addISORequestListener(this);

            // Execute the server run method in a Virtual Thread
            Thread.startVirtualThread(() -> {
                log.info("jPOS ISOServer active and listening on TCP port {}", port);
                isoServer.run();
            });

        } catch (Exception e) {
            log.error("Fatal error starting jPOS ISO Server on port {}", port, e);
            throw new IllegalStateException("Could not start jPOS ISOServer", e);
        }
    }

    private ISOPackager loadPackager() {
        try {
            Resource resource = resourceLoader.getResource("classpath:packager/iso87a.xml");
            if (resource.exists()) {
                log.info("Loading custom GenericPackager from XML configuration: {}", resource.getURI());
                try (InputStream is = resource.getInputStream()) {
                    return new GenericPackager(is);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load GenericPackager from XML. Falling back to default programmatic ISO87APackager", e);
        }
        log.info("Using native programmatic ISO87APackager");
        return new ISO87APackager();
    }

    @Override
    public boolean process(ISOSource source, ISOMsg msg) {
        // Enforce virtual thread boundary per incoming transaction request
        Thread.startVirtualThread(() -> {
            log.info("Received ISO message from source. MTI: {}", getMtiSafe(msg));
            ISOMsg response = null;
            try {
                response = (ISOMsg) msg.clone();
                response.setResponseMTI();

                // Extract Fields
                String mti = msg.getMTI();
                String processingCode = msg.getString(3);
                String amountStr = msg.getString(4);
                String stan = msg.getString(11);
                String transmissionTime = msg.getString(7);
                String terminalId = msg.getString(41); // Field 41 MUX Routing Key
                String merchantId = msg.getString(42);
                String currencyCode = msg.getString(49);
                String panStr = extractPan(msg);

                log.info("ISO Request - MTI: {}, PC: {}, STAN: {}, Terminal: {}", mti, processingCode, stan, terminalId);

                if (panStr == null) {
                    log.error("Rejecting transaction: Missing or invalid PAN");
                    response.set(39, "14"); // Invalid card number
                    source.send(response);
                    return;
                }

                if (terminalId == null || terminalId.isBlank()) {
                    log.error("Rejecting transaction: Missing Field 41 (Terminal ID)");
                    response.set(39, "58"); // Terminated transaction / Invalid Terminal
                    source.send(response);
                    return;
                }

                BigDecimal amount = BigDecimal.ZERO;
                if (amountStr != null && !amountStr.isBlank()) {
                    // Field 4 is represent in cents (12 digits) e.g., 000000010050 is $100.50
                    amount = new BigDecimal(amountStr).divide(new BigDecimal("100"), 2, BigDecimal.ROUND_HALF_UP);
                }

                PAN pan = new PAN(panStr);
                Transaction tx = new Transaction(
                        UUID.randomUUID(),
                        pan,
                        amount,
                        currencyCode != null ? currencyCode : "840",
                        merchantId != null ? merchantId : "UNKNOWN",
                        terminalId,
                        transmissionTime != null ? transmissionTime : "0000000000",
                        stan != null ? stan : "000000"
                );

                // Process through the hexagonal domain core
                Transaction processedTx = processTransactionUseCase.process(tx);

                // Map response fields back to the ISO response
                response.set(39, processedTx.getResponseCode());
                if (processedTx.getApprovalCode() != null) {
                    response.set(38, processedTx.getApprovalCode());
                }

                log.info("Sending ISO Response - MTI: {}, RC: {}, Auth: {}", response.getMTI(), processedTx.getResponseCode(), processedTx.getApprovalCode());
                source.send(response);

            } catch (ISOException e) {
                log.error("ISO Exception processing incoming message", e);
                sendSystemErrorResponse(source, response, "96");
            } catch (IllegalArgumentException e) {
                log.warn("Validation failure in domain request parsing", e);
                sendSystemErrorResponse(source, response, "14"); // Invalid Card / Luhn fail
            } catch (Exception e) {
                log.error("Unexpected runtime exception in ISO receiver", e);
                sendSystemErrorResponse(source, response, "96");
            }
        });
        return true;
    }

    private String extractPan(ISOMsg msg) {
        // PAN can be in Field 2 (Primary Account Number) or Field 35 (Track 2 Data)
        if (msg.hasField(2)) {
            return msg.getString(2);
        }
        if (msg.hasField(35)) {
            String track2 = msg.getString(35);
            // Track 2 is format: PAN=EXPIRATION_DATE... or PAN=SERVICE_CODE...
            // Standard separator is '=' or 'D'
            if (track2 != null) {
                String[] parts = track2.split("[=D]");
                if (parts.length > 0 && parts[0].matches("\\d+")) {
                    return parts[0];
                }
            }
        }
        return null;
    }

    private void sendSystemErrorResponse(ISOSource source, ISOMsg response, String rc) {
        try {
            if (response == null) {
                response = new ISOMsg();
                response.setMTI("0210");
            }
            response.set(39, rc);
            source.send(response);
        } catch (Exception ex) {
            log.error("Critical failure sending recovery error response", ex);
        }
    }

    private String getMtiSafe(ISOMsg msg) {
        try {
            return msg.getMTI();
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
}
