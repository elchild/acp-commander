package acpcommander.util;

public class CannedMessages {
    private ScopedLogger log;

    public CannedMessages(ScopedLogger log){
        this.log = log;
    }

    public void logCommunicationTimeout(int port) {
        System.out.println(
            "A SocketTimeoutException was thrown which usually indicates bad firewall settings.\n"
             + "Check that UDP port " + port + " can be used by ACP Commander to send/receive and that your device is powered on and has booted successfully."
        );

        if (port != 22936) log.outWarning("The Timeout could also be caused as you specified parameter \"-p\" to use UDP port " + port + " which differs from the standard port 22936.");
    }

    public void logPortCommunicationFailure(int port) {
        System.out.println(
            "A SocketException was thrown which often indicates bad firewall settings.\n"
            + "ACP Commander was unable to sent/receive on UDP port " + port + "."
        );
    }

    public void logUnknownTargetHost() {
        System.out.println(
            "An UnknownHostException was thrown, which usually indicates that the specified targetIp is not known to your PC (can not be resolved).\n"
            + "Possible reasons are typos in the targetIp parameter \"-t\", connection or name resolution problems.\n"
            + "Also make sure that the device at the targetIp is powered on and has booted successfully."
        );
    }
}
