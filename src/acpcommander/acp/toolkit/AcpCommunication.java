package acpcommander.acp.toolkit;

import acpcommander.util.ScopedLogger;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class AcpCommunication {
    private ScopedLogger log;
    private InetSocketAddress bindInterface;
    private Integer port;

    public AcpCommunication(ScopedLogger log, InetSocketAddress bindInterface){
        this.log = log;
        this.bindInterface = bindInterface;
    }

    public AcpCommunication(ScopedLogger log, Integer port){
        this.log = log;
        this.port = port;
    }

    public DatagramSocket getSocket(int timeout) throws java.net.SocketException {
        DatagramSocket socket;

        if (bindInterface != null) {
            // bind socket to a local address (-b)
            // Create a socket address from a hostname (_bind) and a port number. A port number
            // of zero will let the system pick up an ephemeral port in a bind operation.
            log.outDebug("Binding socket to: " + bindInterface + "\n", 1);

            socket = new DatagramSocket(bindInterface);
        } else if (port != 0) {
            // Create a socket from a port number.
            socket = new DatagramSocket(port);
            log.outDebug("Binding socket to port: " + port + "\n", 1);
        } else {
            socket = new DatagramSocket();
        }

        socket.setSoTimeout(timeout);

        return socket;
    }
}
