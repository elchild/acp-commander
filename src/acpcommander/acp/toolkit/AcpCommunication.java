package acpcommander.acp.toolkit;

import acpcommander.util.ScopedLogger;

import java.net.DatagramSocket;
import java.net.InetSocketAddress;

public class AcpCommunication {
    private ScopedLogger log;
    private InetSocketAddress bindInterface;
    private Integer bindPort;

    public AcpCommunication(ScopedLogger log, InetSocketAddress bindInterface){
        this.log = log;
        this.bindInterface = bindInterface;
    }

    public AcpCommunication(ScopedLogger log, Integer bindPort){
        this.log = log;
        this.bindPort = bindPort;
    }

    public DatagramSocket getSocket(int timeout) throws java.net.SocketException {
        DatagramSocket socket;

        if (bindInterface != null) {
            // bind socket to a local address (-b)
            // Create a socket address from a hostname (_bind) and a port number. A port number
            // of zero will let the system pick up an ephemeral port in a bind operation.
            log.outDebug("Binding socket to: " + bindInterface + "\n", 1);

            socket = new DatagramSocket(bindInterface);
        } else if (bindPort != 0) {
            // Create a socket from a port number.
            socket = new DatagramSocket(bindPort);
            log.outDebug("Binding socket to port: " + bindPort + "\n", 1);
        } else {
            socket = new DatagramSocket();
        }

        socket.setSoTimeout(timeout);

        return socket;
    }
}
