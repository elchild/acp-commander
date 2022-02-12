package acpcommander.acp;

import acpcommander.acp.toolkit.reply.AcpReply;
import acpcommander.acp.toolkit.reply.AcpReplyType;
import acpcommander.acp.toolkit.AcpCommunication;
import acpcommander.acp.toolkit.AcpEncryption;
import acpcommander.acp.toolkit.AcpPacketCreator;
import acpcommander.acp.toolkit.AcpParser;
import acpcommander.util.*;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Beschreibung: Core class for sending ACP commands to Buffalo Linkstation (R). Out
 * of the work of linkstationwiki.net</p>
 *
 * <p>Copyright: Copyright (c) 2006, GPL</p>
 *
 * <p>Organisation: linkstationwiki.net</p>
 *
 * @author Georg
 * @version 0.4.1 (beta)
 */
public class AcpDevice {
    private ScopedLogger log;

    private InetAddress targetIp;
    public Integer bindPort = 0;
    public Integer port = 22936;
    private String connectionId; // connection ID, "unique" identifier for the connection
    private String targetMacAddress; // MAC address of the LS, it reacts only if correct MAC or FF:FF:FF:FF:FF:FF is set in the packet

    protected byte[] key = new byte[4]; // Key for password encryption sent in reply to ACP discovery packet
    protected String password;
    private final String staticPassword = "ap_servd"; //AH: This seems to be some kind of static password

    protected final int maxPacketResendAttempts = 2; // standard value for repeated sending of packets
    protected final int defaultReceiveBufferSize = 4096; // standard length of receive buffer

    private CannedMessages cannedMessages;
    private AcpEncryption encryption;
    private AcpPacketCreator packetCreator;
    private AcpCommunication communication;

    public AcpDevice(ScopedLogger log, String newTarget) {
        this.log = log;
        configureLogDependencies();
        setTarget(newTarget);
    }

    public AcpDevice(ScopedLogger log, byte[] newTarget) {
        this.log = log;
        configureLogDependencies();
        setTarget(newTarget);
    }

    private void configureLogDependencies(){
        cannedMessages = new CannedMessages(log);
        encryption = new AcpEncryption(log, StandardCharsets.UTF_8);
        packetCreator = new AcpPacketCreator(log, StandardCharsets.UTF_8);
    }

    //
    //  set/get for private variables
    //

    public void setConnectionId(String connectionId) {
        // TODO: input param checking!
        this.connectionId = connectionId;
    }

    public String getTargetMac() {
        return targetMacAddress;
    }

    public void setTargetMac(String newTargetMac) {
        // TODO: input param checking!
        targetMacAddress = newTargetMac;
    }

    public void setPassword(String newPassword) {
        password = newPassword;
    }

    public InetAddress getTarget() {
        return targetIp;
    }

    public void setTarget(String newTarget) {
        try {
            targetIp = InetAddress.getByName(newTarget);
        } catch (UnknownHostException ex) {
            cannedMessages.logUnknownTargetHost();
            log.outError(ex + " [in setTarget]");
        }
    }

    public void setTarget(byte[] newTarget) {
        try {
            targetIp = InetAddress.getByAddress(newTarget);
        } catch (UnknownHostException ex) {
            cannedMessages.logUnknownTargetHost();
            log.outError(ex + " [in setTarget]");
        }
    }

    public void bind(InetSocketAddress localip) {
        if (localip.isUnresolved()) {
            log.outWarning(
                "The bind address " + localip + " given with parameter \"-b\" could not be resolved to a local IP-Address.\n"
                + "You must use this parameter with a valid IP-Address that belongs to the PC you run acp_commander on.\n"
            );
        }

        communication = new AcpCommunication(log, localip);
    }

    public void bind(String localip) {
        if(localip == "" || localip == null){
            communication = new AcpCommunication(log, bindPort);
            return;
        }

        // bind socket to a local address (-b)
        // Create a socket address from a hostname (_bind) and a port number. A port number
        // of zero (default) will let the system pick up an ephemeral port in a bind operation.
        if (!localip.equalsIgnoreCase("")) {
            bind(new InetSocketAddress(localip, bindPort));
        }
    }

    /*public String getconnid() {
        return connectionId.toString();
    }*/

    /*public void setConnectionId(byte[] connectionid) {
        // TODO: input param checking!
        connectionId = takeHexFromPacket(connectionid, 0, 6);
    }*/

    /*public byte[] getTargetKey() {
        byte[] result = new byte[key.length];
        System.arraycopy(key, 0, result, 0, key.length);
        return (result);
    }*/

    /*public void setTargetKey(byte[] newkey) {
        // TODO: input param checking!
        if (newkey.length != 4) {
            outError("ACPException: Encryption key must be 4 bytes long!");
            return;
        }
        System.arraycopy(newkey, 0, key, 0, newkey.length);
    }*/

    /*public void setTargetKey(String newkey) {
        // TODO: input param checking!
        setTargetKey(hexToByte(newkey));
    }*/

    /*public void setbroadcastip(String newtarget) {
        try {
            targetIp = InetAddress.getByName(newtarget);
            setTargetMac("FF:FF:FF:FF:FF:FF");
        } catch (UnknownHostException ex) {
            outError(ex.toString() + " [in setbroadcastip]");
        }
    }*/

    /*public void setbroadcastip(byte[] newtarget) {
        try {
            targetIp = InetAddress.getByAddress(newtarget);
            setTargetMac("FF:FF:FF:FF:FF:FF");
        } catch (UnknownHostException ex) {
            outError(ex.toString() + " [in setbroadcastip]");
        }
    }*/

    //
    // ACP functionality
    //

    public AcpReply find() {
        // discover linkstations by sending an ACP-Discover package
        // return on line of formatted string per found LS
        return doDiscover();
    }

    /**
     * AH: Send a shell command to the device
     * @param cmd The command to send to the device
     * @param maxResend How many attempts should be made to execute the command when no response is received
     * @return The decoded reply received from the device
     */
    public AcpReply command(String cmd, int maxResend) {
        // send telnet-type command cmd to Linkstation by acpcmd
        enOneCmd();
        authenticate();

        if (maxResend <= 0) {
            maxResend = maxPacketResendAttempts;
        }

        return doTransaction(packetCreator.getAcpCmdPacket(connectionId, targetMacAddress, cmd), maxResend, 60000);
    }

    public AcpReply command(String cmd) {
        // send telnet-type command cmd to Linkstation by acpcmd - only send packet once!

        return command(cmd, 1);
    }

    public AcpReply authenticate(byte[] encpassword) {
        // authenticate to ACP protokoll
        return doTransaction(packetCreator.getAcpAuthPacket(connectionId, targetMacAddress, encpassword));
    }

    public AcpReply authenticate() {
        byte[] encrypted = encryption.encryptAcpPassword(password, key);
        return authenticate(encrypted);
    }

    public AcpReply shutdown() {
        // ENOneCmd protected
        return doTransaction(packetCreator.getAcpShutdownPacket(connectionId, targetMacAddress));
    }

    public AcpReply reboot() {
        // ENOneCmd protected
        return doTransaction(packetCreator.getAcpRebootPacket(connectionId, targetMacAddress));
    }

    public AcpReply emMode() {
        // ENOneCmd protected
        return doTransaction(packetCreator.getAcpEmModePacket(connectionId, targetMacAddress));
    }

    public AcpReply normMode() {
        // ENOneCmd protected
        return doTransaction(packetCreator.getAcpNormModePacket(connectionId, targetMacAddress));
    }

    public AcpReply blinkLed() {
        return doTransaction(packetCreator.getAcpBlinkLedPacket(connectionId, targetMacAddress), maxPacketResendAttempts, 60000);
    }

    public AcpReply enOneCmd(byte[] encPassword) {
        return doTransaction(packetCreator.getAcpEnOneCmdPacket(connectionId, targetMacAddress, encPassword));
    }

    public AcpReply enOneCmd() {
        return enOneCmd(encryption.encryptAcpPassword(staticPassword, key));
    }

    public AcpReply setWebUiLanguage(byte language) {
        // interface to switch web GUI language
        // ENOneCmd protected
        // 0 .. Japanese
        // 1 .. English
        // 2 .. German
        // default .. English   AH: Mine defaults to Japanese. Help.
        return doTransaction(packetCreator.getAcpWebUiLanguagePacket(connectionId, targetMacAddress, language));
    }

    public AcpReply changeIp(byte[] newIp, byte[] newMask, boolean useDhcp) {
        // change IP address
        byte[] encrypted = encryption.encryptAcpPassword(password, key);
        return doTransaction(packetCreator.getAcpChangeIpPacket(connectionId, targetMacAddress, newIp, newMask, useDhcp, encrypted), maxPacketResendAttempts, 10000);
    }

    /*public String[] saveconfig() {
        // set timeout to 1 min
        int mytimeout = timeout;
        timeout = 60000;
        String[] result = doTransaction(getacpsaveconfig(connectionId, targetMacAddress));
        timeout = mytimeout;
        return result;
    }*/

    /*public String[] loadconfig() {
        // set timeout to 1 min
        int mytimeout = timeout;
        timeout = 60000;
        String[] result = doTransaction(getacploadconfig(connectionId, targetMacAddress));
        timeout = mytimeout;
        return result;
    }*/

    /*public String[] debugmode() {
        return doTransaction(getacpdebugmode(connectionId, targetMacAddress));
    }*/

    //--- End of public routines ---


    //
    // ACP-Interface functions (private)
    //

    private AcpReply doDiscover() {
        String state = "[Send/Receive ACPDiscover]";

        byte[] discoverPacket = packetCreator.getAcpDiscoverPacket(connectionId, targetMacAddress);
        byte[] newDiscoverPacket = packetCreator.getAcpNewDiscoverPacket(connectionId, targetMacAddress);

        AcpReply singleDeviceResponse;
        AcpReply multiDeviceResponse = new AcpReply();

        DatagramSocket socket;

        DatagramPacket discoverPacketTransmittable = new DatagramPacket(discoverPacket, discoverPacket.length, targetIp, port);
        DatagramPacket newDiscoverPacketTransmittable = new DatagramPacket(newDiscoverPacket, newDiscoverPacket.length, targetIp, port);

        DatagramPacket responsePacketReceivable = new DatagramPacket(new byte[defaultReceiveBufferSize], defaultReceiveBufferSize); //AH: Prepare a packet object into which received data will be placed

        try {
            socket = communication.getSocket(3000); // TODO bind functionality is missing here

            socket.send(discoverPacketTransmittable);
            socket.send(newDiscoverPacketTransmittable);

            long lastSendTime = System.currentTimeMillis();

            List<String> knownDevices = new ArrayList<>();

            while (System.currentTimeMillis() - lastSendTime < 3000) { //AH: Whilst we still have time to keep receiving discovery responses
                socket.receive(responsePacketReceivable); //AH: Receive a packet into the prepared packet object

                singleDeviceResponse = decodeResponsePacket(responsePacketReceivable.getData(), log.debugLevel); // get search results

                if(knownDevices.contains(singleDeviceResponse.ip)){
                    //AH: Duplicate response from a device we already know about, skip.
                    continue;
                }

                knownDevices.add(singleDeviceResponse.ip);

                // TODO: do optional Discover event with _searchres
                multiDeviceResponse.extraInformation += (multiDeviceResponse.extraInformation.length() == 0 ? "" : "\n") + singleDeviceResponse.extraInformation; // add formatted string to result list
                multiDeviceResponse.associatedReplies.add(singleDeviceResponse);
            }
        } catch (SocketTimeoutException e) {
            // TimeOut should be OK as we wait until Timeout if we get packets
            log.outDebug("Timeout reached, stop listening to further Discovery replies",2);
        } catch (SocketException e) {
            // TODO: better error handling
            cannedMessages.logPortCommunicationFailure(port);
            log.outError("Exception: SocketException (" + e.getMessage() + ") " + state);
        } catch (IOException e) {
            // TODO: better error handling
            log.outError("Exception: IOException (" + e.getMessage() + ") " + state);
        }

        multiDeviceResponse.packetType = AcpReplyType.DiscoveryReply; //AH: Because we are forming the packet reply manually we need to set a type manually

        return multiDeviceResponse;
    }

    // send ACP packet and handle answer

    private AcpReply doTransaction(byte[] packet, int maxPacketResendAttempts, int timeout) {
        String intendedTransaction = "[ACP Send/Receive (Packet:" + (AcpParser.takeHexFromPacket(packet, 9, 1) + AcpParser.takeHexFromPacket(packet, 8, 1)) + " = " + AcpParser.getCommandStringFromPacket(packet) + ")]";

        int sendCount = 0;
        boolean sendAgain = true;

        DatagramPacket transmittablePacket = new DatagramPacket(packet, packet.length, targetIp, port);
        DatagramPacket inboundPacket = new DatagramPacket(new byte[defaultReceiveBufferSize], defaultReceiveBufferSize);

        String errorMessages = "";

        do {
            sendCount++;

            try {
                log.outDebug("Sending " + sendCount + "/" + maxPacketResendAttempts, 2);

                DatagramSocket socket = communication.getSocket(timeout);
                socket.send(transmittablePacket);
                socket.receive(inboundPacket);

                sendAgain = false; // we received an answer

                // TODO: do optional Receive-event with result
            } catch (SocketTimeoutException e) {
                // TODO: better error handling

                if (sendCount >= maxPacketResendAttempts) {
                    String errorMessage = "Exception: SocketTimeoutException (" + e.getMessage() + ") " + intendedTransaction;
                    errorMessages += errorMessage + "\n";

                    cannedMessages.logCommunicationTimeout(port);
                    log.outError(errorMessage);
                } else {
                    String errorMessage = "Timeout (" + intendedTransaction + " retry sending (" + sendCount + "/" + maxPacketResendAttempts + ")";
                    errorMessages += errorMessage + "\n";

                    log.outDebug(errorMessage, 1);
                }
            } catch (SocketException e) {
                // TODO: better error handling

                String errorMessage = "Exception: SocketException (" + e.getMessage() + ") " + intendedTransaction;
                errorMessages += errorMessage + "\n";

                cannedMessages.logPortCommunicationFailure(port);
                log.outError(errorMessage);
            } catch (IOException e) {
                // TODO: better error handling

                String errorMessage = "Exception: IOException (" + e.getMessage() + ") " + intendedTransaction;
                errorMessages += errorMessage + "\n";

                log.outError(errorMessage);
            }

        } while ((sendCount < maxPacketResendAttempts) && sendAgain); // repeat until max retries reached

        AcpReply result = decodeResponsePacket(inboundPacket.getData(), log.debugLevel); // get search results

        result.extraInformation = errorMessages + result.extraInformation; //AH: Prepend error messages to actual result. If we have a result, any error messages would have been previous to the result, hence the prepend

        return result;
    }

    private AcpReply doTransaction(byte[] buf, int maxPacketResendAttempts){
        return doTransaction(buf, maxPacketResendAttempts, 1000); //AH: Default to 1 second timeout
    }

    private AcpReply doTransaction(byte[] buf) {
        return doTransaction(buf, maxPacketResendAttempts);
    }


    //
    // ACP packet creation functionality
    //

    private void dumpResponsePacket(byte[] packet) {
        // very simple hex | char debug output of received packet for debugging
        try {
            log.outLn("Buffer-Length: " + packet.length);

            for (int row = 0; row < (packet.length / 16); row++) { //AH: Split the packet into rows of 16 bytes
                switch(row){
                    case 0: //AH: Row 0 and 1 are ACP Header bytes (32 bytes)
                        log.outLn("ACP-Header:");
                        break;

                    case 2: //AH: Row 2 and beyond are ACP Payload bytes
                        log.outLn("ACP-Payload:");
                        break;
                }

                log.out(row * 16 + "::\t"); //AH: Byte marker. Tells you which byte row is displayed

                for (int hexColumn = 0; hexColumn <= 15; hexColumn++) { //AH: Take 16 bytes for the row and print each one outLn as hex, with a space between
                    log.out(AcpParser.takeHexFromPacket(packet, row * 16 + hexColumn, 1) + " ");
                }

                log.out("\t"); //AH: Tab over to the character representation

                for (int charColumn = 0; charColumn <= 15; charColumn++) { //AH: Take 16 bytes for the row and print each one outLn as raw characters
                    byte charByte = packet[row * 16 + charColumn];

                    if ((charByte != 0x0A) & (charByte != 0x09)) {
                        log.out(String.valueOf((char) charByte));
                    } else {
                        log.out(" ");
                    }
                }

                log.outLn(""); //AH: Newline for the next row
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            log.outError(e.toString());
        }
    }

    /* Analyse ACPDisc answer packet, get hostname, hostIP, DHCP-state, FW-version
     * outACPrcvDisc(byte[] responsePacket, int debug)
     *  INPUT
     *    responsePacket      ... byte [], buffer with received data
     *    debug   ... int, debug state
     *  OUTPUT
     *    result   ... String [] string array with results of packet analysis
     *            0 - "ACPdiscovery reply" = packet type
     *            1 - formatted output
     *            2 - host name
     *            3 - IP
     *            4 - MAC
     *            5 - Product string
     *            6 - Product ID
     *            7 - FW version
     *            8 - key (used for pwd encryption in regular authentication process)
     */
    private AcpReply decodeDiscoveryResponsePacket(byte[] responsePacket) {
        AcpReply disassembledReply = new AcpReply();

        disassembledReply.packetType = AcpReplyType.DiscoveryReply;

        try {
            // get IP
            byte[] receivedTargetIp = new byte[4];

            for (int packetPos = 0; packetPos <= 3; packetPos++) {
                receivedTargetIp[packetPos] = responsePacket[35 - packetPos]; //AH: From position 35 backwards, read the IP outLn of the packet and into the target IP byte array
            }

            disassembledReply.ip = InetAddress.getByAddress(receivedTargetIp).toString().replace("/", "");

            // get host name
            int packetPosition = 48; //AH: Start at position 48 and iterate
            while ((responsePacket[packetPosition] != 0x00) & (packetPosition < responsePacket.length)) {
                disassembledReply.hostname += (char) responsePacket[packetPosition++]; //AH: Append a character from the packet buffer to the disassembledPacketInformation hostname
            }

            // Product ID string starts at byte 80
            packetPosition = 80;
            while ((responsePacket[packetPosition] != 0x00) & (packetPosition < responsePacket.length)) {
                disassembledReply.productIdString += (char) responsePacket[packetPosition++];
            }

            //AH: Product ID starts at byte 195 and is read in reverse to byte 192
            for (int packetPos = 3; packetPos >= 0; packetPos--) {
                disassembledReply.productId += responsePacket[192 + packetPos];
            }

            // MAC starts at byte 311, read outLn 6 bytes
            for (int packetPos = 0; packetPos <= 5; packetPos++) {
                disassembledReply.mac += AcpParser.takeHexFromPacket(responsePacket, packetPos + 311, 1);

                if (packetPos != 5) {
                    disassembledReply.mac += ":"; //AH: Place a colon after each byte, except the last one
                }
            }

            // Key - changes with connectionid (everytime) -> key to password encryption?
            for (int packetPos = 0; packetPos <= 3; packetPos++) {
                disassembledReply.key += AcpParser.takeHexFromPacket(responsePacket, 47 - packetPos, 1);
            }

            // Firmware version starts at 187
            disassembledReply.firmwareVersion = responsePacket[187] + responsePacket[188] + "." + responsePacket[189] + responsePacket[190];

            //AH: Compile all the information into a consistent syntax
            disassembledReply.extraInformation = "Hostname: " + disassembledReply.hostname + "\t";
            disassembledReply.extraInformation += "IP: " + disassembledReply.ip + "\t";
            disassembledReply.extraInformation += "IDStr: " + disassembledReply.productIdString + "\t";
            disassembledReply.extraInformation += "ID: " + disassembledReply.productId + "\t";
            disassembledReply.extraInformation += "MAC: " + disassembledReply.mac + "\t";
            disassembledReply.extraInformation += "Firmware: " + disassembledReply.firmwareVersion + "\t";
            //disassembledReply.extraInformation += "Key: " + disassembledReply.key + "\t"; //AH TODO: Add a command line switch to enable key output

            //AH TODO: Make a nice tabular output for this list, and add a command line switch to use the old output format instead

        } catch (UnknownHostException e) {
            log.outError(e.getMessage());
        }
        return disassembledReply;
    }

    /* Analyses incoming ACP Replys - TODO progress, still needs better handling
     *  decodeResponsePacket(byte[] packet, int debug)
     *  INPUT
     *    packet      ... byte [], buffer with received data
     *    debug   ... int, debug state
     *  OUTPUT
     *    result   ... String [] string array with results of packet analysis
     *            0 - "ACP... reply" = packet type
     *            1 - formatted output
     *             2..n - possible details (ACPdiscovery)
     */
    private AcpReply decodeResponsePacket(byte[] packet, int debug) {
        if (debug >= 3) {
            dumpResponsePacket(packet); //AH: Print outLn dump of received reply packet if the debug mode is set high enough
        }

        // get type of ACP answer both as long and hexstring
        int replyType = (packet[8] & 0xFF) + (packet[9] & 0xFF) * 256; // &0xFF necessary to avoid neg. values
        String acpReply = AcpParser.takeHexFromPacket(packet, 9, 1) + AcpParser.takeHexFromPacket(packet, 8, 1);

        //@georg check!
        // value = 0xFFFFFFFF if ERROR occured
        String acpStatus = AcpParser.takeHexFromPacket(packet, 31, 1) + AcpParser.takeHexFromPacket(packet, 30, 1) + AcpParser.takeHexFromPacket(packet, 29, 1) + AcpParser.takeHexFromPacket(packet, 28, 1);

        if (acpStatus.equalsIgnoreCase("FFFFFFFF")) {
            log.outDebug(
                "Received packet (" + acpReply + ") has the error-flag set!\n"
                + "For 'authenticate' that is (usually) OK as we do send a buggy packet.",
            1);
        }

        AcpReply result = new AcpReply();

        switch (replyType) {
            case 0xc020: // ACP discovery
            case 0xce00:
                log.outDebug("received ACP Discovery reply", 2);
                result = decodeDiscoveryResponsePacket(packet);
                break;

            case 0xc030: // ACP changeIP
                log.outDebug("received ACP change IP reply", 2);

                result.packetType = AcpReplyType.ChangeIpReply;
                result.extraInformation = AcpParser.getStatusMessageFromPacket(packet);

                break;

            case 0xc0a0: // ACP special command
                log.outDebug("received ACP special command reply", 2);

                result.packetType = AcpReplyType.SpecialCommandReply;
                result.extraInformation = AcpParser.getStatusMessageFromPacket(packet);
                break;

            case 0xca10: // acpcmd
                log.outDebug("received acpcmd reply", 2);

                result.packetType = AcpReplyType.ShellCommandReply;
                result.extraInformation = "";

                int packetPosition = 40;
                while((packet[packetPosition] != 0x00) && (packetPosition < packet.length)){
                    result.extraInformation += (char) packet[packetPosition++];
                }

                // filter the LSPro default answer "**no message**" as it led to some user queries/worries
                if (result.extraInformation.equalsIgnoreCase("**no message**")) {
                    result.extraInformation = "OK (" + AcpParser.getStatusMessageFromPacket(packet) + ")";
                }

                break;

            default:
                result.packetType = AcpReplyType.UnknownReply;
                result.extraInformation = "Unknown ACP-Reply packet: 0x" + acpReply;

                break;
        }

        log.outDebug("ACP analysis result: " + result.extraInformation, 2);

        return result;
    }
}
