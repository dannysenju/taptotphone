package danny.com.taptotphone.client;

import org.jpos.iso.ISOException;
import org.jpos.iso.ISOMsg;
import org.jpos.iso.channel.ASCIIChannel;
import org.jpos.iso.packager.ISO87APackager;

import java.io.IOException;

/**
 * Interactive Client Simulator that connects directly to the embedded SoftPOS jPOS server via TCP.
 * Constructs and transmits a standard ISO-8583 0200 message over an ASCIIChannel,
 * and parses/displays the returned 0210 transaction approval response.
 */
public class IsoClientSimulator {

    public static void main(String[] args) {
        String host = "localhost";
        int port = 6000;

        System.out.println("=========================================================");
        System.out.println("      SoftPOS Tap-to-Phone ISO-8583 TCP Client Simulator  ");
        System.out.println("=========================================================");
        System.out.println("Connecting to TCP Server on " + host + ":" + port + "...");

        try {
            // Initialize packager and ASCII Channel
            ISO87APackager packager = new ISO87APackager();
            ASCIIChannel channel = new ASCIIChannel(host, port, packager);

            channel.connect();
            System.out.println("Connection established successfully!\n");

            // Build ISO 8583 request msg (0200 Purchase Request)
            ISOMsg request = new ISOMsg();
            request.setPackager(packager);
            request.setMTI("0200");

            request.set(2, "4111112222213333");           // Field 2: PAN (Visa)
            request.set(3, "000000");                       // Field 3: Processing Code (Purchase)
            request.set(4, "000000015000");                 // Field 4: Amount ($150.00, in cents)
            request.set(7, "0522152200");                   // Field 7: Transmission date/time (MMDDhhmmss)
            request.set(11, "123456");                      // Field 11: System Trace Audit Number (STAN)
            request.set(41, "TRM00001");                    // Field 41: Card Acceptor Terminal ID (MUX Key)
            request.set(42, "MERCH0000000001");             // Field 42: Card Acceptor Merchant ID
            request.set(49, "840");                         // Field 49: Currency Code (USD)

            System.out.println("Sending 0200 ISO-8583 message details:");
            printIsoMessage(request);

            channel.send(request);
            System.out.println("Message transmitted. Waiting for host authorization response...");

            // Receive response (0210 Purchase Response)
            ISOMsg response = channel.receive();
            System.out.println("\nResponse received from host!");
            System.out.println("Parsed 0210 ISO-8583 message details:");
            printIsoMessage(response);

            // Display result
            String rc = response.getString(39);
            String authCode = response.getString(38);
            System.out.println("---------------------------------------------------------");
            if ("00".equals(rc)) {
                System.out.println("TRANSACTION STATUS: APPROVED");
                System.out.println("Approval Authorization Code (Field 38): " + authCode);
            } else {
                System.out.println("TRANSACTION STATUS: DECLINED / FAILED");
                System.out.println("Response Error Code (Field 39): " + rc);
            }
            System.out.println("---------------------------------------------------------");

            channel.disconnect();
            System.out.println("Disconnected from server.");

        } catch (IOException e) {
            System.err.println("TCP Socket Error connecting to jPOS Server: " + e.getMessage());
            System.err.println("Ensure the Spring Boot application is running and listening on port " + port + "!");
        } catch (ISOException e) {
            System.err.println("ISO Packager / Format Error: " + e.getMessage());
        }
        System.out.println("=========================================================");
    }

    private static void printIsoMessage(ISOMsg msg) throws ISOException {
        System.out.println("  MTI: " + msg.getMTI());
        for (int i = 1; i <= msg.getMaxField(); i++) {
            if (msg.hasField(i)) {
                String val = msg.getString(i);
                // Obfuscate PAN for print security if displaying raw Field 2
                if (i == 2 && val != null && val.length() >= 10) {
                    val = val.substring(0, 6) + "******" + val.substring(val.length() - 4);
                }
                System.out.printf("  Field %-3d : [%s]\n", i, val);
            }
        }
        System.out.println();
    }
}
