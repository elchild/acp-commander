package acpcommander;

/**
 * <p>Beschreibung: Used to sent ACP-commands to Buffalo(R) devices.
 *  Out of the work of nas-central.org (linkstationwiki.net)</p>
 *
 * <p>Copyright: Copyright (c) 2006, GPL</p>
 *
 * <p>Organisation: nas-central.org (linkstationwiki.net)</p>
 *
 * @author Georg
 * @version 0.4.1 (beta)
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;

import java.net.InetAddress;
import java.net.URL;

import java.util.Random;

import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;


public class acp_commander {
  private static String _version = "0.5";
  private static int _stdport = 22936;
  private static int _timeout = 5000; // set socket timeout to 5000 ms, some user report timeout problems

  private static int _debug = 0; // determins degree of additional output, increasing with value
  private static String _state; // where are we in the code, mostly for exceptions output :-(

  private static void outTitle() {
    System.out.println(
      "ACP_commander based on the nas-central.org (linkstationwiki.net) project.\n");
  }

  private static void outUsage() {
    System.out.println("Usage:  acp_commander [options] -t target\n\n" +
                           "options are:\n" +
                           "   -t target .. IP or network name of the Device\n" +
                           "   -m MAC   ... define targets mac address set in the ACP package *),\n" +
                           "                default = FF:FF:FF:FF:FF:FF.\n" +
                           "   -na      ... no authorisation, skip the ACP_AUTH packet. You should\n" +
                           "                only use this option together with -i.\n" +
                           "   -pw passwd . your admin password. A default password will be tried if ommited.\n" +
                           "                if authentication fails you will be prompted for your password\n" +
                           "   -i ID    ... define a connection identifier, if not given, a random one will\n" +
                           "                be used. (With param MAC the senders MAC address will be used.)\n" +
                           "                Successfull authenitfications are stored in conjunction with a \n" +
                           "                given connection ID. So you may reuse a previously used one.\n" +
                           "                Using a lot of different id's in a chain of commands might\n" +
                           "                cause a lot of overhead at the device.\n" +
                           "   -p port  ... define alternative target port, default = " +
                           _stdport + "\n" +
                           "   -b localIP.. bind socket to local address. Use if acp_commander\n" +
                           "                can not find your device (might use wrong adapter).\n" +
                           "\n" +
                           "   -f       ... find device(s) by sending an ACP_DISCOVER package\n" +
                           "   -o       ... open the device by sending 'telnetd' and 'passwd -d root',\n" +
                           "                thus enabling telnet and clearing the root password\n" +
                           "   -c cmd   ... sends the given shell command cmd to the device.\n" +
                           "   -s       ... rudimentry interactive shell\n" +
                           "   -cb      ... clear \\boot, get rid of ACP_STATE_ERROR after firmware update\n" +
                           "                output of df follows for control\n" +
                           "   -ip newIP... change IP to newIP (basic support).\n" +
                           "   -blink   ... blink LED's and play some tones\n" +
                           "\n" +
                           "   -gui nr  ... set Web GUI language 0=Jap, 1=Eng, 2=Ger.\n" +
                           "   -diag    ... run some diagnostics on device settings (lang, backup).\n" +
                           "   -emmode  ... Device reboots next into EM-mode.\n" +
                           "   -normmode .. Device reboots next into normal mode.\n" +
                           "   -reboot  ... reboot device.\n" +
                           "   -shutdown .. shutdown device.\n" +
                           "\n" +
                           "   -d1...-d3 .. set debug level, generate additional output\n" +
                           "                debug level >= 3: HEX/ASCII dump of incoming packets\n" +
                           "   -q       ... quiet, surpress header, does not work with -h or -v\n" +
                           "   -h | -v  ... extended help (this output)\n" +
                           "   -u       ... (shorter) usage \n" +
                           "\n" +
                           "*)  this is not the MAC address the packet is sent to, but the address within\n" +
                           "    the ACP packet. The device will only react to ACP packets if they\n" +
                           "    carry the correct (its) MAC-address or FF:FF:FF:FF:FF:FF\n" +
                           "\n" +
                           "This program is based on the work done at nas-central.org (linkstationwiki.net),\n" +
                           "which is not related with Buffalo(R) in any way.\n"
                            + "report issues/enhancement requests at https://github.com/1000001101000/acp-commander");
  }


  // help(), long version with explanations
  private static void help() {
    outTitle();

    System.out.println("Version " + _version + "\n");

    outUsage();
  }

  private static void usage() {
    help();
  }

  private static String getParamValue(String name, String[] args) {
   // not looking at the last argument, as it would have no following parameter
    for (int i = 0; i < args.length - 1; ++i) {
      if (args[i].equals(name)) {
        return args[i + 1];
      }
    }
    return null;
  }

    // private static String getParamValue(String name, String[] args, String defvalue)
    // retreive the value passed to parameter "name" within the arguments "args",
    // returns "defvalue" if argument "name" could not be found.
  private static String getParamValue(String name, String[] args, String defvalue) {
    // not looking at the last argument, as it would have no following parameter
    for (int i = 0; i < args.length - 1; ++i) {
      if (args[i].equals(name)) {
        return args[i + 1];
      }
    }
    return defvalue;
  }

    // private static boolean hasParam(String name, String[] args)
    // checks wether parameter "name" is specified in "args"
    private static boolean hasParam(String name, String[] args) {
        for (int i = 0; i < args.length; ++i) {
            if (args[i].equals(name)) {
                return true;
            }
        }
        return false;
    }

    // private static boolean hasParam(String[] names, String[] args) {
    // checks wether one of the parameters in "names" is specified in "args"
    private static boolean hasParam(String[] names, String[] args) {
        for (int i = 0; i < args.length; ++i) {
            for (int j = 0; j < names.length; ++j) {
                if (args[i].equals(names[j])) {
                    return true;
                }
            }
        }
        return false;
    }

    // private static void outDebug(String message, int debuglevel)
    // if parameter "debuglevel" <= _debug the debug message is written to System.out
    private static void outDebug(String message, int debuglevel) {
        // negative debuglevels are considered as errors!
        if (debuglevel < 0) {
            outError(message);
            return;
        }

        if (debuglevel <= _debug) {
            System.out.println(message);
        }
    }

    // private static void outError(String message)
    // writes an Errormessage to System.err and exits program, called by outDebug for
    // negative debuglevels
    private static void outError(String message) {
        System.err.println("ERROR: " + message);
        System.exit( -1);
    }

    // private static void outWarning(String message)
    // writes the warning to System.out
    private static void outWarning(String message) {
        System.out.println("WARNING: " + message);
    }

    private static Preferences myPreferences() {
        Preferences myprefs = Preferences.userRoot().node("acp_commander");
        if (myprefs.get("acp-commander.version", "-").equals("-")) {
            // there seems to be no existing prefs, write default values
            System.out.println(
                    "there seems to be no existing prefs, write default values");
            myprefs = defaultPreferences(myprefs);
        }

        return myprefs;
    }

    private static Preferences defaultPreferences(Preferences myprefs) {
        myprefs.put("acp-commander.version", _version);
        myprefs.putBoolean("acp-commander.experimental", false);
        myprefs.putInt("debug.level", 0);
        //Scripts
        myprefs.put("scripts.url",
                    "http://downloads.nas-central.org/Users/Georg/");
        myprefs.put("scripts.install", "installer.sh");
        myprefs.put("scripts.normmode", "normmode.sh");
        // Target
        myprefs.putInt("target.port", _stdport);
        myprefs.put("target.ip", "");
        myprefs.put("target.mac", "");
        myprefs.put("target.connid", "");
        // Local
        myprefs.put("local.bind", "");
        myprefs.putInt("local.timeout", _timeout);

        return myprefs;
    }

    private static byte[] HexToByte(String hexstr) {
        String pureHex = hexstr.replaceAll(":", "");
        byte[] bts = new byte[pureHex.length() / 2];
        for (int i = 0; i < bts.length; i++) {
            bts[i] = (byte) Integer.parseInt(pureHex.substring(2 * i, 2 * i + 2), 16);
        }
        return (bts);
    }

    private static String bufferToHex(byte buffer[], int startOffset, int length) {
        StringBuffer hexString = new StringBuffer(2 * length);
        int endOffset = startOffset + length;

        for (int i = startOffset; i < endOffset; i++) {
            appendHexPair(buffer[i], hexString);
        }
        return hexString.toString();
    }

    private static void appendHexPair(byte b, StringBuffer hexString) {
        char highNibble = kHexChars[(b & 0xF0) >> 4];
        char lowNibble = kHexChars[b & 0x0F];

        hexString.append(highNibble);
        hexString.append(lowNibble);
    }

    private static final char kHexChars[] = {'0', '1', '2', '3', '4', '5', '6',
                                            '7', '8', '9', 'A', 'B', 'C', 'D',
                                            'E', 'F'};


    public static void main(String[] args) {
        // preferences, load preferences file
        Preferences myprefs = myPreferences();

        _debug = myprefs.getInt("debug.level", _debug);
        _timeout = myprefs.getInt("local.timeout", _timeout);

        // variables
        String _mac = myprefs.get("target.mac", new String(""));
        String _connID = myprefs.get("target.connid", new String(""));
        String _target = myprefs.get("target.ip", new String(""));
        Integer _port = new Integer(myprefs.getInt("target.port", _stdport));
        String _bind = myprefs.get("local.bind", new String("")); // local address used for binding socket

        String _cmd = new String("");
        String _newip = new String(""); // new ip address
        String _password = new String(""); // admin password
        Integer _setgui = new Integer(1); // set gui to language 0=jap, 1=eng, 2=ger

        // flags what to do, set during parsing the command line arguments
        boolean _openbox = false;
        boolean _authent = false;
        boolean _shell = false;
        boolean _clearboot = false;
        boolean _emmode = false; // next reboot into EM-Mode
        boolean _normmode = false; // next reboot into rootFS-Mode
        boolean _reboot = false; // reboot device
        boolean _shutdown = false; // shut device down
        boolean _findLS = false; // discover/find (search) devices
        boolean _blink = false; // blink LED's and play some tones
        boolean _changeip = false; // change ip
        boolean _gui = false; // set web gui language 0=jap, 1=eng, 2=ger
        boolean _diag = false; // run diagnostics
        boolean _test = false; // for testing purposes

        //
        // Parsing the command line parameters.
        //
        _state = "CmdLnParse";

        // catch various standard options for help. Only -h and -v are official, though
        if ((args.length == 0) |
            (hasParam(new String[] {"-u", "-usage", "--usage", "/u",
                      "-h", "--h", "-v", "--v", "-?", "--?", "/h", "/?",
                      "-help", "--help",
                      "-version", "--version"}, args))) {
            // if none or usage parameter is given only output of shorter usage
            // otherwise longer help with explanations is presented
            if ((args.length == 0) |
                (hasParam(new String[] {"-u", "-usage", "--usage", "/u"}, args))) {
                usage();
                return;
            } else {
                help();
                return;
            } // if usage | help
        } else {
            if (!hasParam("-q", args)) {
                outTitle();
            }
        } // if noparam, usage, help, version ...

        if (hasParam(new String[] {"-d1", "-d2", "-d3"}, args)) {
            if (hasParam("-d1", args)) {
                _debug = 1;
            }
            if (hasParam("-d2", args)) {
                _debug = 2;
            }
            if (hasParam("-d3", args)) {
                _debug = 3;
            }
            System.out.println("Debug level set to " + _debug);
        }

        if (hasParam("-test", args)) {
            _authent = true;
            _test = true;
        }

        if (hasParam("-t", args)) {
            outDebug("Target parameter -t found", 2);
            _target = getParamValue("-t", args, "");
        } else {
            if (hasParam("-f", args)) {
                _target = "255.255.255.255"; // if no target is specified for find, use broadcast
            } else {
                if (_target.equals("")) {
                    outError(
                            "You didn't specify a target! Parameter '-t target' is missing");
                    return;
                }
            }
        }

        if (hasParam("-p", args)) {
            outDebug("Port parameter -p given", 2);
            _port = new Integer(getParamValue("-p", args, _port.toString()));
        }

        if (hasParam("-m", args)) {
            outDebug("MAC-Address parameter -m given", 2);
            _mac = getParamValue("-m", args, _mac);
        }

        if (hasParam("-o", args)) {
            outDebug("Using parameter -o (openbox)", 1);
            _authent = true;
            _openbox = true;
        }

        if (hasParam("-c", args)) {
            // send a telnet-command via ACP_CMD
            outDebug("Command-line parameter -c given", 2);
            _authent = true;
            _cmd = getParamValue("-c", args, "");
        }

        if (hasParam("-cb", args)) {
            // clear boot, removes unneccessary files from /boot to free space
            outDebug("Command-line parameter -cb given", 2);
            _authent = true;
            _clearboot = true;
        }

        if (hasParam("-i", args)) {
            outDebug("ConnectionID parameter -i given", 2);
            _connID = getParamValue("-i", args, _connID);
        }

        if (hasParam("-s", args)) {
            _authent = true;
            _shell = true;
        }

        if (hasParam("-gui", args)) {
            _authent = true;
            _gui = true;
            _setgui = new Integer(getParamValue("-gui", args, _setgui.toString()));
        }

        if (hasParam("-diag", args)) {
            _authent = true;
            _diag = true;
        }

        if (hasParam("-reboot", args)) {
            _authent = true;
            _reboot = true;
        }

        if (hasParam("-normmode", args)) {
            _authent = true;
            _normmode = true;
        }

        if (hasParam("-emmode", args)) {
            _authent = true;
            _emmode = true;
            if (_normmode) {
                outWarning(
                        "You specified both '-emmode' and '-normmode' " +
                        "for normal reboot\n" +
                        "--> '-rebootem' will be ignored");
                _emmode = false;
            }
        }

        if (hasParam("-f", args)) {
            // we use -f (find) rather than -d (discover) to avoid any conflicts with debug options
            _authent = false;
            _findLS = true;
        }

        if (hasParam("-b", args)) {
            outDebug("bind to local address parameter -b found", 2);
            _bind = getParamValue("-b", args, "");
            if (_bind.equalsIgnoreCase("")) {
                outError(
                        "You didn't specify a (correct) local address for parameter '-b'");
                return;

            }
        }

        if (hasParam("-blink", args)) {
            outDebug("Command-line parameter -blink given", 2);
            _authent = true; // blink needs autenticate
            _blink = true;
        }

        if (hasParam("-ip", args)) {
            outDebug("Command-line parameter -ip given", 2);
            _newip = getParamValue("-ip", args, "");
            _changeip = true;
            _authent = true; // changeip requires autenticate
        }

        if (hasParam("-pw", args)) {
            outDebug("Command-line parameter -pw given", 2);
            _password = getParamValue("-pw", args, "");
        }

        if (hasParam("-na", args)) {
            // disable authenticate
            outDebug("Using parameter -na (no authentication)", 2);
            _authent = false;
        }

        //
        // Catch some errors.
        //

        _state = "ErrCatch";

        if (!_findLS & ((_target.equals("")) | (_target == null))) {
            outError("No target specified or target is null!");
        }

        if (hasParam("-c", args) & ((_cmd == null) | (_cmd.equals("")))) {
            outError(
                    "Command-line argument -c given, but command line is empty!");
        }

        if ((!_authent) & (_connID.equals(""))) {
            outWarning("Using a random connection ID without authentification!");
        }

        if (_connID.equals("")) {
            // TODO
            // generate random connection ID
            Random generator = new Random();
            byte[] temp_connID = new byte[6];
            generator.nextBytes(temp_connID);
            _connID = bufferToHex(temp_connID, 0, 6);
            System.out.println("Using random connID value = " + _connID);
        } else {
            if (_connID.equalsIgnoreCase("mac")) {
                // TODO
                // get local MAC and set it as connection ID
                _connID = "00:50:56:c0:00:08";
                outWarning("Using local MAC not implemented, yet!\n" +
                           "Using default connID value (" + _connID + ")");
            } else {
                // TODO
                // check given connection id for length and content
                _connID.replaceAll(":", "");
                if (_connID.length() != 12) {
                    outError(
                            "Given connection ID has invalid length (not 6 bytes long)");
                }
            }
        }

        if (_mac.equals("")) {
            // set default MAC
            _mac = "FF:FF:FF:FF:FF:FF";
        } else {
            if (_mac.equalsIgnoreCase("mac")) {
                // TODO
                // get targets MAC and set it
                _mac = "FF:FF:FF:FF:FF:FF";
                outWarning("Using targets MAC is not implemented, yet!\n" +
                           "Using default value (" + _mac + ")");
            } else {
                // TODO
                // check given MAC for length and content
                _mac = _mac.replaceAll(":", "");
                if (_mac.length() != 12) {
                    outError("Given MAC has invalid length (not 6 bytes long)");
                } else {
                    System.out.println("Using MAC: " + _mac);
                }
            }
        }

        if (!_cmd.equals("")) {
            // check for leading and trailing "
            if (_cmd.startsWith("\"")) {
                _cmd = _cmd.substring(1, _cmd.length());

                // only check cmd-line end for " if it starts with one
                if (_cmd.endsWith("\"")) {
                    _cmd = _cmd.substring(0, _cmd.length() - 1);
                }
            }
            outDebug("Using cmd-line:\n>>" + _cmd + "\n", 1);
        }

        if (_changeip) {
            if (_newip.equals("")) {
                outError("You didn't specify a new IP to be set.");
            }

            try {
                InetAddress _testip;
                _testip = InetAddress.getByName(_newip);
                if (_testip.isAnyLocalAddress()) {
                    outError("'" + _newip +
                             "' is recognized as local IP. You must specify an untaken IP");
                }

            } catch (java.net.UnknownHostException Ex) {
                outError("'" + _newip +
                         "' is not recognized as a valid IP for the use as new IP to be set.");
            }
            ;
        }

        //
        // variable definition
        //
        _state = "VarPrep - NewLib";

        ACP myACP = new ACP(_target);
        myACP.DebugLevel = _debug;
        myACP.Port = _port;
        myACP.setConnID(_connID);
        myACP.setTargetMAC(_mac);
        myACP.bind(_bind);

        //
        // Generate some output.
        //
        try {
            _state = "initial status output";
            System.out.println("Using target:\t" +
                               myACP.getTarget().getHostName() +
                               "/" + myACP.getTarget().getHostAddress());
            if (myACP.Port.intValue() != _stdport) {
                System.out.println("Using port:\t" + myACP.Port.toString() +
                                   "\t (this is NOT the standard port)");
            } else {
                outDebug("Using port:\t" + myACP.Port.toString(), 1);
            }
            outDebug("Using MAC-Address:\t" + myACP.getTargetMAC(), 1);

        } catch
                (java.lang.NullPointerException NPE) {
            outError("NullPointerException in " + _state + ".\n" +
                     "Usually this is thrown when the target can not be resolved. " +
                     "Check, if the specified target \"" + _target +
                     "\" is correct!");
        }

        //
        // lets go
        //

        if (_findLS) {
            _state = "ACP_DISCOVER";
            // discover devices by sending both types of ACP-Discover packet
            int _foundLS = 0;

            System.out.println("Sending ACP-Disover packet...");
            String[] foundLS = myACP.Find();
            for (int i = 0; i < foundLS.length; i++) {
                System.out.println(foundLS[i]);
            }
            System.out.println("Found " + foundLS.length + " device(s).");
        }

        if (_authent) {
            _state = "ACP_AUTHENT";
            /**
             * authentication must be on of our first actions, as it has been done before
             * other commands can be sent to the device.
             */
                /**
                 * Buffalos standard authentication procedure:
                 * 1 - send ACPDiscover to get key for password encryption
                 * 2 - send ACPSpecial-EnOneCmd with encrypted password "ap_servd"
                 * 3 - send ACPSpecial-Authent with encrypted admin password
                 */

                System.out.println("Sending Discover packet...\t");
                String[] _discover = myACP.Discover(true);
                System.out.println(_discover[1]);

                System.out.println("Trying to authenticate EnOneCmd...\t" + myACP.EnOneCmd()[1]);

               if (_password.equals("")) {
                 //if password blank, try "password" otherwise prompt
                 System.out.println("Password not specified, trying default password.");
                 _password = "password";
               }

               myACP.setPassword(_password);

               if (!myACP.Authent()[1].equals("ACP_STATE_OK")) {
                 System.out.println(_password);
                 BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

                 try {
                   System.out.print("Please enter your admin password for \"" +_target +"\":\n");
                   PasswordMaskingThread thread = new PasswordMaskingThread();
                   thread.start();
                   _password = thread.getPassword();
                   myACP.setPassword(_password);
                   myACP.Authent();
                 } catch (Exception E) {}
               }
        }

        if (_diag) {
            _state = "diagnostics";
            // do some diagnostics on LS
            System.out.println("\nRunning diagnostics...");

            // display status of backup jobs /etc/melco/backup*:status=
            System.out.print("status of backup jobs:\n");
            String[] BackupState = myACP.Command(
                    "grep status= /etc/melco/backup*", 3);
            System.out.println(BackupState[1]);

            // display language for WebGUI /etc/melco/info:lang=
            System.out.print("language setting of WebGUI:\t"
                             + myACP.Command("grep lang= /etc/melco/info", 3)[1]);

        }

        if (_test) {
            _state = "TEST"; // Test@Georg
            System.out.println("Performing test sequence...");

            try {
//                System.out.println("ACPTest 8000:\t" + myACP.ACPTest("8000")[1]);  //no
//                System.out.println("ACPTest 8010:\t" + myACP.ACPTest("8010")[1]);  //no
//                System.out.println("ACPTest 8040:\t" + myACP.ACPTest("8040")[1]);  //ACP_PING
//                System.out.println("ACPTest 80B0:\t" + myACP.ACPTest("80B0")[1]);  //no
//                System.out.println("ACPTest 80E0:\t" + myACP.ACPTest("80E0")[1]);  //ACP_RAID_INFO
//                System.out.println("ACPTest 80F0:\t" + myACP.ACPTest("80F0")[1]);  //no
//                System.out.println("ACPTest 80C0:\t" + myACP.ACPTest("80C0")[1]);  //no
//                System.out.println("ACPTest 8C00:\t" + myACP.ACPTest("8C00")[1]);  //ACP_Format
//                System.out.println("ACPTest 8D00:\t" + myACP.ACPTest("8D00")[1]);  //ACP_EREASE_USER
//                System.out.println("ACPTest 8E00:\t" + myACP.ACPTest("8E00")[1]);  //no
//                System.out.println("ACPTest 8F00:\t" + myACP.ACPTest("8F00")[1]);  //no
            } catch (Exception ex) {
            }
//                 System.out.println("DebugMode:\t"+myACP.DebugMode()[1]);
//                 System.out.println("Shutdown:\t"+myACP.Shutdown()[1]);
        }

        if (_openbox) {
            _state = "ACP_OPENBOX";
            System.out.println("Reset root pwd...\t" + myACP.Command("passwd -d root", 3)[1]);
	    myACP.Command("rm /etc/securetty", 3);
	    System.out.println("start telnetd...\t" + myACP.Command("/bin/busybox telnetd&", 3)[1]);

            // Due to many questions in the forum...
            System.out.println(
                    "\nYou can now telnet to your box as user 'root' providing " +
                    "no / an empty password. Please change your root password to" +
                    " something secure.");
        }

        if (_clearboot) {
            _state = "clearboot";
            // clear /boot; full /boot is the reason for most ACP_STATE_FAILURE messages
            // send packet up to 3 times
            System.out.println("Sending clear /boot command sequence...\t" +
                               myACP.Command(
                                       "cd /boot; rm -rf hddrootfs.buffalo.updated hddrootfs.img" +
                                       " hddrootfs.buffalo.org hddrootfs.buffalo.updated.done",
                                       3)[
                               1]);
            // show result of df to verify success, send packet up to 3 times
            System.out.println("Output of df for verification...\t" +
                               myACP.Command("df", 3)[1]);
        }

        if (_blink) {
            _state = "blink";
            // blink LED's and play tones via ACP-command
            System.out.println("BlinkLED...\t" + myACP.BlinkLED()[1]);
        }

        if (_gui) {
            _state = "set webgui language";
            // set WebGUI language
            System.out.println("Setting WebGUI language...\t" +
                               myACP.MultiLang(_setgui.byteValue())[1]);
        }

        if (_emmode) {
            _state = "Set EM-Mode";
            // send EM-Mode command
            System.out.println("Sending EM-Mode command...\t");
            String _result = myACP.EMMode()[1];
            System.out.println(_result);
            if (_result.equals("ACP_STATE_OK")) {
                System.out.println("At your next reboot your LS will boot into EM mode.");
            }
        }

        if (_normmode) {
            _state = "Set Norm-Mode";
            // send Norm-Mode command
            System.out.print("Sending Norm-Mode command...\t");
            String _result = myACP.NormMode()[1];
            System.out.println(_result);
            if (_result.equals("ACP_STATE_OK")) {
                System.out.println("At your next reboot your LS will boot into normal mode.");
            }
        }

        if (!_cmd.equals("")) {
            _state = "ACP_CMD";
            // send custom telnet command via ACP
            System.out.println(">" + _cmd + "\n" + myACP.Command(_cmd)[1]);
        }

        // create a telnet style shell, leave with "exit"
        if (_shell) {
            _state = "shell";
            String cmdln = new String("");
            String pwd = new String("/");
            String output = new String("");
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
            System.out.print("Enter telnet commands to device, enter 'exit' to leave\n");

            // get first commandline
            try {
                System.out.print(pwd + ">");
                cmdln = keyboard.readLine();

                while ((cmdln != null) && (!cmdln.equals("exit"))) {
                    // send command and display answer
                    //only first cmd working for some reason.
                    output = myACP.Command("cd " + pwd + ";" + cmdln + ";pwd > /tmp/.pwd")[1];
                    if (output.equals("OK (ACP_STATE_OK)")) {
                      output = "";
                    }
                    System.out.print(output);
                    pwd = myACP.Command("cat /tmp/.pwd")[1].split("\n",2)[0];
                    // get next commandline
                    System.out.print(pwd + ">");
                    cmdln = keyboard.readLine();
                }
            } catch (java.io.IOException IOE) {}
        }

        /**
         * changeip should be one of the last things we do as it will be the last we can do
         * for this sequence.
         */

        if (_changeip) {
            _state = "changeip";

            try {
                int _mytimeout = myACP.Timeout;
                myACP.Timeout = 10000;

                System.out.println("Changeing IP:\t" +
                                   myACP.ChangeIP(InetAddress.getByName(_newip).
                                                  getAddress(),
                                                  new byte[] {(byte) 255,
                                                  (byte) 255, (byte) 255,
                                                  (byte) 0}, true)[1]);

                myACP.Timeout = _mytimeout;
                System.out.println(
                        "\nPlease note, that the current support for the change of the IP "+
                        "is currently very rudimentary.\nThe IP has been set to the given, "+
                        "fixed IP. However DNS and gateway have not been set. Use the "+
                        "WebGUI to make appropriate settings.");
            } catch (java.net.UnknownHostException NetE) {
                outError(NetE.toString() + "[in changeIP]");
            }

        }

        // reboot
        if (_reboot) {
            _state = "reboot";

            System.out.println("Rebooting...:\t" + myACP.Reboot()[1]);
        }

        // shutdown
        if (_shutdown) {
            _state = "shutdown";

            System.out.println("Sending SHUTDOWN command...:\t" + myACP.Shutdown()[1]);
        }
    }
}
