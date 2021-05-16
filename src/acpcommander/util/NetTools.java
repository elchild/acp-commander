package acpcommander.util;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class NetTools {
    public static boolean tcpTest(String host, int port) {
        try (Socket _ = new Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getLocalIP(String ipTarget) throws IOException {
        try (final DatagramSocket socket = new DatagramSocket()) {
            //try to open a connection to remote IP and record what local IP the OS uses for that connection
            socket.connect(InetAddress.getByName(ipTarget), 10002);

            return socket.getLocalAddress().getHostAddress();
        } catch (IOException e) {
            throw e;
        }
    }
}
