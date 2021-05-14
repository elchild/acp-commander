package acpcommander;

import acpcommander.util.*;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;

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
public class ACP {
    private InetAddress targetIp;
    protected Integer port = 22936;
    private String connectionId; // connection ID, "unique" identifier for the connection
    private String targetMacAddress; // MAC address of the LS, it reacts only if correct MAC or FF:FF:FF:FF:FF:FF is set in the packet
    protected byte[] key = new byte[4]; // Key for password encryption sent in reply to ACP discovery packet
    protected String password;
    private String apservd = "ap_servd"; //AH: This seems to be some kind of static password

    public ScopedLogger log = new ScopedLogger(0);
    private CannedMessages cannedMessages = new CannedMessages(log);
    private AcpEncryption encryption = new AcpEncryption(Charset.forName("UTF-8"), log);
    private AcpPacketCreator packetCreator = new AcpPacketCreator(log, Charset.forName("UTF-8"));
    private AcpCommunication communication;

    //default packet timout, overriden by most operations
    protected int timeout = 1000;
    protected int resendPackets = 2; // standard value for repeated sending of packets

    protected int defaultReceiveBufferSize = 4096; // standard length of receive buffer

    public ACP(String newTarget) {
        setTarget(newTarget);
    }

    public ACP(byte[] newTarget) {
        setTarget(newTarget);
    }

    //
    //  set/get for private variables
    //

    public void setConnectionId(String connectionId) {
        // TODO: input param checking!
        this.connectionId = connectionId;
    }

    public String getTargetMac() {
        return (targetMacAddress);
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
            log.outError(ex.toString() + " [in setTarget]");
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
            log.outWarning("The bind address " + localip
                    + " given with parameter -b could not be resolved to a local IP-Address.\n"
                    + "You must use this parameter with a valid IP-Address that belongs to "
                    + "the PC you run acp_commander on.\n");
        }

        communication = new AcpCommunication(log, localip, timeout);
    }

    public void bind(String localip) {
        // bind socket to a local address (-b)
        // Create a socket address from a hostname (_bind) and a port number. A port number
        // of zero will let the system pick up an ephemeral port in a bind operation.
        if (!localip.equalsIgnoreCase("")) {
            bind(new InetSocketAddress(localip, 0));
        }
    }

    /*public String getconnid() {
        return connectionId.toString();
    }*/

    /*public void setConnectionId(byte[] connectionid) {
        // TODO: input param checking!
        connectionId = bufferToHex(connectionid, 0, 6);
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
    // ACP functionallity
    //

    public AcpReply find() {
        // discover linkstations by sending an ACP-Discover package
        // return on line of formatted string per found LS
        return doDiscover();
    }

    public AcpReply command(String cmd, int maxResend) {
        // send telnet-type command cmd to Linkstation by acpcmd
        enOneCmd();
        authenticate();

        if (maxResend <= 0) {
            maxResend = resendPackets;
        }

        return doTransaction(packetCreator.getAcpCmdPacket(connectionId, targetMacAddress, cmd), maxResend);
    }

    public AcpReply command(String cmd) {
        // send telnet-type command cmd to Linkstation by acpcmd - only send packet once!
        timeout = 60000;
        enOneCmd();
        authenticate();
        return doTransaction(packetCreator.getAcpCmdPacket(connectionId, targetMacAddress, cmd), 1);
    }

    public AcpReply authenticate() {
        byte[] encrypted = encryption.encryptAcpPassword(password, key);
        return authenticate(encrypted);
    }

    public AcpReply authenticate(byte[] encpassword) {
        // authenticate to ACP protokoll
        return doTransaction(packetCreator.getAcpAuthPacket(connectionId, targetMacAddress, encpassword));
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
        int mytimeout = timeout;
        timeout = 60000;
        AcpReply result = doTransaction(packetCreator.getAcpBlinkLedPacket(connectionId, targetMacAddress));
        timeout = mytimeout;
        return result;
    }

    public AcpReply enOneCmd() {
        return enOneCmdEnc(encryption.encryptAcpPassword(apservd, key));
    }

    public AcpReply enOneCmdEnc(byte[] encPassword) {
        return doTransaction(packetCreator.getAcpEnOneCmdPacket(connectionId, targetMacAddress, encPassword));
    }

    public AcpReply setWebUiLanguage(byte language) {
        // interface to switch web GUI language
        // ENOneCmd protected
        // 0 .. Japanese
        // 1 .. English
        // 2 .. German
        // default .. English
        return doTransaction(packetCreator.getAcpWebUiLanguagePacket(connectionId, targetMacAddress, language));
    }

    public AcpReply changeIp(byte[] newip, byte[] newMask, boolean usedhcp) {
        // change IP address
        byte[] encrypted = encryption.encryptAcpPassword(password, key);
        return doTransaction(packetCreator.getAcpChangeIpPacket(connectionId, targetMacAddress, newip, newMask, usedhcp, encrypted));
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
        timeout = 3000; //AH: Change the response timeout

        String state = "[Send/Receive ACPDiscover]";

        byte[] discoverPacket = packetCreator.getAcpDiscoverPacket(connectionId, targetMacAddress);
        byte[] newDiscoverPacket = packetCreator.getAcpNewDiscoverPacket(connectionId, targetMacAddress);

        //String[] packetActionResult = new String[1];
        AcpReply packetActionResult = new AcpReply();
        //ArrayList<String> tempres = new ArrayList<>();
        AcpReply tempReplyResult = new AcpReply();
        DatagramSocket socket;

        DatagramPacket discoverPacketTransmittable = new DatagramPacket(discoverPacket, discoverPacket.length, targetIp, port);
        DatagramPacket newDiscoverPacketTransmittable = new DatagramPacket(newDiscoverPacket, newDiscoverPacket.length, targetIp, port);

        DatagramPacket responsePacketReceivable = new DatagramPacket(new byte[defaultReceiveBufferSize], defaultReceiveBufferSize); //AH: Prepare a packet object into which received data will be placed

        try {
            socket = communication.getSocket(); // TODO bind functionality is missing here

            //AH: Send both packets and expect only one response it seems. Probably gonna get mixed results if you have two devices which respond in different ways
            socket.send(discoverPacketTransmittable);
            socket.send(newDiscoverPacketTransmittable);

            long lastSendTime = System.currentTimeMillis();

            while (System.currentTimeMillis() - lastSendTime < timeout) { //AH: Whilst we still have time to keep sending discovery requests
                socket.receive(responsePacketReceivable); //AH: Receive a packet into the prepared packet object

                packetActionResult = receiveAcp(responsePacketReceivable.getData(), log.debugLevel); // get search results

                // TODO: do optional Discover event with _searchres
                //tempres.add(packetActionResult[1]); // add formatted string to result list
                tempReplyResult.concatenatedOutput = packetActionResult.concatenatedOutput; // add formatted string to result list
            }
        } catch (java.net.SocketTimeoutException stoe) {
            // TimeOut should be OK as we wait until Timeout if we get packets
            log.outDebug(
                    "Timeout reached, stop listening to further Discovery replies",
                    2);
        } catch (java.net.SocketException se) {
            // TODO: better error handling
            cannedMessages.logPortCommunicationFailure(port);
            log.outError("Exception: SocketException (" + se.getMessage() + ") "
                    + state);
        } catch (java.io.IOException ioe) {
            // TODO: better error handling
            log.outError("Exception: IOException (" + ioe.getMessage() + ") "
                    + state);
        }

        // first check for repeated entries and delete them.
        /*for (int i = 0; i < tempres.size() - 1; i++) {
            for (int j = i + 1; j < tempres.size(); j++) {
                // if entry i is equal to entry j
                if ((tempres.get(i)).equals(tempres.get(j))) {
                    // remove j, alternatively clear string and delete in second loop
                    tempres.remove(j);
                    j--;
                }
            }
        }*/

        // move results into string array
        /*String[] result = new String[tempres.size()];
        for (int i = 0; i < tempres.size(); i++) {
            result[i] = tempres.get(i);
        }*/

        //probably not good practice and should be refactored
        /*if (targetIp.toString().split("/", 2)[1].equals("255.255.255.255")) {
            return result;
        }*/

        //probably not good practice and should be refactored
        if (targetIp.toString().split("/", 2)[1].equals("255.255.255.255")) {
            return tempReplyResult;
        }

        return packetActionResult;
    }

    // send ACP packet and handle answer
    private AcpReply doTransaction(byte[] buf) {
        return doTransaction(buf, resendPackets);
    }


    private AcpReply doTransaction(byte[] buf, int repeatSend) {
        String acpcmd = AcpParser.bufferToHex(buf, 9, 1) + AcpParser.bufferToHex(buf, 8, 1);
        String state = "[ACP Send/Receive (Packet:" + acpcmd + " = "
                + AcpParser.getCommandString(buf) + ")]";
        //String[] result;
        AcpReply result;
        int sendcount = 0;
        boolean sendagain = true;
        DatagramSocket socket;
        DatagramPacket packet = new DatagramPacket(buf, buf.length, targetIp, port);

        DatagramPacket receive = new DatagramPacket(new byte[defaultReceiveBufferSize], defaultReceiveBufferSize);

        do {
            sendcount++;
            try {
                log.outDebug("Sending " + sendcount + "/" + repeatSend, 2);

                socket = communication.getSocket();

                socket.send(packet);
                socket.receive(receive);

                sendagain = false; // we received an answer

                // TODO: do optional Receive-event with result
            } catch (java.net.SocketTimeoutException stoe) {
                // TODO: better error handling
                /*result = new String[2];
                if (sendcount >= repeatSend) {
                    result[1] = "Exception: SocketTimeoutException (" + stoe.getMessage() + ") " + state;

                    cannedMessages.logCommunicationTimeout(port);
                    log.outError(result[1]);
                } else {
                    result[1] = "Timeout (" + state + " retry sending ("
                            + sendcount + "/" + repeatSend + ")";
                    log.outDebug(result[1], 1);
                }*/

                result = new AcpReply();

                if (sendcount >= repeatSend) {
                    result.concatenatedOutput = "Exception: SocketTimeoutException (" + stoe.getMessage() + ") " + state;

                    cannedMessages.logCommunicationTimeout(port);
                    log.outError(result.concatenatedOutput);
                } else {
                    result.concatenatedOutput = "Timeout (" + state + " retry sending ("
                            + sendcount + "/" + repeatSend + ")";
                    log.outDebug(result.concatenatedOutput, 1);
                }
            } catch (java.net.SocketException se) {
                // TODO: better error handling
                /*result = new String[2];
                result[1] = "Exception: SocketException (" + se.getMessage() + ") " + state;

                cannedMessages.logPortCommunicationFailure(port);
                log.outError(result[1]);*/

                result = new AcpReply();

                result.concatenatedOutput = "Exception: SocketException (" + se.getMessage() + ") " + state;

                cannedMessages.logPortCommunicationFailure(port);
                log.outError(result.concatenatedOutput);
            } catch (java.io.IOException ioe) {
                // TODO: better error handling
                /*result = new String[2];
                result[1] = "Exception: IOException (" + ioe.getMessage() + ") " + state;
                log.outError(result[1]);*/

                result = new AcpReply();

                result.concatenatedOutput = "Exception: IOException (" + ioe.getMessage() + ") " + state;

                log.outError(result.concatenatedOutput);
            }

        } while ((sendcount < repeatSend) && sendagain); // repeat until max retries reached

        result = receiveAcp(receive.getData(), log.debugLevel); // get search results

        return result;
    }



    //
    // ACP packet creation functionality
    //




    private void receiveAcpHexDump(byte[] buf) {
        // very simple hex | char debug output of received packet for debugging
        try {
            byte onebyte;

            System.out.println("Buffer-Length: " + buf.length);
            for (int j = 0; j < (buf.length / 16); j++) {
                if (j == 0) {
                    System.out.println("ACP-Header:");
                }
                if (j == 2) {
                    System.out.println("ACP-Payload:");
                }

                System.out.print(j * 16 + "::\t");
                for (int i = 0; i <= 15; i++) {
                    System.out.print(AcpParser.bufferToHex(buf, j * 16 + i, 1) + " ");
                }
                System.out.print("\t");

                for (int i = 0; i <= 15; i++) {
                    onebyte = buf[j * 16 + i];
                    if ((onebyte != 0x0A) & (onebyte != 0x09)) {
                        System.out.print((char) onebyte);
                    } else {
                        System.out.print(" ");
                    }
                }
                System.out.println("");
            }
        } catch (java.lang.ArrayIndexOutOfBoundsException arrayE) {
            log.outError(arrayE.toString());
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
    private AcpReply receiveAndDisassembleAcpDiscoveryResponsePacket(byte[] responsePacket) {
        AcpReply disassembledReply = new AcpReply();
        //String[] disassembledPacketInformation = new String[9];

        /*int tmppckttype = 0; //AH: Human readable information about what disassembledPacketInformation type this is?? (assuming a disassembledPacketInformation is always a String[9])
        int out = 1; //AH: Concatenated load of information for all of the parts of the disassembled packet
        int hostname = 2; //AH: Target's Hostname is stored in position 2
        int ip = 3; //AH: Target's IP is stored in position 3
        int mac = 4; //AH: Target's MAC is stored in position 4
        int productstr = 5; //AH: Target's Product ID String
        int productid = 6; //AH: Target's Product ID
        int fwversion = 7; //AH: Target's Firmware Version
        int key = 8; //AH: Target's encryption key??*/

        //AH: Default every one of the disassembledPacketInformation strings to an empty string
        /*for (int i = 0; i < disassembledPacketInformation.length; i++) {
            disassembledPacketInformation[i] = "";
        }*/

        //disassembledPacketInformation[tmppckttype] = "ACPdiscovery reply"; //AH: Human readable information about what disassembledPacketInformation type this is?? (assuming a disassembledPacketInformation is always a String[9])
        disassembledReply.tmppckttype = "ACPdiscovery reply";

        try {
            // get IP
            byte[] targetIp = new byte[4];

            for (int i = 0; i <= 3; i++) {
                targetIp[i] = responsePacket[35 - i];
            }

            InetAddress targetAddr = InetAddress.getByAddress(targetIp);
            //disassembledPacketInformation[ip] = targetAddr.toString();
            disassembledReply.ip = targetAddr.toString();

            // get host name
            int packetPosition = 48; //AH: Start at position 48 and iterate
            while ((responsePacket[packetPosition] != 0x00) & (packetPosition < responsePacket.length)) {
                //disassembledPacketInformation[hostname] = disassembledPacketInformation[hostname] + (char) responsePacket[packetPosition++]; //AH: Append a character from the packet buffer to the disassembledPacketInformation hostname
                disassembledReply.hostname = disassembledReply.hostname + (char) responsePacket[packetPosition++]; //AH: Append a character from the packet buffer to the disassembledPacketInformation hostname
            }

            // Product ID string starts at byte 80
            packetPosition = 80;
            while ((responsePacket[packetPosition] != 0x00) & (packetPosition < responsePacket.length)) {
                //disassembledPacketInformation[productstr] = disassembledPacketInformation[productstr] + (char) responsePacket[packetPosition++];
                disassembledReply.productIdString = disassembledReply.productIdString + (char) responsePacket[packetPosition++];
            }

            // Product ID starts at byte 192 low to high
            for (int i = 3; i >= 0; i--) {
                //disassembledPacketInformation[productid] = disassembledPacketInformation[productid] + responsePacket[192 + i];
                disassembledReply.productId = disassembledReply.productId + responsePacket[192 + i];
            }

            // MAC starts at byte 311
            for (int i = 0; i <= 5; i++) {
                //disassembledPacketInformation[mac] = disassembledPacketInformation[mac] + AcpParser.bufferToHex(responsePacket, i + 311, 1);
                disassembledReply.mac = disassembledReply.mac + AcpParser.bufferToHex(responsePacket, i + 311, 1);

                if (i != 5) {
                    //disassembledPacketInformation[mac] = disassembledPacketInformation[mac] + ":";
                    disassembledReply.mac = disassembledReply.mac + ":";
                }
            }

            // Key - changes with connectionid (everytime) -> key to password encryption?
            for (int i = 0; i <= 3; i++) {
                //disassembledPacketInformation[key] = disassembledPacketInformation[key] + AcpParser.bufferToHex(responsePacket, 47 - i, 1);
                disassembledReply.key = disassembledReply.key + AcpParser.bufferToHex(responsePacket, 47 - i, 1);
            }

            // Firmware version starts at 187
            //disassembledPacketInformation[fwversion] = responsePacket[187] + responsePacket[188] + "." + responsePacket[189] + responsePacket[190];
            disassembledReply.firmwareVersion = responsePacket[187] + responsePacket[188] + "." + responsePacket[189] + responsePacket[190];

            /*disassembledPacketInformation[out] = (disassembledPacketInformation[hostname] + "\t"
                    + disassembledPacketInformation[ip].replace("/", "") + "\t"
                    + String.format("%-" + 20 + "s", disassembledPacketInformation[productstr]) + "\t"
                    + "ID=" + disassembledPacketInformation[productid] + "\t"
                    + "mac: " + disassembledPacketInformation[mac] + "\t"
                    + "FW=  " + disassembledPacketInformation[fwversion] + "\t"
                    //+ "Key=" + disassembledPacketInformation[newkey] + "\t"
            );*/

                disassembledReply.concatenatedOutput = disassembledReply.hostname  + "\t"
                        + disassembledReply.ip.replace("/", "") + "\t"
                        + String.format("%-" + 20 + "s", disassembledReply.productIdString) + "\t"
                        + "ID=" + disassembledReply.productId + "\t"
                        + "mac: " + disassembledReply.mac + "\t"
                        + "FW=  " + disassembledReply.firmwareVersion + "\t";
                        //+ "Key=" + disassembledPacketInformation[newkey] + "\t"

        } catch (java.net.UnknownHostException unkhoste) {
            log.outError(unkhoste.getMessage());
        }
        return disassembledReply;
    }

    /* Analyses incoming ACP Replys - TODO progress, still needs better handling
     *  receiveAcp(byte[] packet, int debug)
     *  INPUT
     *    packet      ... byte [], buffer with received data
     *    debug   ... int, debug state
     *  OUTPUT
     *    result   ... String [] string array with results of packet analysis
     *            0 - "ACP... reply" = packet type
     *            1 - formatted output
     *             2..n - possible details (ACPdiscovery)
     */
    private AcpReply receiveAcp(byte[] packet, int debug) {
        if (debug >= 3) {
            receiveAcpHexDump(packet); //AH: Print out dump of received reply packet if the debug mode is set high enough
        }

        AcpReply result;
        String acpReply;
        int replyType = 0;
        String acpStatus;

        // get type of ACP answer both as long and hexstring
        replyType = (packet[8] & 0xFF) + (packet[9] & 0xFF) * 256; // &0xFF necessary to avoid neg. values
        acpReply = AcpParser.bufferToHex(packet, 9, 1) + AcpParser.bufferToHex(packet, 8, 1);

        //@georg check!
        // value = 0xFFFFFFFF if ERROR occured
        acpStatus = AcpParser. bufferToHex(packet, 31, 1) + AcpParser.bufferToHex(packet, 30, 1) + AcpParser.bufferToHex(packet, 29, 1) + AcpParser.bufferToHex(packet, 28, 1);

        if (acpStatus.equalsIgnoreCase("FFFFFFFF")) {
            log.outDebug(
                "Received packet (" + acpReply + ") has the error-flag set!\n"
                + "For 'authenticate' that is (usually) OK as we do send a buggy packet.",
            1);
        }

        switch (replyType) {
            case 0xc020: // ACP discovery
                log.outDebug("received ACP Discovery reply", 2);
                result = receiveAndDisassembleAcpDiscoveryResponsePacket(packet);
                break;

            case 0xc030: // ACP changeIP
                /*log.outDebug("received ACP change IP reply", 2);
                result = new String[2]; //handling needed ?
                result[0] = "ACP change IP reply";
                result[1] = AcpParser.getErrorMsg(packet);*/

                log.outDebug("received ACP change IP reply", 2);

                result = new AcpReply();
                result.tmppckttype = "ACP change IP reply";
                result.concatenatedOutput = AcpParser.getErrorMsg(packet);

                break;

            case 0xc0a0: // ACP special command
                /*log.outDebug("received ACP special command reply", 2);
                result = new String[2]; //handling needed ?
                result[0] = "ACP special command reply";
                result[1] = AcpParser.getErrorMsg(packet);

                //            result[1] = "OK"; // should be set according to acpStatus!*/

                log.outDebug("received ACP special command reply", 2);

                result = new AcpReply();
                result.tmppckttype = "ACP special command reply";
                result.concatenatedOutput = AcpParser.getErrorMsg(packet);
                break;

            case 0xca10: // acpcmd
                log.outDebug("received acpcmd reply", 2);

                /*result = new String[2];
                result[0] = "acpcmd reply";
                result[1] = "";

                int index = 40;
                while ((packet[index] != 0x00) & (index < packet.length)) {
                    result[1] = result[1] + (char) packet[index++];
                }

                // filter the LSPro default answere "**no message**" as it led to some user queries/worries
                if (result[1].equalsIgnoreCase("**no message**")) {
                    result[1] = "OK (" + AcpParser.getErrorMsg(packet) + ")";
                }*/

                result = new AcpReply();
                result.tmppckttype = "acpcmd reply";
                result.concatenatedOutput = "";

                int index = 40;
                while((packet[index] != 0x00) & (index < packet.length)){
                    result.concatenatedOutput += (char) packet[index++];
                }

                // filter the LSPro default answere "**no message**" as it led to some user queries/worries
                if (result.concatenatedOutput.equalsIgnoreCase("**no message**")) {
                    result.concatenatedOutput = "OK (" + AcpParser.getErrorMsg(packet) + ")";
                }

                break;

            case 0xce00: // ACP discovery
                log.outDebug("received ACP Discovery reply", 1);
                result = receiveAndDisassembleAcpDiscoveryResponsePacket(packet);
                break;

            default:
                /*result = new String[2]; //handling needed ?
                result[0] = "Unknown ACP-Reply packet: 0x" + acpReply;
                result[1] = "Unknown ACP-Reply packet: 0x" + acpReply; // add correct status!*/

                result = new AcpReply();
                result.tmppckttype = "Unknown ACP-Reply packet: 0x" + acpReply;
                result.concatenatedOutput = "Unknown ACP-Reply packet: 0x" + acpReply;

                break;
        }

        //log.outDebug("ACP analysis result: " + result[1], 2);
        log.outDebug("ACP analysis result: " + result.concatenatedOutput, 2);

        return result;
    }
}
