package acpcommander.util;

public class AcpParser {
    public static byte[] hexToByte(String hexstr) {
        String pureHex = hexstr.replaceAll(":", "");
        byte[] bts = new byte[pureHex.length() / 2];
        for (int i = 0; i < bts.length; i++) {
            bts[i] = (byte) Integer.parseInt(pureHex.substring(2 * i, 2 * i + 2), 16);
        }
        return (bts);
    }

    public static String bufferToHex(byte[] buffer, int startOffset, int length) {
        StringBuilder sb = new StringBuilder(length * 2);

        for (int i = startOffset; i < (startOffset + length); i++) {
            sb.append(String.format("%02x", buffer[i]));
        }
        return sb.toString().toUpperCase();
    }

    // retreive errorcode out of receive buffer
    public static int getErrorCode(byte[] buf) {
        return (buf[28] & 0xFF) + ((buf[29] & 0xFF) << 8) + ((buf[30] & 0xFF) << 16) + ((buf[31] & 0xFF) << 24);
    }

    // Translate errorcode to meaningful string
    public static String getErrorMsg(byte[] buf) {
        String acpStatus = bufferToHex(buf, 31, 1) + bufferToHex(buf, 30, 1) + bufferToHex(buf, 29, 1) + bufferToHex(buf, 28, 1);
        int errorCode = getErrorCode(buf);
        String errorString;

        switch (errorCode) {
            // There should be an error state ACP_OK, TODO: Test
            case 0x00000000:
                errorString = "ACP_STATE_OK";
                break;

            case 0x80000000:
                errorString = "ACP_STATE_MALLOC_ERROR";
                break;

            case 0x80000001:
                errorString = "ACP_STATE_PASSWORD_ERROR";
                break;

            case 0x80000002:
                errorString = "ACP_STATE_NO_CHANGE";
                break;

            case 0x80000003:
                errorString = "ACP_STATE_MODE_ERROR";
                break;

            case 0x80000004:
                errorString = "ACP_STATE_CRC_ERROR";
                break;

            case 0x80000005:
                errorString = "ACP_STATE_NOKEY";
                break;

            case 0x80000006:
                errorString = "ACP_STATE_DIFFMODEL";
                break;

            case 0x80000007:
                errorString = "ACP_STATE_NOMODEM";
                break;

            case 0x80000008:
                errorString = "ACP_STATE_COMMAND_ERROR";
                break;

            case 0x80000009:
                errorString = "ACP_STATE_NOT_UPDATE";
                break;

            case 0x8000000A:
                errorString = "ACP_STATE_PERMIT_ERROR";
                break;

            case 0x8000000B:
                errorString = "ACP_STATE_OPEN_ERROR";
                break;

            case 0x8000000C:
                errorString = "ACP_STATE_READ_ERROR";
                break;

            case 0x8000000D:
                errorString = "ACP_STATE_WRITE_ERROR";
                break;

            case 0x8000000E:
                errorString = "ACP_STATE_COMPARE_ERROR";
                break;

            case 0x8000000F:
                errorString = "ACP_STATE_MOUNT_ERROR";
                break;

            case 0x80000010:
                errorString = "ACP_STATE_PID_ERROR";
                break;

            case 0x80000011:
                errorString = "ACP_STATE_FIRM_TYPE_ERROR";
                break;

            case 0x80000012:
                errorString = "ACP_STATE_FORK_ERROR";
                break;

            case 0xFFFFFFFF:
                errorString = "ACP_STATE_FAILURE";
                break;

            // unknown error, better use errorCode and format it to hex
            default:
                errorString = "ACP_STATE_UNKNOWN_ERROR (" + acpStatus + ")";
                break;
        }

        return errorString;
    }

    public static int getCommand(byte[] buf) {
        return ((buf[9] & 0xFF) << 8) + (buf[8] & 0xFF);
    }

    public static byte getSpecialCmd(byte[] buf) {
        return buf[32];
    }

    public static String getCommandString(byte[] buf) {
        int acpcmd = getCommand(buf);
        String cmdstring;

        switch (acpcmd) {
            // ACP_Commands
            // Currently missing, but defined in clientUtil_server:
            //     ACP_FORMAT
            //     ACP_ERASE_USER
            // missing candidates are 0x80C0 and 0x80D0 or 0x8C00 and 0x8D00

            case 0x8020:
            case 0x8E00:
                cmdstring = "ACP_Discover";
                break;

            case 0x8030:
                cmdstring = "ACP_Change_IP";
                break;

            case 0x8040:
                cmdstring = "ACP_Ping";
                break;

            case 0x8050:
                cmdstring = "ACP_Info";
                break;

            case 0x8070:
                cmdstring = "ACP_FIRMUP_End";
                break;

            case 0x8080:
                cmdstring = "ACP_FIRMUP2";
                break;

            case 0x8090:
                cmdstring = "ACP_INFO_HDD";
                break;

            case 0x80A0:
                switch (getSpecialCmd(buf)) {
                    // ACP_Special - details in packetbuf [32]
                    case 0x01:
                        cmdstring = "SPECIAL_CMD_REBOOT";
                        break;

                    case 0x02:
                        cmdstring = "SPECIAL_CMD_SHUTDOWN";
                        break;

                    case 0x03:
                        cmdstring = "SPECIAL_CMD_EMMODE";
                        break;

                    case 0x04:
                        cmdstring = "SPECIAL_CMD_NORMMODE";
                        break;

                    case 0x05:
                        cmdstring = "SPECIAL_CMD_BLINKLED";
                        break;

                    case 0x06:
                        cmdstring = "SPECIAL_CMD_SAVECONFIG";
                        break;

                    case 0x07:
                        cmdstring = "SPECIAL_CMD_LOADCONFIG";
                        break;

                    case 0x08:
                        cmdstring = "SPECIAL_CMD_FACTORYSETUP";
                        break;

                    case 0x09:
                        cmdstring = "SPECIAL_CMD_LIBLOCKSTATE";
                        break;

                    case 0x0a:
                        cmdstring = "SPECIAL_CMD_LIBLOCK";
                        break;

                    case 0x0b:
                        cmdstring = "SPECIAL_CMD_LIBUNLOCK";
                        break;

                    case 0x0c:
                        cmdstring = "SPECIAL_CMD_AUTHENICATE";
                        break;

                    case 0x0d:
                        cmdstring = "SPECIAL_CMD_EN_ONECMD";
                        break;

                    case 0x0e:
                        cmdstring = "SPECIAL_CMD_DEBUGMODE";
                        break;

                    case 0x0f:
                        cmdstring = "SPECIAL_CMD_MAC_EEPROM";
                        break;

                    case 0x12:
                        cmdstring = "SPECIAL_CMD_MUULTILANG";
                        break;

                    default:
                        cmdstring = "Unknown SPECIAL_CMD";
                        break;
                }
                break;

            case 0x80D0:
                cmdstring = "ACP_PART";
                break;

            case 0x80E0:
                cmdstring = "ACP_INFO_RAID";
                break;

            case 0x8A10:
                cmdstring = "ACP_CMD";
                break;

            case 0x8B10:
                cmdstring = "ACP_FILE_SEND";
                break;

            case 0x8B20:
                cmdstring = "ACP_FILESEND_END";
                break;

            // Answers to ACP-Commands
            // Currently missing, but defined in clientUtil_server:
            //     ACP_FORMAT_Reply
            //     ACP_ERASE_USER_Reply
            case 0xC020:
                cmdstring = "ACP_Discover_Reply";
                break;

            case 0xC030:
                cmdstring = "ACP_Change_IP_Reply";
                break;

            case 0xC040:
                cmdstring = "ACP_Ping_Reply";
                break;

            case 0xC050:
                cmdstring = "ACP_Info_Reply";
                break;

            case 0xC070:
                cmdstring = "ACP_FIRMUP_End_Reply";
                break;

            case 0xC080:
                cmdstring = "ACP_FIRMUP2_Reply";
                break;

            case 0xC090:
                cmdstring = "ACP_INFO_HDD_Reply";
                break;

            case 0xC0A0:
                cmdstring = "ACP_Special_Reply";
                break;

            // further handling possible. - necessary?
            case 0xC0D0:
                cmdstring = "ACP_PART_Reply";
                break;

            case 0xC0E0:
                cmdstring = "ACP_INFO_RAID_Reply";
                break;

            case 0xCA10:
                cmdstring = "ACP_CMD_Reply";
                break;

            case 0xCB10:
                cmdstring = "ACP_FILE_SEND_Reply";
                break;

            case 0xCB20:
                cmdstring = "ACP_FILESEND_END_Reply";
                break;

            // Unknown! - Error?
            default:
                cmdstring = "Unknown ACP command - possible error!";
                break;
        }

        return cmdstring;
    }
}
