package acpcommander.util;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class AcpCommunication {
    private ScopedLogger log;
    private InetSocketAddress bindInterface;
    private int timeout;

    public AcpCommunication(ScopedLogger log, InetSocketAddress bindInterface, int timeout){
        this.log = log;
        this.bindInterface = bindInterface;
        this.timeout = timeout;
    }

    public DatagramSocket getSocket() throws java.net.SocketException {
        DatagramSocket socket;

        if (bindInterface != null) {
            // bind socket to a local address (-b)
            // Create a socket address from a hostname (_bind) and a port number. A port number
            // of zero will let the system pick up an ephemeral port in a bind operation.
            log.outDebug("Binding socket to: " + bindInterface + "\n", 1);

            socket = new DatagramSocket(bindInterface);
        } else {
            socket = new DatagramSocket();
        }

        socket.setSoTimeout(timeout);

        return socket;
    }
}
