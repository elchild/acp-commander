package acpcommander.util;

import acpcommander.acp.toolkit.reply.AcpReply;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ScopedLogger {
    public int debugLevel;
    public boolean quietMode;

    public ScopedLogger(int debugLevel, boolean quietMode){
        this.debugLevel = debugLevel;
        this.quietMode = quietMode;
    }

    public void outDebug(String message, int requiredDebugLevel) {
        // negative debuglevels are considered as errors!
        if (requiredDebugLevel < 0) {
            outError(message);
            return;
        }

        if (requiredDebugLevel <= debugLevel) {
            System.out.println("[DEBUG] " + message);
        }
    }

    public void outError(String message) {
        System.err.println("[ERROR] " + message);
        System.exit(-1);
    }

    public void outWarning(String message) {
        if(!quietMode){
            System.out.println("[WARN] " + message);
        }
    }

    public void out(String message){
        System.out.print(message);
    }

    public void outLn(String message){
        out(message + "\n");
    }

    public void outLoudOnly(String message){
        if(!quietMode){
            out(message);
        }
    }

    public void outLoudOnlyLn(String message){
        if(!quietMode){
            outLn(message);
        }
    }

    public void outQuietOnly(String message){
        if(quietMode){
            out(message);
        }
    }

    public void outQuietOnlyLn(String message){
        if(quietMode){
            outLn(message);
        }
    }

    public static String buildTabularReplyTable(List<AcpReply> associatedReplies) {
        AcpReply tableHeader = new AcpReply();
        tableHeader.hostname = "Hostname";
        tableHeader.mac = "MAC";
        tableHeader.ip = "IP";
        tableHeader.productIdString = "Product ID String";
        tableHeader.productId = "Product ID";
        tableHeader.firmwareVersion = "Firmware";
        associatedReplies.add(tableHeader);

        String table = "";

        //AH: Get longest hostname
        Collections.sort(associatedReplies, new Comparator<AcpReply>() {
            public int compare(AcpReply replyA, AcpReply replyB) {
                return replyA.hostname.length() > replyB.hostname.length() ? -1 : 1;
            }
        });

        int largestHostnameLength = associatedReplies.get(0).hostname.length();

        //AH: Get longest MAC
        Collections.sort(associatedReplies, new Comparator<AcpReply>() {
            public int compare(AcpReply replyA, AcpReply replyB) {
                return replyA.mac.length() > replyB.mac.length() ? -1 : 1;
            }
        });

        int largestMacLength = associatedReplies.get(0).mac.length();

        //AH: Get longest ip
        Collections.sort(associatedReplies, new Comparator<AcpReply>() {
            public int compare(AcpReply replyA, AcpReply replyB) {
                return replyA.ip.length() > replyB.ip.length() ? -1 : 1;
            }
        });

        int largestIpLength = associatedReplies.get(0).ip.length();

        //AH: Get longest productIdString
        Collections.sort(associatedReplies, new Comparator<AcpReply>() {
            public int compare(AcpReply replyA, AcpReply replyB) {
                return replyA.productIdString.length() > replyB.productIdString.length() ? -1 : 1;
            }
        });

        int largestProductIdStringLength = associatedReplies.get(0).productIdString.length();

        //AH: Get longest productId
        Collections.sort(associatedReplies, new Comparator<AcpReply>() {
            public int compare(AcpReply replyA, AcpReply replyB) {
                return replyA.productId.length() > replyB.productId.length() ? -1 : 1;
            }
        });

        int largestProductIdLength = associatedReplies.get(0).productId.length();

        //AH: Get longest firmwareVersion
        Collections.sort(associatedReplies, new Comparator<AcpReply>() {
            public int compare(AcpReply replyA, AcpReply replyB) {
                return replyA.firmwareVersion.length() > replyB.firmwareVersion.length() ? -1 : 1;
            }
        });

        int largestFirmwareVersionLength = associatedReplies.get(0).firmwareVersion.length();

        //AH: Remove the header row because we want to force it to be the first printed row
        associatedReplies.remove(tableHeader);

        //AH: Add top border split row
        table += generateTableSplitRow(new int[]{largestHostnameLength, largestMacLength, largestIpLength, largestProductIdStringLength, largestProductIdLength, largestFirmwareVersionLength});

        //AH: Add header row
        table += "\n" + generateTableContentRow(
                new int[]{largestHostnameLength, largestMacLength, largestIpLength, largestProductIdStringLength, largestProductIdLength, largestFirmwareVersionLength},
                new String[]{tableHeader.hostname, tableHeader.mac, tableHeader.ip, tableHeader.productIdString, tableHeader.productId, tableHeader.firmwareVersion}
        );

        table += "\n" + generateTableSplitRow(new int[]{largestHostnameLength, largestMacLength, largestIpLength, largestProductIdStringLength, largestProductIdLength, largestFirmwareVersionLength});

        //AH: Add a device row and then a border split row per-device
        for(int device = 0; device < associatedReplies.size(); device++){
            table += "\n" + generateTableContentRow(
                new int[]{largestHostnameLength, largestMacLength, largestIpLength, largestProductIdStringLength, largestProductIdLength, largestFirmwareVersionLength},
                new String[]{associatedReplies.get(device).hostname, associatedReplies.get(device).mac, associatedReplies.get(device).ip, associatedReplies.get(device).productIdString, associatedReplies.get(device).productId, associatedReplies.get(device).firmwareVersion}
            );

            table += "\n" + generateTableSplitRow(new int[]{largestHostnameLength, largestMacLength, largestIpLength, largestProductIdStringLength, largestProductIdLength, largestFirmwareVersionLength});
        }

        return table;
    }

    private static String generateTableSplitRow(int[] columnWidths){
        String splitRow = "+";

        for (int column = 0; column < columnWidths.length; column++){
            splitRow += "-";

            for (int columnCharacter = 0; columnCharacter < columnWidths[column]; columnCharacter++){
                splitRow += "-";
            }

            splitRow += "-+";
        }

        return splitRow;
    }

    private static String generateTableContentRow(int[] columnWidths, String[] columnValues){
        String contentRow = "|";

        for (int column = 0; column < columnWidths.length; column++){
            contentRow += " ";

            for (int columnCharacter = 0; columnCharacter < columnWidths[column]; columnCharacter++){
                try{
                    contentRow += columnValues[column].charAt(columnCharacter);
                }catch(IndexOutOfBoundsException e){
                    contentRow += " ";
                }
            }

            contentRow += " |";
        }

        return contentRow;
    }
}
