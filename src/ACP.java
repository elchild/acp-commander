package acpcommander;

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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;

public class ACP {
  private InetAddress target;
  protected Integer port = new Integer(22936);
  private String connid; // connection ID, "unique" identifier for the connection
  private String targetmac; // MAC address of the LS, it reacts only if correct MAC or
  // FF:FF:FF:FF:FF:FF is set in the packet
  protected byte[] key = new byte[4]; // Key for password encryption
  // sent in reply to ACP discovery packet
  protected String password;
  private String apservd = "ap_servd";
  private InetSocketAddress bind;
  private Charset defaultCharset = Charset.forName("UTF-8");

  //default packet timout, overriden by most operations
  protected int timeout = 1000;
  protected int resendPackets = 2; // standard value for repeated sending of packets

  public int debuglevel = 0; // Debug level

  protected int rcvBufLen = 4096; // standard length of receive buffer

  public ACP() {
  }

  public ACP(String newtarget) {
    this();
    setTarget(newtarget);
  }

  public ACP(byte[] newtarget) {
    this();
    setTarget(newtarget);
  }

  //
  //  set/get for private variables
  //
  public String getconnid() {
    return connid.toString();
  }

  public void setconnid(String connectionid) {
    // TODO: input param checking!
    connid = connectionid;
  }

  public void setconnid(byte[] connectionid) {
    // TODO: input param checking!
    connid = bufferToHex(connectionid, 0, 6);
  }

  public String gettargetmac() {
    return (targetmac.toString());
  }

  public void settargetmac(String newtargetmac) {
    // TODO: input param checking!
    targetmac = newtargetmac;
  }

  public byte[] getTargetKey() {
    byte[] result = new byte[key.length];
    System.arraycopy(key,0,result,0,key.length);
    return (result);
  }

  public void setTargetKey(byte[] newkey) {
    // TODO: input param checking!
    if (newkey.length != 4) {
      outError("ACPException: Encryption key must be 4 bytes long!");
      return;
    }
    System.arraycopy(newkey,0,key,0,newkey.length);
  }

  public void setTargetKey(String newkey) {
    // TODO: input param checking!
    setTargetKey(hextobyte(newkey));
  }

  public void setPassword(String newpassword) {
    password = newpassword;
  }

  public InetAddress getTarget() {
    return target;
  }

  public void setTarget(String newtarget) {
    try {
      target = InetAddress.getByName(newtarget);
    } catch (UnknownHostException ex) {
      outInfoSetTarget();
      outError(ex.toString() + " [in setTarget]");
    }
  }

  public void setTarget(byte[] newtarget) {
    try {
      target = InetAddress.getByAddress(newtarget);
    } catch (UnknownHostException ex) {
      outInfoSetTarget();
      outError(ex.toString() + " [in setTarget]");
    }
  }

  public void setbroadcastip(String newtarget) {
    try {
      target = InetAddress.getByName(newtarget);
      settargetmac("FF:FF:FF:FF:FF:FF");
    } catch (UnknownHostException ex) {
      outError(ex.toString() + " [in setbroadcastip]");
    }
  }

  public void setbroadcastip(byte[] newtarget) {
    try {
      target = InetAddress.getByAddress(newtarget);
      settargetmac("FF:FF:FF:FF:FF:FF");
    } catch (UnknownHostException ex) {
      outError(ex.toString() + " [in setbroadcastip]");
    }
  }

  public void bind(InetSocketAddress localip) {
    bind = localip;
    if (localip.isUnresolved()) {
      outWarning("The bind address " + localip
          + " given with parameter -b could not be resolved to a local IP-Address.\n"
          + "You must use this parameter with a valid IP-Address that belongs to "
          + "the PC you run acp_commander on.\n");
      bind = null;
    }
  }

  public void bind(String localip) {
    // bind socket to a local address (-b)
    // Create a socket address from a hostname (_bind) and a port number. A port number
    // of zero will let the system pick up an ephemeral port in a bind operation.
    if (!localip.equalsIgnoreCase("")) {
      bind(new InetSocketAddress(localip, 0));
    } else {
      bind = null;
    }
  }

  int getdebuglevel() {
    return debuglevel;
  }

  //
  // ACP functionallity
  //

  public String[] find() {
    // discover linkstations by sending an ACP-Discover package
    // return on line of formatted string per found LS
    return doDiscover();
  }

  public String[] command(String cmd, int maxResend) {
    // send telnet-type command cmd to Linkstation by acpcmd
    enonecmd();
    authent();
    if (maxResend <= 0) {
      maxResend = resendPackets;
    }
    return doSendRcv(getacpcmd(connid, targetmac, cmd), maxResend);
  }

  public String[] command(String cmd) {
    // send telnet-type command cmd to Linkstation by acpcmd - only send packet once!
    timeout = 60000;
    enonecmd();
    authent();
    return doSendRcv(getacpcmd(connid, targetmac, cmd), 1);
  }

  public String[] authent() {
    byte[] encrypted = encryptacppassword(password, key);
    return authent(encrypted);
  }

  public String[] authent(byte[] encpassword) {
    // authenticate to ACP protokoll
    return doSendRcv(getacpauth(connid, targetmac, encpassword));
  }

  public String[] shutdown() {
    // ENOneCmd protected
    return doSendRcv(getacpshutdown(connid, targetmac));
  }

  public String[] reboot() {
    // ENOneCmd protected
    return doSendRcv(getacpreboot(connid, targetmac));
  }

  public String[] emmode() {
    // ENOneCmd protected
    return doSendRcv(getacpemmode(connid, targetmac));
  }

  public String[] normmode() {
    // ENOneCmd protected
    return doSendRcv(getacpnormmode(connid, targetmac));
  }

  public String[] blinkled() {
    int mytimeout = timeout;
    timeout = 60000;
    String[] result = doSendRcv(getacpblinkled(connid, targetmac));
    timeout = mytimeout;
    return result;
  }

  public String[] enonecmd() {
    return enonecmdenc(encryptacppassword(apservd, key));
  }

  public String[] enonecmdenc(byte[] encPassword) {
    return doSendRcv(getacpenonecmd(connid, targetmac, encPassword));
  }

  public String[] saveconfig() {
    // set timeout to 1 min
    int mytimeout = timeout;
    timeout = 60000;
    String[] result = doSendRcv(getacpsaveconfig(connid, targetmac));
    timeout = mytimeout;
    return result;
  }

  public String[] loadconfig() {
    // set timeout to 1 min
    int mytimeout = timeout;
    timeout = 60000;
    String[] result = doSendRcv(getacploadconfig(connid, targetmac));
    timeout = mytimeout;
    return result;
  }

  public String[] debugmode() {
    return doSendRcv(getacpdebugmode(connid, targetmac));
  }

  public String[] multilang(byte language) {
    // interface to switch web GUI language
    // ENOneCmd protected
    // 0 .. Japanese
    // 1 .. English
    // 2 .. German
    // default .. English
    return doSendRcv(getacpmultilang(connid, targetmac, language));
  }

  public String[] changeip(byte[] newip, byte[] newMask, boolean usedhcp) {
    // change IP address
    byte[] encrypted = encryptacppassword(password, key);
    return doSendRcv(getacpchangeip(connid, targetmac, newip, newMask, usedhcp, encrypted));
  }

  //--- End of public routines ---

  //
  // ACP-Interface functions (private)
  //

  private String[] doDiscover() {
    timeout = 3000;
    String state = "[Send/Receive ACPDiscover]";
    byte[] buf = getacpdisc(connid, targetmac);
    byte[] buf2 = getacpdisc2(connid, targetmac);
    String[] searchres = new String[1];
    ArrayList<String> tempres = new ArrayList<>();
    DatagramSocket socket;

    DatagramPacket packet = new DatagramPacket(buf, buf.length, target, port.intValue());
    DatagramPacket receive = new DatagramPacket(new byte[rcvBufLen], rcvBufLen);

    DatagramPacket packet2 = new DatagramPacket(buf2, buf2.length, target, port.intValue());

    try {
      socket = getSocket(); // TODO bind functionality is missing here

      socket.send(packet);
      socket.send(packet2);

      long lastsendtime = System.currentTimeMillis();
      while (System.currentTimeMillis() - lastsendtime < timeout) {
        socket.receive(receive);
        searchres = rcvacp(receive.getData(), debuglevel); // get search results

        // TODO: do optional Discover event with _searchres
        tempres.add(searchres[1]); // add formatted string to result list
      }
    } catch (java.net.SocketTimeoutException stoe) {
      // TimeOut should be OK as we wait until Timeout if we get packets
      outDebug(
          "Timeout reached, stop listening to further Discovery replies",
                2);
    } catch (java.net.SocketException se) {
      // TODO: better error handling
      outInfoSocket();
      outError("Exception: SocketException (" + se.getMessage() + ") "
                + state);
    } catch (java.io.IOException ioe) {
      // TODO: better error handling
      outError("Exception: IOException (" + ioe.getMessage() + ") "
                + state);
    }

    // first check for repeated entries and delete them.
    for (int i = 0; i < tempres.size() - 1; i++) {
      for (int j = i + 1; j < tempres.size(); j++) {
        // if entry i is equal to entry j
        if (((String) tempres.get(i)).equals((String) tempres.get(j))) {
          // remove j, alternatively clear string and delete in second loop
          tempres.remove(j);
          j--;
        }
      }
    }

    // move results into string array
    String[] result = new String[tempres.size()];
    for (int i = 0; i < tempres.size(); i++) {
      result[i] = (String) tempres.get(i);
    }

    //probably not good practice and should be refactored
    if (target.toString().split("/",2)[1].equals("255.255.255.255")) {
      return result;
    }
    return searchres;
  }

  // send ACP packet and handle answer
  private String[] doSendRcv(byte[] buf) {
    return doSendRcv(buf, resendPackets);
  }


  private String[] doSendRcv(byte[] buf, int repeatSend) {
    String acpcmd = bufferToHex(buf, 9, 1) + bufferToHex(buf, 8, 1);
    String state = "[ACP Send/Receive (Packet:" + acpcmd + " = "
            + getcmdstring(buf) + ")]";
    String[] result;
    int sendcount = 0;
    boolean sendagain = true;
    DatagramSocket socket;
    DatagramPacket packet = new DatagramPacket(buf, buf.length, target, port.intValue());

    DatagramPacket receive = new DatagramPacket(new byte[rcvBufLen], rcvBufLen);

    do {
      sendcount++;
      try {
        outDebug("Sending " + sendcount + "/" + repeatSend, 2);

        socket = getSocket();

        socket.send(packet);
        socket.receive(receive);

        sendagain = false; // we received an answer

            // TODO: do optional Receive-event with result
      } catch (java.net.SocketTimeoutException stoe) {
        // TODO: better error handling
        result = new String[2];
        if (sendcount >= repeatSend) {
          result[1] = "Exception: SocketTimeoutException (" + stoe.getMessage() + ") " + state;

          outInfoTimeout();
          outError(result[1]);
        } else {
          result[1] = "Timeout (" + state + " retry sending ("
                + sendcount + "/" + repeatSend + ")";
          outDebug(result[1], 1);
        }
      } catch (java.net.SocketException se) {
        // TODO: better error handling
        result = new String[2];
        result[1] = "Exception: SocketException (" + se.getMessage() + ") " + state;

        outInfoSocket();
        outError(result[1]);
      } catch (java.io.IOException ioe) {
        // TODO: better error handling
        result = new String[2];
        result[1] = "Exception: IOException (" + ioe.getMessage() + ") " + state;
        outError(result[1]);
      }

    } while ((sendcount < repeatSend) && sendagain); // repeat until max retries reached

    result = rcvacp(receive.getData(), debuglevel); // get search results

    return result;
  }

  private DatagramSocket getSocket() throws java.net.SocketException {
    DatagramSocket socket;
    if (bind != null) {
      // bind socket to a local address (-b)
      // Create a socket address from a hostname (_bind) and a port number. A port number
      // of zero will let the system pick up an ephemeral port in a bind operation.
      outDebug("Binding socket to: " + bind.toString() + "\n", 1);

      socket = new DatagramSocket(bind);
    } else {
      socket = new DatagramSocket();
    }

    socket.setSoTimeout(timeout);
    return socket;
  }

  //
  // ACP packet creation functionality
  //

  private int getcommand(byte[] buf) {
    return (int) ((buf[9] & 0xFF) << 8) + (int) (buf[8] & 0xFF);
  }

  private byte getSpecialCmd(byte[] buf) {
    return buf[32];
  }

  private String getcmdstring(byte[] buf) {
    int acpcmd = getcommand(buf);
    String cmdstring = String.valueOf("");

    switch (acpcmd) {
        // ACP_Commands
        // Currently missing, but defined in clientUtil_server:
        //     ACP_FORMAT
        //     ACP_ERASE_USER
        // missing candidates are 0x80C0 and 0x80D0 or 0x8C00 and 0x8D00

      case 0x8020:
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
      case 0x8E00:
        cmdstring = "ACP_Discover";
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
    }
    return cmdstring;
  }

  // retreive errorcode out of receive buffer
  private int geterrorcode(byte[] buf) {
    return (int) (buf[28] & 0xFF) + (int) ((buf[29] & 0xFF) << 8)
          +  (int) ((buf[30] & 0xFF) << 16) + (int) ((buf[31] & 0xFF) << 24);
  }


  // Translate errorcode to meaningful string
  private String getErrorMsg(byte[] buf) {
    String acpStatus = bufferToHex(buf, 31, 1) + bufferToHex(buf, 30, 1)
               + bufferToHex(buf, 29, 1) + bufferToHex(buf, 28, 1);
    int errorcode = geterrorcode(buf);

    String errorstring;
    switch (errorcode) {
      // There should be an error state ACP_OK, TODO: Test
      case 0x00000000:
        errorstring = "ACP_STATE_OK";
        break;
      case 0x80000000:
        errorstring = "ACP_STATE_MALLOC_ERROR";
        break;
      case 0x80000001:
        errorstring = "ACP_STATE_PASSWORD_ERROR";
        break;
      case 0x80000002:
        errorstring = "ACP_STATE_NO_CHANGE";
        break;
      case 0x80000003:
        errorstring = "ACP_STATE_MODE_ERROR";
        break;
      case 0x80000004:
        errorstring = "ACP_STATE_CRC_ERROR";
        break;
      case 0x80000005:
        errorstring = "ACP_STATE_NOKEY";
        break;
      case 0x80000006:
        errorstring = "ACP_STATE_DIFFMODEL";
        break;
      case 0x80000007:
        errorstring = "ACP_STATE_NOMODEM";
        break;
      case 0x80000008:
        errorstring = "ACP_STATE_COMMAND_ERROR";
        break;
      case 0x80000009:
        errorstring = "ACP_STATE_NOT_UPDATE";
        break;
      case 0x8000000A:
        errorstring = "ACP_STATE_PERMIT_ERROR";
        break;
      case 0x8000000B:
        errorstring = "ACP_STATE_OPEN_ERROR";
        break;
      case 0x8000000C:
        errorstring = "ACP_STATE_READ_ERROR";
        break;
      case 0x8000000D:
        errorstring = "ACP_STATE_WRITE_ERROR";
        break;
      case 0x8000000E:
        errorstring = "ACP_STATE_COMPARE_ERROR";
        break;
      case 0x8000000F:
        errorstring = "ACP_STATE_MOUNT_ERROR";
        break;
      case 0x80000010:
        errorstring = "ACP_STATE_PID_ERROR";
        break;
      case 0x80000011:
        errorstring = "ACP_STATE_FIRM_TYPE_ERROR";
        break;
      case 0x80000012:
        errorstring = "ACP_STATE_FORK_ERROR";
        break;
      case 0xFFFFFFFF:
        errorstring = "ACP_STATE_FAILURE";
        break;
      // unknown error, better use errorcode and format it to hex
      default:
        errorstring = "ACP_STATE_UNKNOWN_ERROR (" + acpStatus + ")";
    }
    return errorstring;
  }

  /**
     * setacpheader
     * Helper function. Creates an ACP header in the given buf.
     *
     * @param buf byte[]        buffer for packet data
     * @param acpcmd String     HexString (2 byte) with ACPCommand
     * @param connid String     HexString (6 byte) with Connection ID
     * @param targetmac String  HexString (6 byte) with targets MAC
     * @param payloadsize byte  Length of payload following header
     *              (for ACPSpecial command this is fixed to 0x28 byte!)
     */
  private void setacpheader(byte[] buf, String acpcmd, String connid,
                  String targetmac, byte payloadsize) {
    buf[0] = 0x20; // length of header, 32 bytes
    buf[4] = 0x08; // minor packet version
    buf[6] = 0x01; // major packet version
    buf[8] = hextobyte(acpcmd.substring(2, 4))[0]; // lowbyte of ACP command
    buf[9] = hextobyte(acpcmd.substring(0, 2))[0]; // highbyte of ACP command
    buf[10] = payloadsize;

    byte[] test = hextobyte(connid);
    System.arraycopy(test, 0, buf, 16, 6);
    System.arraycopy(hextobyte(targetmac), 0, buf, 22, 6);
  }

  // creates an ACPReboot packet, ACP_EN_ONECMD protected
  private byte[] getacpreboot(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x01; // type ACPReboot

    return (buf);
  }

  // creates an ACPShutdown packet, ACP_EN_ONECMD protected
  private byte[] getacpshutdown(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x02; // type ACPShutdown

    return (buf);
  }

  // creates an ACPemmode packet, ACP_EN_ONECMD protected
  private byte[] getacpemmode(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x03; // type ACPemmode

    return (buf);
  }

  // creates an ACPnormmode packet, ACP_EN_ONECMD protected
  private byte[] getacpnormmode(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x04; // type ACPNormmode

    return (buf);
  }

  // creates an ACPblinkled packet, also plays a series of tones
  private byte[] getacpblinkled(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x05; // type ACPBlinkled

    return (buf);
  }

  // creates an ACPsaveconfig packet
  private byte[] getacpsaveconfig(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x06; // type ACPsaveconfig

    return (buf);
  }

  // creates an ACPloadconfig packet
  private byte[] getacploadconfig(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x07; // type ACPloadconfig

    return (buf);
  }

  // creates an ACPenonecmd packet with the encrypted password (HexString 8 byte)
  private byte[] getacpenonecmd(String connid, String targetmac,
                  byte[] password) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) 0x28);
    buf[32] = 0x0d;

    System.arraycopy(password, 0, buf, 40, 8);
    return (buf);
  }

  // creates an ACPDebugmode packet
  // unclear what this causes on the LS
  private byte[] getacpdebugmode(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x0e; // type ACPDebugmode

    return (buf);
  }

  // creates an ACPMultilang packet, ACP_EN_ONECMD protected
  // Used for setting GUI language, then additional parameter for language is needed
  private byte[] getacpmultilang(String connid, String targetmac,
                   byte language) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) (0x28));
    buf[32] = 0x12; // type ACPMultilang

    buf[0x24] = language; // seems to be a 4 byte value, starting at 0x24

    return (buf);
  }

  // creates an ACPDiscover packet
  // LS answers with a packet giving firmware details and a key used for pw encryption
  private byte[] getacpdisc(String connid, String targetmac) {
    byte[] buf = new byte[72];
    setacpheader(buf, "8020", connid, targetmac, (byte) 0x28);

    return (buf);
  }

  //newer version of discovery packet required by some devs
  private byte[] getacpdisc2(String connid, String targetmac) {
    byte[] buf = new byte[32];
    setacpheader(buf, "8E00", connid, targetmac, (byte) 0x00);

    return (buf);
  }

  // creates an ACPchangeip packet
  private byte[] getacpchangeip(String connid, String targetmac, byte[] newip,
                  byte[] newMask, boolean usedhcp,
                  byte[] encPassword) {
    byte[] buf = new byte[144];
    setacpheader(buf, "8030", connid, targetmac, (byte) 112);

    System.arraycopy(encPassword, 0, buf, 0x40, encPassword.length);
    // actually 144 byte long, contains password


    if (usedhcp) {
      buf[0x2C] = (byte) 1; // could be: DHCP=true - seems always to be true,
      // expect DHCP and password beyond 0x38
    }
    for (int i = 0; i <= 3; i++) {
      buf[0x33 - i] = newip[i]; // ip starts at 0x30, low byte first
      buf[0x37 - i] = newMask[i]; // mask starts at 0x34, low byte first
    }

    return (buf);
  }

  // create a correct ACPAuth packet
  private byte[] getacpauth(String connid, String targetmac,
                  byte[] password) {
    byte[] buf = new byte[72];
    setacpheader(buf, "80a0", connid, targetmac, (byte) 0x28);
    buf[32] = 0x0c;

    System.arraycopy(password, 0, buf, 40, password.length);
    return (buf);
  }


  // creates an ACPCMD packet, used to send telnet-style commands to the LS
  private byte[] getacpcmd(String connid, String targetmac, String cmd) {
    if (cmd.length() > 210) {
      outError("Command line too long (>210 chars).");
    }

    byte[] buf = new byte[cmd.length() + 44];
    setacpheader(buf, "8a10", connid, targetmac, (byte) (cmd.length() + 12));
    buf[32] = (byte) (cmd.length());
    buf[36] = 0x03; // type

    System.arraycopy(cmd.getBytes(defaultCharset), 0, buf, 40, cmd.length());

    return (buf);
  }

  public byte[] encryptacppassword(String password, byte[] newkey) {
    if (password.length() > 24) {
      outError("The acp_commander only allows password lengths up to 24 chars");
    }
    if (password.length() == 0) {
      return new byte[8];
    }

    byte[] subpasswd = new byte[8];
    int sublength = 0;
    byte[] result = new byte[(password.length() + 7 >> 3) * 8];

    for (int i = 0; i < (password.length() + 7) >> 3; i++) {
      sublength = password.length() - i * 8;
      if (sublength > 8) {
        sublength = 8;
      }

      System.arraycopy(password.substring(i * 8).getBytes(defaultCharset), 0,
                 subpasswd, 0, sublength);
      if (sublength < 8) {
        subpasswd[sublength] = (byte) 0x00; // end of string must be 0x00
      }

      System.arraycopy(encacppassword(subpasswd, newkey), 0, result, i * 8, 8);
    }

    return result;
  }

  private byte[] encacppassword(byte[] password, byte[] outkey) {
    //
    // mimmicks route from LSUpdater.exe, starting at 0x00401700
    // key is a 4 byte array (changed order, key 6ae2ad78 => (0x6a, 0xe2, 0xad, 0x78)
    // password = ap_servd, key= 6ae2ad78 gives encrypted 19:A4:F7:9B:AF:7B:C4:DD
    //
    byte[] newkey = new byte[8];
    byte[] result = new byte[8];

    // first generate initial encryption key (newkey) from key
    for (int i = 0; i < 4; i++) {
      newkey[3 - i] = (byte) (outkey[i]); // lower 4 bytes
      newkey[4 + i] = (byte) ((outkey[i] ^ outkey[3 - i]) * outkey[3 - i]); // higher 4 bytes
    }
    // use newkey to generate scrambled (xor) password, newkey is regularly altered
    //int j = 0;
    for (int i = 0; i < 4; i++) {
      // encryption of first char, first alter newkey
      newkey[0] = (byte) (password[(i * 2)] ^ newkey[0]);

      for (int k = 1; k < (i + 1); k++) { // only executed if i > 1
        newkey[k * 2] = (byte) (newkey[k * 2] ^ newkey[(k * 2) - 2]);
      }

      result[i] = newkey[(i * 2)];

      // above is repeated (more or less) for 2nd char, first alter newkey
      newkey[1] = (byte) (password[(i * 2) + 1] ^ newkey[1]);

      for (int k = 1; k < (i + 1); k++) { // only executed if i > 1
        newkey[(k * 2) + 1] = (byte) (newkey[(k * 2) + 1] ^ newkey[(k * 2) - 1]);
      }

      result[7 - i] = newkey[(i * 2) + 1];
    }

    return (result);
  }


  private void rcvacpHexDump(byte[] buf) {
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
          System.out.print(bufferToHex(buf, j * 16 + i, 1) + " ");
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
      outError(arrayE.toString());
    }
  }

  /* Analyse ACPDisc answer packet, get hostname, hostIP, DHCP-state, FW-version
     * outACPrcvDisc(byte[] buf, int debug)
     *  INPUT
     *    buf      ... byte [], buffer with received data
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
  private String[] rcvacpDisc(byte[] buf, int debug) {
    String[] result = new String[9];
    int tmppckttype = 0;
    int out = 1;
    int hostname = 2;
    int ip = 3;
    int mac = 4;
    int productstr = 5;
    int productid = 6;
    int fwversion = 7;
    int key = 8;

    for (int i = 0; i < result.length; i++) {
      result[i] = "";
    }

    result[tmppckttype] = "ACPdiscovery reply";
    try {
      // get IP
      byte[] targetip = new byte[4];
      for (int i = 0; i <= 3; i++) {
        targetip[i] = buf[35 - i];
      }
      InetAddress targetAddr = InetAddress.getByAddress(targetip);
      result[ip] = targetAddr.toString();

      // get host name
      int index = 48;
      while ((buf[index] != 0x00) & (index < buf.length)) {
        result[hostname] = result[hostname] + (char) buf[index++];
      }

      // Product ID string starts at byte 80
      index = 80;
      while ((buf[index] != 0x00) & (index < buf.length)) {
        result[productstr] = result[productstr] + (char) buf[index++];
      }

      // Product ID starts at byte 192 low to high
      for (int i = 3; i >= 0; i--) {
        result[productid] = result[productid] + buf[192 + i];
      }

      // MAC starts at byte 311
      for (int i = 0; i <= 5; i++) {
        result[mac] = result[mac] + bufferToHex(buf, i + 311, 1);
        if (i != 5) {
          result[mac] = result[mac] + ":";
        }
      }

      // Key - changes with connectionid (everytime) -> key to password encryption?
      for (int i = 0; i <= 3; i++) {
        result[key] = result[key] + bufferToHex(buf, 47 - i, 1);
      }

      // Firmware version starts at 187
      result[fwversion] = buf[187] + buf[188] + "." + buf[189] + buf[190];

      result[out] = (result[hostname] + "\t"
                + result[ip].replace("/","") + "\t"
                + String.format("%-" + 20 + "s", result[productstr]) + "\t"
                + "ID=" + result[productid] + "\t"
                + "mac: " + result[mac] + "\t"
                + "FW=  " + result[fwversion] + "\t"
               //+ "Key=" + result[newkey] + "\t"
               );
    } catch (java.net.UnknownHostException unkhoste) {
      outError(unkhoste.getMessage());
    }
    return (result);
  }

  /* Analyses incoming ACP Replys - TODO progress, still needs better handling
     *  rcvacp(byte[] buf, int debug)
     *  INPUT
     *    buf      ... byte [], buffer with received data
     *    debug   ... int, debug state
     *  OUTPUT
     *    result   ... String [] string array with results of packet analysis
     *            0 - "ACP... reply" = packet type
     *            1 - formatted output
     *             2..n - possible details (ACPdiscovery)
     */
  private String[] rcvacp(byte[] buf, int debug) {
    if (debug >= 3) {
      rcvacpHexDump(buf);
    }

    String[] result;
    String acpReply;
    int acptype = 0;
    String acpStatus;

    // get type of ACP answer both as long and hexstring
    acptype = (buf[8] & 0xFF) + (buf[9] & 0xFF) * 256; // &0xFF necessary to avoid neg. values
    acpReply = bufferToHex(buf, 9, 1) + bufferToHex(buf, 8, 1);

    //@georg check!
    // value = 0xFFFFFFFF if ERROR occured
    acpStatus = bufferToHex(buf, 31, 1) + bufferToHex(buf, 30, 1)
          + bufferToHex(buf, 29, 1) + bufferToHex(buf, 28, 1);
    if (acpStatus.equalsIgnoreCase("FFFFFFFF")) {
      outDebug("Received packet (" + acpReply + ") has the error-flag set!\n"
          + "For 'authenticate' that is (usually) OK as we do send a buggy packet.", 1);
    }

    switch (acptype) {
      case 0xc020: // ACP discovery
        outDebug("received ACP Discovery reply", 1);
        result = rcvacpDisc(buf, debug);
        break;
      case 0xc030: // ACP changeIP
        outDebug("received ACP change IP reply", 1);
        result = new String[2]; //handling needed ?
        result[0] = "ACP change IP reply";
        result[1] = getErrorMsg(buf);
        break;
      case 0xc0a0: // ACP special command
        outDebug("received ACP special command reply", 1);
        result = new String[2]; //handling needed ?
        result[0] = "ACP special command reply";
        result[1] = getErrorMsg(buf);

        //            result[1] = "OK"; // should be set according to acpStatus!
        break;
      case 0xca10: // acpcmd
        outDebug("received acpcmd reply", 1);

        result = new String[2];
        result[0] = "acpcmd reply";
        result[1] = "";
        int index = 40;
        while ((buf[index] != 0x00) & (index < buf.length)) {
          result[1] = result[1] + (char) buf[index++];
        }

        // filter the LSPro default answere "**no message**" as it led to some user queries/worries
        if (result[1].equalsIgnoreCase("**no message**")) {
          result[1] = "OK (" + getErrorMsg(buf) + ")";
        }
        break;
      case 0xce00: // ACP discovery
        outDebug("received ACP Discovery reply", 1);
        result = rcvacpDisc(buf, debug);
        break;
      default:
        result = new String[2]; //handling needed ?
        result[0] = "Unknown ACP-Reply packet: 0x" + acpReply;
        result[1] = "Unknown ACP-Reply packet: 0x" + acpReply; // add correct status!
    }
    outDebug("ACP analysis result: " + result[1], 2);
    return (result);
  }

  //
  // Standard warning, explanation functions
  //

  private void outInfoTimeout() {
    System.out.println(
            "A SocketTimeoutException usually indicates bad firewall settings.\n"
            + "Check especially for *UDP* port " + port.toString()
            + " and make sure that the connection to your LS is working.");
    if (port.intValue() != 22936) {
      outWarning("The Timeout could also be caused as you specified "
                + "(parameter -p) to use port " + port.toString()
                + " which differs from standard port 22936.");
    }
  }

  private void outInfoSocket() {
    System.out.println(
            "A SocketException often indicates bad firewall settings.\n"
            + "The acp_commander / your java enviroment needs to send/recevie on UDP port "
            + port.toString() + ".");
  }

  private void outInfoSetTarget() {
    System.out.println(
        "A UnknownHostException usually indicates that the specified target is not known "
        + "to your PC (can not be resolved).\n"
        + "Possible reasons are typos in the target parameter \"-t\", connection or "
        + "name resolution problems.\n"
        + "Also make sure that the target - here your Linkstation / Terastation - is powered on.");
  }

  //
  // Helper functions, should be moved to own classes
  //

  private void outDebug(String message, int debuglevel) {
    // negative debuglevels are considered as errors!
    if (debuglevel < 0) {
      outError(message);
      return;
    }

    if (debuglevel <= getdebuglevel()) {
      System.out.println(message);
    }
  }

  private void outError(String message) {
    System.err.println("ERROR: " + message);
    System.exit( -1);
  }

  private void outWarning(String message) {
    System.out.println("WARNING: " + message);
  }

  private byte[] hextobyte(String hexstr) {
    String pureHex = hexstr.replaceAll(":", "");
    byte[] bts = new byte[pureHex.length() / 2];
    for (int i = 0; i < bts.length; i++) {
      bts[i] = (byte) Integer.parseInt(pureHex.substring(2 * i, 2 * i + 2),16);
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
}
