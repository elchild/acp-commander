package acpcommander.launch;

/**
 * <p>Beschreibung: Used to sent ACP-commands to Buffalo(R) devices.
 * Out of the work of nas-central.org (linkstationwiki.net)</p>
 *
 * <p>Copyright: Copyright (c) 2006, GPL</p>
 *
 * <p>Organisation: nas-central.org (linkstationwiki.net)</p>
 *
 * @author Georg
 * @version 0.4.1 (beta)
 */

import java.io.*;

import java.net.*;

import java.nio.charset.StandardCharsets;

import java.util.concurrent.TimeUnit;
import java.util.Random;

import acpcommander.acp.AcpDevice;
import acpcommander.acp.toolkit.reply.AcpReply;
import acpcommander.util.ParameterReader;
import acpcommander.util.ScopedLogger;
import acpcommander.util.StaticFileHandler;
import acpcommander.acp.toolkit.AcpParser;

import com.sun.net.httpserver.HttpServer;

public class AcpCommander {
    private static final String acpCommanderVersion = "0.6 (2021)";
    private static final int standardAcpPort = 22936;

    private static int _debug = 0; // determines degree of additional output.
    private static String _state; // where are we in the code.

    private static ParameterReader params;
    private static ScopedLogger log = new ScopedLogger(0);

    private static void outUsage() {
        System.out.println(
            "Usage:  acp_commander [options] -t target\n\n"
            + "options are:\n"
            + "   -t target .. IP or network name of the Device\n"
            + "   -m MAC   ... define targets mac address set in the ACP package *),\n"
            + "                default = FF:FF:FF:FF:FF:FF.\n"
            + "   -na      ... no authorisation, skip the ACP_AUTH packet. You should\n"
            + "                only use this option together with -i.\n"
            + "   -pw passwd . your admin password. A default password will be tried if omitted.\n"
            + "                if authentication fails you will be prompted for your password\n"
            + "   -i ID    ... define a connection identifier, if not given, a random one will\n"
            + "                be used. (With param MAC the senders MAC address will be used.)\n"
            + "                Successful authentications are stored in conjunction with a \n"
            + "                given connection ID. So you may reuse a previously used one.\n"
            + "                Using a lot of different id's in a chain of commands might\n"
            + "                cause a lot of overhead at the device.\n"
            + "   -p port  ... define alternative target port, default = " + standardAcpPort + "\n"
            + "   -b localip.. bind socket to local address. Use if acp_commander\n"
            + "                can not find your device (might use wrong adapter).\n"
            + "\n"
            + "   -f       ... find device(s) by sending an ACP_DISCOVER package\n"
            + "   -o       ... open the device by sending 'telnetd' and 'passwd -d root',\n"
            + "                thus enabling telnet and clearing the root password\n"
            + "   -c cmd   ... sends the given shell command cmd to the device.\n"
            + "   -s       ... rudimentary interactive shell\n"
            + "   -cb      ... clear \\boot, get rid of ACP_STATE_ERROR after firmware update\n"
            + "                output of df follows for control\n"
            + "   -ip newip... change IP to newip (basic support).\n"
            + "   -blink   ... blink LED's and play some tones\n"
            + "\n"
            + "   -gui nr  ... set Web GUI language 0=Jap, 1=Eng, 2=Ger.\n"
            + "   -diag    ... run some diagnostics on device settings (lang, backup).\n"
            + "   -emMode  ... Device reboots next into EM-mode.\n"
            + "   -normMode .. Device reboots next into normal mode.\n"
            + "   -reboot  ... reboot device.\n"
            + "   -shutdown .. shutdown device.\n"
            + "   -xfer     .. Transfer file from current directory to device via HTTP.\n"
            + "             .. creates backup with .bak extension if file already exists.\n"
            + "   -xferto   .. Optional target directory for -xfer command.\n"
            + "             .. creates directory if it doesn't exist.\n"
            + "\n"
            + "   -d1...-d3 .. set debug level, generate additional output\n"
            + "                debug level >= 3: HEX/ASCII dump of incoming packets\n"
            + "   -q       ... quiet, surppress header, does not work with -h or -v\n"
            + "   -h | -v  ... extended help (this output)\n"
            + "   -u       ... (shorter) usage \n"
            + "\n"
            + "*)  this is not the MAC address the packet is sent to, but the address within\n"
            + "    the ACP packet. The device will only react to ACP packets if they\n"
            + "    carry the correct (its) MAC-address or FF:FF:FF:FF:FF:FF\n"
            + "\n"
            + "This program is based on the work done at nas-central.org (linkstationwiki.net),\n"
            + "which is not related with Buffalo(R) in any way.\n"
            + "Please report issues/enhancement requests at https://github.com/1000001101000/acp-commander"
        );
    }

    private static boolean tcpTest(String host, int port) {

        try (Socket _ = new Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
        //AH: Ignore
    }

    private static String getLocalIP(String ipTarget) {
        try (final DatagramSocket socket = new DatagramSocket()) {
            //try to open a connection to remote IP and record what local IP the OS uses for that connection
            socket.connect(InetAddress.getByName(ipTarget), 10002);

            return socket.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            System.out.println(e.getMessage());
            System.exit(-1);
        }

        return null; //AH: This is a compile time error. It will never get hit because of the System.exit() call
    }

    public static void main(String[] args) {
        params = new ParameterReader(args);

        // variables
        String _mac = "";
        String _connid = "";
        String _target = "";
        int _port = standardAcpPort;
        String _bind = null;

        String _cmd = "";
        String _newip = "";
        String _password = "";
        int _setgui = 1; // set gui to language 0=jap, 1=eng, 2=ger

        // flags what to do, set during parsing the command line arguments
        /*
            AH TODO: When a command needs authentication, it sets the _authent flag. Really, the only use for _authent should be to manually start authentication.
            Commands which require authentication will be calling do*() functions in AcpDevice. Therefore, AcpDevice should have some state handling to know if
            authentication has already been completed (as to not do it again) and to call authentication automatically if a do*() function needs it. Basically,
            any automated authentication should not be handled here!
        */
        boolean _openbox = false;
        boolean _authent = false;
        boolean _shell = false;
        boolean _tcpshell = false;
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

        System.out.println("Welcome to ACP Commander v" + acpCommanderVersion + ", the tool for Buffalo stock firmware control!\n");

        //
        // Parsing the command line parameters.
        //
        _state = "CmdLnParse";

        // catch various standard options for help. Only -h and -v are official, though
        if ((args.length == 0) || (params.hasParam(new String[]{
            "-u", "-usage", "--usage", "/u",
            "-h", "--h", "-?", "--?", "/h", "/?", "-help", "--help",
            "-v", "--v", "-version", "--version"
        }))) {

            outUsage();
            return;
        }

        if (params.hasParam(new String[]{"-d1", "-d2", "-d3"})) {
            if (params.hasParam("-d1")) {
                _debug = 1;
            }

            if (params.hasParam("-d2")) {
                _debug = 2;
            }

            if (params.hasParam("-d3")) {
                _debug = 3;
            }

            System.out.println("Debug level set to " + _debug);
        }

        if (params.hasParam("-test")) {
            _authent = true;
            _test = true;
        }

        if (params.hasParam("-t")) {
            log.outDebug("Target parameter -t found", 2);

            _target = params.getParamValue("-t", "");
        } else {
            if (params.hasParam("-f")) {
                _target = "255.255.255.255"; // if no target is specified for find, use broadcast
            } else if (_target.equals("")) {
                log.outError("You didn't specify a target! Parameter '-t target' is missing");

                return;
            }
        }

        if (params.hasParam("-p")) {
            log.outDebug("Port parameter -p given", 2);

            _port = Integer.parseInt(params.getParamValue("-p", Integer.toString(_port)));
        }

        if (params.hasParam("-m")) {
            log.outDebug("MAC-Address parameter -m given", 2);

            _mac = params.getParamValue("-m", _mac);
        }

        if (params.hasParam("-o")) {
            log.outDebug("Using parameter -o (openbox)", 1);

            _authent = true;
            _openbox = true;
        }

        if (params.hasParam("-c")) {
            // send a telnet-command via ACP_CMD
            log.outDebug("Command-line parameter -c given", 2);

            _authent = true;
            _cmd = params.getParamValue("-c", "");
        }

        if (params.hasParam("-cb")) {
            // clear boot, removes unnecessary files from /boot to free space
            log.outDebug("Command-line parameter -cb given", 2);

            _authent = true;
            _clearboot = true;
        }

        if (params.hasParam("-i")) {
            log.outDebug("connectionid parameter -i given", 2);

            _connid = params.getParamValue("-i", _connid);
        }

        if (params.hasParam("-s")) {
            _authent = true;
            _shell = true;
        }

        if (params.hasParam("-tcpshell")) {
            _authent = true;
            _tcpshell = true;
        }

        if (params.hasParam("-xfer")) {
            _authent = true;
        }

        if (params.hasParam("-gui")) {
            _authent = true;
            _gui = true;
            _setgui = Integer.parseInt(params.getParamValue("-gui", Integer.toString(_setgui)));
        }

        if (params.hasParam("-diag")) {
            _authent = true;
            _diag = true;
        }

        if (params.hasParam("-reboot")) {
            _authent = true;
            _reboot = true;
        }

        if (params.hasParam("-normMode")) {
            _authent = true;
            _normmode = true;
        }

        if (params.hasParam("-emMode")) {
            _authent = true;
            _emmode = true;

            if (_normmode) {
                log.outWarning("You specified both '-emMode' and '-normMode' for normal reboot\n" + "--> '-rebootem' will be ignored");
                _emmode = false;
            }
        }

        if (params.hasParam("-f")) {
            // we use -f (find) rather than -d (discover) to avoid any conflicts with debug options
            _authent = false;
            _findLS = true;
        }

        if (params.hasParam("-b")) {
            log.outDebug("bind to local address parameter -b found", 2);

            _bind = params.getParamValue("-b", "");

            if (_bind.equalsIgnoreCase("")) {
                log.outError("You didn't specify a (correct) local address for parameter '-b'");
                return;
            }
        }

        if (params.hasParam("-blink")) {
            log.outDebug("Command-line parameter -blink given", 2);

            _authent = true; // blink needs authenticate
            _blink = true;
        }

        if (params.hasParam("-ip")) {
            log.outDebug("Command-line parameter -ip given", 2);

            _newip = params.getParamValue("-ip", "");
            _changeip = true;
            _authent = true; // changeIp requires authenticate
        }

        if (params.hasParam("-pw")) {
            log.outDebug("Command-line parameter -pw given", 2);

            _password = params.getParamValue("-pw", "");
        }

        if (params.hasParam("-na")) {
            // disable authenticate
            log.outDebug("Using parameter -na (no authentication)", 2);

            _authent = false;
        }

        //
        // Catch some errors.
        //

        _state = "ErrCatch";

        if (!_findLS && ((_target.equals("")) || (_target == null))) {
            log.outError("No target specified or target is null!");
        }

        if (params.hasParam("-c") && ((_cmd == null) || (_cmd.equals("")))) {
            log.outError("Command-line argument -c given, but command line is empty!");
        }

        if ((!_authent) && (_connid.equals("") && !_findLS)) {
            log.outWarning("Using a random connection ID without authentication!");
        }

        if (_connid.equals("")) {
            // TODO
            // generate random connection ID
            Random generator = new Random();

            byte[] temp_connid = new byte[6];
            generator.nextBytes(temp_connid);

            _connid = AcpParser.takeHexFromPacket(temp_connid, 0, 6);

            log.outDebug("Using random connid value = " + _connid, 1);
        } else {
            if (_connid.equalsIgnoreCase("mac")) {
                // TODO
                // get local MAC and set it as connection ID
                _connid = "00:50:56:c0:00:08";

                log.outWarning(
                    "Using local MAC not implemented, yet!\n"
                    + "Using default connid value (" + _connid + ")"
                );
            } else {
                // TODO
                // check given connection id for length and content
                _connid = _connid.replaceAll(":", "");
                if (_connid.length() != 12) {
                    log.outError("Given connection ID has invalid length (not 6 bytes long)");
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

                log.outWarning(
                    "Using targets MAC is not implemented, yet!\n"
                    + "Using default value (" + _mac + ")"
                );
            } else {
                // TODO check given MAC for length and content
                _mac = _mac.replaceAll(":", "");

                if (_mac.length() != 12) {
                    log.outError("Given MAC has invalid length (not 6 bytes long)");
                } else {
                    System.out.println("Using MAC: " + _mac);
                }
            }
        }

        if (_changeip) {
            if (_newip.equals("")) {
                log.outError("You didn't specify a new IP to be set.");
            }

            try {
                InetAddress _testip = InetAddress.getByName(_newip);

                if (_testip.isAnyLocalAddress()) {
                    log.outError("'" + _newip + "' is recognized as local IP. You must specify an IP which has not been taken on your local network");
                }
            } catch (UnknownHostException e) {
                log.outError("'" + _newip + "' is not recognized as a valid IP for the use as new IP to be set.");
            }
        }

        //
        // variable definition
        //
        _state = "VarPrep - NewLib";

        AcpDevice device = new AcpDevice(log, _target);
        log.debugLevel = _debug;
        device.port = _port;
        device.setConnectionId(_connid);
        device.setTargetMac(_mac);
        device.bind(_bind);

        //
        // Generate some output.
        //
        try {
            _state = "initial status output";

            log.outDebug("Using target:\t" + device.getTarget().getHostName() + "/" + device.getTarget().getHostAddress(), 1);

            if (device.port != standardAcpPort) {
                System.out.println("Using port:\t" + device.port + "\t (this is NOT the standard port)");
            } else {
                log.outDebug("Using port:\t" + device.port, 1);
            }

            log.outDebug("Using MAC-Address:\t" + device.getTargetMac(), 1);

        } catch (NullPointerException e) {
            log.outError(
                "NullPointerException in " + _state + ".\n"
                + "Usually this is thrown when the target can not be resolved.\n"
                + "Check if the specified target \"" + _target + "\" is correct!"
            );
        }

        //
        // lets go
        //

        if (_findLS) {
            _state = "ACP_DISCOVER";
            // discover devices by sending both types of ACP-Discover packet
            log.outDebug("Sending discovery packet...", 1);

            /*String[] foundLS = device.find();
            for (int i = 0; i < foundLS.length; i++) {
                System.out.println(foundLS[i]);
            }

            System.out.println("Found " + foundLS.length + " device(s).");*/

            AcpReply reply = device.find();
            int deviceCount = Integer.parseInt(reply.extraInformationMetadata);

            System.out.println(reply.extraInformation += "\n");
            System.out.println("Found " + deviceCount + " device" + (deviceCount == 1 ? "" : "s") + ".");
        }

        if (_authent) {
            _state = "ACP_AUTHENT";

            /**
             * authentication must be on of our first actions, as it has been done before
             * other commands can be sent to the device.
             */
            /**
             * Buffalo's standard authentication procedure:
             * 1 - send ACPDiscover to get key for password encryption
             * 2 - send ACPSpecial-enOneCmd with encrypted password "ap_servd"
             * 3 - send ACPSpecial-authenticate with encrypted admin password
             */

            //log.outDebug("Trying to authenticate enOneCmd...\t" + device.enOneCmd()[1], 1);
            log.outDebug("Trying to authenticate enOneCmd...\t" + device.enOneCmd().extraInformation, 1); //AH Todo: I think this isn't quite right

            if (_password.equals("")) {
                //if password blank, try "password" otherwise prompt
                log.outDebug("Password not specified, trying default password.", 1);
                _password = "password";
            }

            device.setPassword(_password);

            //if (!device.authenticate()[1].equals("ACP_STATE_OK")) {
            if (!device.authenticate().extraInformation.equals("ACP_STATE_OK")) { //AH Todo: I think this isn't quite right
                Console console = System.console();

                try {
                    _password = new String(console.readPassword("admin password: "));
                    device.setPassword(_password);
                    device.authenticate();
                } catch (Exception e) {
                    //AH: Ignore
                }
            }
        }

        if (_diag) {
            _state = "diagnostics";

            // do some diagnostics on LS
            System.out.println("\nRunning diagnostics...");

            // display status of backup jobs /etc/melco/backup*:status=
            System.out.print("status of backup jobs:\n");

            /*String[] BackupState = device.command("grep status= /etc/melco/backup*", 3);
            System.out.println(BackupState[1]);*/

            AcpReply BackupState = device.command("grep status= /etc/melco/backup*", 3);
            System.out.println(BackupState.extraInformation);

            // display language for WebGUI /etc/melco/info:lang=
            System.out.print("language setting of WebGUI:\t" + device.command("grep lang= /etc/melco/info", 3).extraInformation);
        }

        if (_test) {
            _state = "TEST"; // Test@Georg

            System.out.println("Performing test sequence...");

            try {
                //System.out.println("ACPTest 8000:\t" + device.ACPTest("8000")[1]);  //no
                //System.out.println("ACPTest 8010:\t" + device.ACPTest("8010")[1]);  //no
                //System.out.println("ACPTest 8040:\t" + device.ACPTest("8040")[1]);  //ACP_PING
                //System.out.println("ACPTest 80B0:\t" + device.ACPTest("80B0")[1]);  //no
                //System.out.println("ACPTest 80E0:\t" + device.ACPTest("80E0")[1]);  //ACP_RAID_INFO
                //System.out.println("ACPTest 80F0:\t" + device.ACPTest("80F0")[1]);  //no
                //System.out.println("ACPTest 80C0:\t" + device.ACPTest("80C0")[1]);  //no
                //System.out.println("ACPTest 8C00:\t" + device.ACPTest("8C00")[1]);  //ACP_Format
                //System.out.println("ACPTest 8D00:\t" + device.ACPTest("8D00")[1]);  //ACP_ERASE_USER
                //System.out.println("ACPTest 8E00:\t" + device.ACPTest("8E00")[1]);  //no
                //System.out.println("ACPTest 8F00:\t" + device.ACPTest("8F00")[1]);  //no
            } catch (Exception e) {
                //AH: Ignore
            }

            //System.out.println("debugmode:\t"+device.debugmode()[1]);
            //System.out.println("Shutdown:\t"+device.shutdown()[1]);
        }

        if (_openbox) {
            _state = "ACP_OPENBOX";

            System.out.println("Reset root pwd...\t" + device.command("passwd -d root", 3).extraInformation);

            device.command("rm /etc/securetty", 3);
            device.command("mkdir /dev/pts; mount devpts /dev/pts -t devpts", 3);

            System.out.print("Starting Telnet .");

            device.command("/bin/busybox telnetd&", 3);
            device.command("chmod +x /tmp/busybox", 3);
            device.command("/tmp/busybox telnetd&", 3);

            boolean telnetup = false;

            for (int i = 0; i < 8; i++) {
                System.out.print(".");

                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    //AH: Ignore
                }

                if (tcpTest(_target, 23)) {
                    System.out.println(" Success!\n");
                    System.out.println("You can now telnet to your box as user 'root' providing no / an empty password. Please change your root password to something secure.");

                    telnetup = true;

                    break;
                }
            }

            if (!telnetup) {
                System.out.print("Failed!\n");
                System.out.println("\nUnable to detect telnet server. \nThis could be a firewall issue but more likely this model does not have a telnetd binary installed.\n Consider using \"-s\" as an alternative.");
            }
        }

        if (_clearboot) {
            _state = "clearboot";

            // clear /boot; full /boot is the reason for most ACP_STATE_FAILURE messages
            // send packet up to 3 times
            System.out.println("Sending clear /boot command sequence...\t"  + device.command("cd /boot; rm -rf hddrootfs.buffalo.updated hddrootfs.img hddrootfs.buffalo.org hddrootfs.buffalo.updated.done", 3).extraInformation);

            // show result of df to verify success, send packet up to 3 times
            System.out.println("Output of df for verification...\t" + device.command("df", 3).extraInformation);
        }

        if (_blink) {
            _state = "blink";

            // blink LED's and play tones via ACP-command
            System.out.println("blinkLed...\t" + device.blinkLed().extraInformation);
        }

        if (_gui) {
            _state = "set webgui language";

            // set WebGUI language
            System.out.println("Setting WebGUI language...\t" + device.setWebUiLanguage((byte) _setgui).extraInformation);
        }

        if (_emmode) {
            _state = "Set EM-Mode";

            // send EM-Mode command
            System.out.println("Sending EM-Mode command...\t");

            String reply = device.emMode().extraInformation;

            System.out.println(reply);

            if (reply.equals("ACP_STATE_OK")) {
                System.out.println("At your next reboot your device will boot into EM mode.");
            }
        }

        if (_normmode) {
            _state = "Set Norm-Mode";

            // send Norm-Mode command
            System.out.print("Sending Norm-Mode command...\t");

            String reply = device.normMode().extraInformation;

            System.out.println(reply);

            if (reply.equals("ACP_STATE_OK")) {
                System.out.println("At your next reboot your device will boot into normal mode.");
            }
        }

        if (!_cmd.equals("")) {
            // check for leading and trailing "

            if (_cmd.startsWith("\"")) {
                _cmd = _cmd.substring(1);

                // only check cmd-line end for " if it starts with one
                if (_cmd.endsWith("\"")) {
                    _cmd = _cmd.substring(0, _cmd.length() - 1);
                }
            }

            log.outDebug("Using cmd-line:\n>>" + _cmd + "\n", 1);
        }

        if (!_cmd.equals("")) {
            _state = "ACP_CMD";

            // send custom command via ACP
            String commandResult = device.command(_cmd).extraInformation;
            log.outDebug(">" + _cmd + "\n", 1);
            System.out.print(commandResult);
        }

        // create a telnet style shell, leave with "exit"
        if (_shell) {
            _state = "shell";

            String shellOutput;
            String shellInput;

            String currentWorkingDirectory = "/";

            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

            System.out.print("Enter commands to device, enter 'exit' to leave\n");

            // get first commandline
            try {
                System.out.print(currentWorkingDirectory + ">");

                shellInput = keyboard.readLine();

                while ((shellInput != null) && (!shellInput.equals("exit"))) {
                    // send command and display answer
                    //only first cmd working for some reason.
                    shellOutput = device.command("cd " + currentWorkingDirectory + ";" + shellInput + "; pwd > /tmp/.pwd").extraInformation;

                    if (shellOutput.equals("OK (ACP_STATE_OK)")) {
                        shellOutput = "";
                    }

                    System.out.print(shellOutput);

                    currentWorkingDirectory = device.command("cat /tmp/.pwd").extraInformation.split("\n", 2)[0];

                    // get next commandline
                    System.out.print(currentWorkingDirectory + ">");
                    shellInput = keyboard.readLine();
                }
            } catch (IOException e) {
                //AH: Ignore
            }
        }

        if (_tcpshell) {
            BufferedReader in;
            PrintWriter out;
            BufferedReader stdIn;
            ServerSocket serverSocket;
            Socket socket;

            //seems like no python in emMode
            String normalModeCommand = "python -c \'import pty; pty.spawn(\"/bin/bash\")\'";
            String emModeCommand = "stty -echo";

            try {
                serverSocket = new ServerSocket(0);
                String localIp = getLocalIP(_target);
                int localPort = serverSocket.getLocalPort();

                device.command("bash -i >&/dev/tcp/" + localIp + "/" + localPort + " 0>&1 &");

                socket = serverSocket.accept();

                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
                stdIn = new BufferedReader(new InputStreamReader(System.in));

                //suppress first line (bash warning)
                in.readLine();

                //send commands to upgrade tty
                out.println(normalModeCommand);
                out.println(emModeCommand);

                //supress output of those commands
                in.readLine();
                in.readLine();
                in.readLine();

                while (!socket.isClosed()) {
                    if (in.ready()) {
                        System.out.printf("%c", in.read());
                    }

                    if (stdIn.ready()) {
                        out.println(stdIn.readLine());
                    }

                    //detect disconnect by reading socket? what happens if not disconnected?
                    //sleep a few ms for sanity?
                }

                in.close();
                out.close();
                socket.close();
                serverSocket.close();
            } catch (Exception e) {
                System.out.println(e);
            }
        }

        if (params.hasParam("-xfer")) {
            log.outDebug("\n\nUsing parameter -xfer (file transfer)", 1);

            String output;
            String localIp;
            String fileName = params.getParamValue("-xfer", "");
            String localDirectory = System.getProperty("user.dir");
            String targetDirectory = "/root";
            String currentCommand;
            int localHttpServerPort = 0;

            log.outDebug("Filename: " + fileName, 1);

            //check for optional destination directory
            if (params.hasParam("-xferto")) {
                //validate somehow?
                log.outDebug("Using parameter -xferto (target directory)", 1);
                targetDirectory = params.getParamValue("-xferto", "");
            }

            log.outDebug("Target Directory: " + targetDirectory, 1);

            //check if local file exists
            File checkfile = new File(localDirectory, fileName);

            if (!checkfile.exists()) {
                System.out.println("local file does not exist!");
                System.exit(-1);
            }

            //open a socket to target and record the local ip address the OS used
            localIp = getLocalIP(_target);

            //have socket find and test a port, then free it to attach the server to
            try {
                ServerSocket s = new ServerSocket(0);
                localHttpServerPort = s.getLocalPort();
                s.close();
            } catch (IOException e) {
                System.out.println(e);
                System.exit(-1);
            }

            //build the url for the device to download from
            String localUrl = "http://" + localIp + ":" + localHttpServerPort + "/" + fileName;

            log.outDebug("File URL: " + localUrl, 1);

            //start an HTTP server in the current directory
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(localIp, localHttpServerPort), 0);
                server.createContext("/", new StaticFileHandler(localDirectory));
                server.start();
                log.outDebug("Starting HTTP...", 1);

                //output the contents of the directory
                currentCommand = "ls -l " + targetDirectory;
                log.outDebug("starting contents... " + currentCommand, 1);
                output = device.command(currentCommand).extraInformation;
                log.outDebug(output, 1);

                //create the target directory if absent
                currentCommand = "mkdir -p " + targetDirectory;
                log.outDebug("creating target directory... " + currentCommand, 1);
                output = device.command(currentCommand + "; echo $?").extraInformation;
                log.outDebug("return code: " + output, 1);

                //backup file if it already exists
                currentCommand = "cd " + targetDirectory + ";" + "mv " + fileName + " " + fileName + ".bak";
                log.outDebug("backup file if present... " + currentCommand, 1);
                output = device.command(currentCommand + "; echo $?").extraInformation;
                log.outDebug("return code: " + output, 1);

                //download the file to the target directory
                currentCommand = "cd " + targetDirectory + ";" + "wget " + localUrl;
                log.outDebug("attempting transfer using wget... " + currentCommand, 1);
                output = device.command(currentCommand + "; echo $?").extraInformation;
                log.outDebug("return code: " + output, 1);

                //output the contents of the directory
                currentCommand = "ls -l " + targetDirectory;
                log.outDebug("ending contents... " + currentCommand, 1);
                output = device.command(currentCommand).extraInformation;
                log.outDebug(output, 1);

                server.stop(0);
                log.outDebug("Stopping HTTP...", 1);
            } catch (IOException e) {
                System.out.println(e);
                System.exit(-1);
            }

            //somehow judge success/fail
        }

        /**
         * changeIp should be one of the last things we do as it will be the last we can do
         * for this sequence.
         */

        if (_changeip) {
            _state = "changeIp";

            try {
                System.out.println("Changing IP:\t" + device.changeIp(InetAddress.getByName(_newip).getAddress(), new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 0}, true).extraInformation);

                System.out.println(
                    "\nPlease note, that the current support for the change of the IP is currently very rudimentary.\n"
                    + "The IP has been set to the given fixed IP, however DNS servers and the gateway have not been set.\n"
                    + "Use the WebGUI to make appropriate settings."
                );
            } catch (UnknownHostException e) {
                log.outError(e + " [in changeIP]");
            }
        }

        // reboot
        if (_reboot) {
            _state = "reboot";

            System.out.println("Rebooting...:\t" + device.reboot().extraInformation);
        }

        // shutdown
        if (_shutdown) {
            _state = "shutdown";

            System.out.println("Sending SHUTDOWN command...:\t" + device.shutdown().extraInformation);
        }
    }
}
