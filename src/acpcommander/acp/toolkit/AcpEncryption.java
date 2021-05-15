package acpcommander.acp.toolkit;

import acpcommander.util.ScopedLogger;

import java.nio.charset.Charset;

public class AcpEncryption {
    private Charset charset;
    private ScopedLogger log;

    public AcpEncryption(ScopedLogger log, Charset charset){
        this.charset = charset;
        this.log = log;
    }

    public byte[] encryptAcpPassword(String password, byte[] newkey) {
        if (password.length() > 24) {
            log.outError("The acp_commander only allows password lengths up to 24 chars");
        }
        if (password.length() == 0) {
            return new byte[8];
        }

        byte[] subpasswd = new byte[8];
        int sublength;
        byte[] result = new byte[(password.length() + 7 >> 3) * 8];

        for (int i = 0; i < (password.length() + 7) >> 3; i++) {
            sublength = password.length() - i * 8;
            if (sublength > 8) {
                sublength = 8;
            }

            System.arraycopy(password.substring(i * 8).getBytes(charset), 0,
                    subpasswd, 0, sublength);
            if (sublength < 8) {
                subpasswd[sublength] = (byte) 0x00; // end of string must be 0x00
            }

            System.arraycopy(baseEncryptAcpPassword(subpasswd, newkey), 0, result, i * 8, 8);
        }

        return result;
    }

    private byte[] baseEncryptAcpPassword(byte[] password, byte[] outkey) {
        //
        // mimics route from LSUpdater.exe, starting at 0x00401700
        // key is a 4 byte array (changed order, key 6ae2ad78 => (0x6a, 0xe2, 0xad, 0x78)
        // password = ap_servd, key= 6ae2ad78 gives encrypted 19:A4:F7:9B:AF:7B:C4:DD
        //
        byte[] newkey = new byte[8];
        byte[] result = new byte[8];

        // first generate initial encryption key (newkey) from key
        for (int i = 0; i < 4; i++) {
            newkey[3 - i] = outkey[i]; // lower 4 bytes
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
}
