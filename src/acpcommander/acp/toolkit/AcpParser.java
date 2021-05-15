package acpcommander.acp.toolkit;

public class AcpParser {
    public static byte[] hexToByte(String hex) {
        String pureHex = hex.replaceAll(":", ""); //AH: Remove colons from hex structures, such as MAC addresses

        byte[] bts = new byte[pureHex.length() / 2];

        for (int i = 0; i < bts.length; i++) {
            bts[i] = (byte) Integer.parseInt(pureHex.substring(2 * i, 2 * i + 2), 16);
        }

        return bts;
    }

    public static String takeHexFromPacket(byte[] packet, int startOffset, int length) {
        StringBuilder hexString = new StringBuilder(length * 2);

        for (int i = startOffset; i < (startOffset + length); i++) {
            hexString.append(String.format("%02x", packet[i]));
        }

        return hexString.toString().toUpperCase();
    }

    // retreive errorcode out of receive buffer
    public static int getErrorCodeFromPacket(byte[] packet) {
        return (packet[28] & 0xFF) + ((packet[29] & 0xFF) << 8) + ((packet[30] & 0xFF) << 16) + ((packet[31] & 0xFF) << 24);
    }

    // Translate errorcode to meaningful string
    public static String getErrorMessageFromPacket(byte[] packet) {
        int errorCode = getErrorCodeFromPacket(packet);

        switch (errorCode) {
            // There should be an error state ACP_OK, TODO: Test
            case 0x00000000:
                return "ACP_STATE_OK";

            case 0x80000000:
                return "ACP_STATE_MALLOC_ERROR";

            case 0x80000001:
                return "ACP_STATE_PASSWORD_ERROR";

            case 0x80000002:
                return "ACP_STATE_NO_CHANGE";

            case 0x80000003:
                return "ACP_STATE_MODE_ERROR";

            case 0x80000004:
                return "ACP_STATE_CRC_ERROR";

            case 0x80000005:
                return "ACP_STATE_NOKEY";

            case 0x80000006:
                return "ACP_STATE_DIFFMODEL";

            case 0x80000007:
                return "ACP_STATE_NOMODEM";

            case 0x80000008:
                return "ACP_STATE_COMMAND_ERROR";

            case 0x80000009:
                return "ACP_STATE_NOT_UPDATE";

            case 0x8000000A:
                return "ACP_STATE_PERMIT_ERROR";

            case 0x8000000B:
                return "ACP_STATE_OPEN_ERROR";

            case 0x8000000C:
                return "ACP_STATE_READ_ERROR";

            case 0x8000000D:
                return "ACP_STATE_WRITE_ERROR";

            case 0x8000000E:
                return "ACP_STATE_COMPARE_ERROR";

            case 0x8000000F:
                return "ACP_STATE_MOUNT_ERROR";

            case 0x80000010:
                return "ACP_STATE_PID_ERROR";

            case 0x80000011:
                return "ACP_STATE_FIRM_TYPE_ERROR";

            case 0x80000012:
                return "ACP_STATE_FORK_ERROR";

            case 0xFFFFFFFF:
                return "ACP_STATE_FAILURE";

            // unknown error, better use errorCode and format it to hex
            default:
                return "ACP_STATE_UNKNOWN_ERROR (" + (takeHexFromPacket(packet, 31, 1) + takeHexFromPacket(packet, 30, 1) + takeHexFromPacket(packet, 29, 1) + takeHexFromPacket(packet, 28, 1)) + ")";
        }
    }

    public static int getCommandFromPacket(byte[] packet) {
        return ((packet[9] & 0xFF) << 8) + (packet[8] & 0xFF);
    }

    public static byte getSpecialCommandFromPacket(byte[] packet) {
        return packet[32];
    }

    public static String getCommandStringFromPacket(byte[] packet) {
        int acpcmd = getCommandFromPacket(packet);

        switch (acpcmd) {
            // ACP_Commands
            // Currently missing, but defined in clientUtil_server:
            //     ACP_FORMAT
            //     ACP_ERASE_USER
            // missing candidates are 0x80C0 and 0x80D0 or 0x8C00 and 0x8D00

            case 0x8020:
            case 0x8E00:
                return "ACP_Discover";

            case 0x8030:
                return "ACP_Change_IP";

            case 0x8040:
                return "ACP_Ping";

            case 0x8050:
                return "ACP_Info";

            case 0x8070:
                return "ACP_FIRMUP_End";

            case 0x8080:
                return "ACP_FIRMUP2";

            case 0x8090:
                return "ACP_INFO_HDD";

            case 0x80A0:
                switch (getSpecialCommandFromPacket(packet)) {
                    // ACP_Special - details in packetbuf [32]
                    case 0x01:
                        return "SPECIAL_CMD_REBOOT";

                    case 0x02:
                        return "SPECIAL_CMD_SHUTDOWN";

                    case 0x03:
                        return "SPECIAL_CMD_EMMODE";

                    case 0x04:
                        return "SPECIAL_CMD_NORMMODE";

                    case 0x05:
                        return "SPECIAL_CMD_BLINKLED";

                    case 0x06:
                        return "SPECIAL_CMD_SAVECONFIG";

                    case 0x07:
                        return "SPECIAL_CMD_LOADCONFIG";

                    case 0x08:
                        return "SPECIAL_CMD_FACTORYSETUP";

                    case 0x09:
                        return "SPECIAL_CMD_LIBLOCKSTATE";

                    case 0x0a:
                        return "SPECIAL_CMD_LIBLOCK";

                    case 0x0b:
                        return "SPECIAL_CMD_LIBUNLOCK";

                    case 0x0c:
                        return "SPECIAL_CMD_AUTHENICATE";

                    case 0x0d:
                        return "SPECIAL_CMD_EN_ONECMD";

                    case 0x0e:
                        return "SPECIAL_CMD_DEBUGMODE";

                    case 0x0f:
                        return "SPECIAL_CMD_MAC_EEPROM";

                    case 0x12:
                        return "SPECIAL_CMD_MUULTILANG";

                    default:
                        return "Unknown SPECIAL_CMD";
                }

            case 0x80D0:
                return "ACP_PART";

            case 0x80E0:
                return "ACP_INFO_RAID";

            case 0x8A10:
                return "ACP_CMD";

            case 0x8B10:
                return "ACP_FILE_SEND";

            case 0x8B20:
                return "ACP_FILESEND_END";

            // Answers to ACP-Commands
            // Currently missing, but defined in clientUtil_server:
            //     ACP_FORMAT_Reply
            //     ACP_ERASE_USER_Reply
            case 0xC020:
                return "ACP_Discover_Reply";

            case 0xC030:
                return "ACP_Change_IP_Reply";

            case 0xC040:
                return "ACP_Ping_Reply";

            case 0xC050:
                return "ACP_Info_Reply";

            case 0xC070:
                return "ACP_FIRMUP_End_Reply";

            case 0xC080:
                return "ACP_FIRMUP2_Reply";

            case 0xC090:
                return "ACP_INFO_HDD_Reply";

            case 0xC0A0:
                return "ACP_Special_Reply";

            // further handling possible. - necessary?
            case 0xC0D0:
                return "ACP_PART_Reply";

            case 0xC0E0:
                return "ACP_INFO_RAID_Reply";

            case 0xCA10:
                return "ACP_CMD_Reply";

            case 0xCB10:
                return "ACP_FILE_SEND_Reply";

            case 0xCB20:
                return "ACP_FILESEND_END_Reply";

            // Unknown! - Error?
            default:
                return "Unknown ACP command - possible error!";
        }
    }
}
