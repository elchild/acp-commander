package acpcommander.acp.toolkit.reply;

import java.util.ArrayList;
import java.util.List;

public class AcpReply {
    public AcpReplyType packetType = AcpReplyType.UnknownReply;
    public String hostname = "";
    public String mac = "";
    public String ip = "";
    public String productIdString = "";
    public String productId = "";
    public String firmwareVersion = "";
    public String key = "";

    public String extraInformation = "";

    public List<AcpReply> associatedReplies = new ArrayList<>();
}
