package acpcommander.util;

import java.nio.charset.Charset;

public class AcpPacketCreator {
    private ScopedLogger log;
    private Charset charset;

    public AcpPacketCreator(ScopedLogger log, Charset charset){
        this.log = log;
        this.charset = charset;
    }

    /**
     * setAcpPacketHeader
     * Helper function. Creates an ACP header in the given packet.
     *
     * @param packet         byte[]        buffer for packet data
     * @param acpCommand      String     HexString (2 byte) with ACPCommand
     * @param connectionId      String     HexString (6 byte) with Connection ID
     * @param targetMac   String  HexString (6 byte) with targets MAC
     * @param payloadSize byte  Length of payload following header
     *                    (for ACPSpecial command this is fixed to 0x28 byte!)
     */
    public void setAcpPacketHeader(byte[] packet, String acpCommand, String connectionId, String targetMac, byte payloadSize) {
        packet[0] = 0x20; // length of header, 32 bytes
        packet[4] = 0x08; // minor packet version
        packet[6] = 0x01; // major packet version
        packet[8] = AcpParser.hexToByte(acpCommand.substring(2, 4))[0]; // lowbyte of ACP command
        packet[9] = AcpParser.hexToByte(acpCommand.substring(0, 2))[0]; // highbyte of ACP command
        packet[10] = payloadSize;

        byte[] test = AcpParser.hexToByte(connectionId);
        System.arraycopy(test, 0, packet, 16, 6);
        System.arraycopy(AcpParser.hexToByte(targetMac), 0, packet, 22, 6);
    }

    // creates an ACPReboot packet, ACP_EN_ONECMD protected
    public byte[] getAcpRebootPacket(String connid, String targetmac) {
        byte[] packet = new byte[72];
        setAcpPacketHeader(packet, "80a0", connid, targetmac, (byte) (0x28));
        packet[32] = 0x01; // type ACPReboot

        return (packet);
    }

    // creates an ACPShutdown packet, ACP_EN_ONECMD protected
    public byte[] getAcpShutdownPacket(String connid, String targetmac) {
        byte[] packet = new byte[72];
        setAcpPacketHeader(packet, "80a0", connid, targetmac, (byte) (0x28));
        packet[32] = 0x02; // type ACPShutdown

        return (packet);
    }

    // creates an ACPemmode packet, ACP_EN_ONECMD protected
    public byte[] getAcpEmModePacket(String connid, String targetmac) {
        byte[] packet = new byte[72];
        setAcpPacketHeader(packet, "80a0", connid, targetmac, (byte) (0x28));
        packet[32] = 0x03; // type ACPemmode

        return (packet);
    }

    // creates an ACPnormmode packet, ACP_EN_ONECMD protected
    public byte[] getAcpNormModePacket(String connid, String targetmac) {
        byte[] packet = new byte[72];
        setAcpPacketHeader(packet, "80a0", connid, targetmac, (byte) (0x28));
        packet[32] = 0x04; // type ACPNormmode

        return (packet);
    }

    // creates an ACPblinkled packet, also plays a series of tones
    public byte[] getAcpBlinkLedPacket(String connid, String targetmac) {
        byte[] packet = new byte[72];
        setAcpPacketHeader(packet, "80a0", connid, targetmac, (byte) (0x28));
        packet[32] = 0x05; // type ACPBlinkled

        return (packet);
    }

    // creates an ACPsaveconfig packet
    /*private byte[] getacpsaveconfig(String connectionId, String targetMacAddress) {
        byte[] buf = new byte[72];
        setAcpPacketHeader(buf, "80a0", connectionId, targetMacAddress, (byte) (0x28));
        buf[32] = 0x06; // type ACPsaveconfig

        return (buf);
    }*/

    // creates an ACPloadconfig packet
    /*private byte[] getacploadconfig(String connectionId, String targetMacAddress) {
        byte[] buf = new byte[72];
        setAcpPacketHeader(buf, "80a0", connectionId, targetMacAddress, (byte) (0x28));
        buf[32] = 0x07; // type ACPloadconfig

        return (buf);
    }*/

    // creates an ACPenonecmd packet with the encrypted password (HexString 8 byte)
    public byte[] getAcpEnOneCmdPacket(String connid, String targetmac, byte[] password) {
        byte[] packet = new byte[72];

        setAcpPacketHeader(packet, "80a0", connid, targetmac, (byte) 0x28);

        packet[32] = 0x0d;

        System.arraycopy(password, 0, packet, 40, 8);

        return packet;
    }

    // creates an ACPDebugmode packet
    // unclear what this causes on the LS
    /*private byte[] getacpdebugmode(String connectionId, String targetMacAddress) {
        byte[] buf = new byte[72];
        setAcpPacketHeader(buf, "80a0", connectionId, targetMacAddress, (byte) (0x28));
        buf[32] = 0x0e; // type ACPDebugmode

        return (buf);
    }*/

    // creates an ACPMultilang packet, ACP_EN_ONECMD protected
    // Used for setting GUI language, then additional parameter for language is needed
    public byte[] getAcpWebUiLanguagePacket(String connid, String targetmac,
                                            byte language) {
        byte[] packet = new byte[72];
        setAcpPacketHeader(packet, "80a0", connid, targetmac, (byte) (0x28));
        packet[32] = 0x12; // type ACPMultilang

        packet[0x24] = language; // seems to be a 4 byte value, starting at 0x24

        return (packet);
    }

    // creates an ACPDiscover packet
    // LS answers with a packet giving firmware details and a key used for pw encryption
    public byte[] getAcpDiscoverPacket(String connid, String targetmac) {
        byte[] packet = new byte[72];
        setAcpPacketHeader(packet, "8020", connid, targetmac, (byte) 0x28);

        return (packet);
    }

    //newer version of discovery packet required by some devs
    public byte[] getAcpNewDiscoverPacket(String connid, String targetmac) {
        byte[] packet = new byte[32];
        setAcpPacketHeader(packet, "8E00", connid, targetmac, (byte) 0x00);

        return (packet);
    }

    // creates an ACPchangeip packet
    public byte[] getAcpChangeIpPacket(String connid, String targetmac, byte[] newip, byte[] newMask, boolean useDhcp, byte[] encPassword) {
        byte[] packet = new byte[144];
        setAcpPacketHeader(packet, "8030", connid, targetmac, (byte) 112);

        System.arraycopy(encPassword, 0, packet, 0x40, encPassword.length);
        // actually 144 byte long, contains password


        if (useDhcp) {
            packet[0x2C] = (byte) 1; // could be: DHCP=true - seems always to be true,
            // expect DHCP and password beyond 0x38
        }
        for (int i = 0; i <= 3; i++) {
            packet[0x33 - i] = newip[i]; // ip starts at 0x30, low byte first
            packet[0x37 - i] = newMask[i]; // mask starts at 0x34, low byte first
        }

        return (packet);
    }

    // create a correct ACPAuth packet
    public byte[] getAcpAuthPacket(String connid, String targetmac,
                                   byte[] password) {
        byte[] packet = new byte[72];
        setAcpPacketHeader(packet, "80a0", connid, targetmac, (byte) 0x28);
        packet[32] = 0x0c;

        System.arraycopy(password, 0, packet, 40, password.length);
        return (packet);
    }


    // creates an ACPCMD packet, used to send telnet-style commands to the LS
    public byte[] getAcpCmdPacket(String connid, String targetmac, String cmd) {
        if (cmd.length() > 210) {
            log.outError("Command line too long (>210 chars).");
        }

        byte[] packet = new byte[cmd.length() + 44];
        setAcpPacketHeader(packet, "8a10", connid, targetmac, (byte) (cmd.length() + 12));
        packet[32] = (byte) (cmd.length());
        packet[36] = 0x03; // type

        System.arraycopy(cmd.getBytes(charset), 0, packet, 40, cmd.length());

        return (packet);
    }
}
