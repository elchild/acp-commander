package acpcommander.util;

public class ScopedLogger {
    public int debugLevel;

    public ScopedLogger(int debugLevel){
        this.debugLevel = debugLevel;
    }

    public void outDebug(String message, int debuglevel) {
        // negative debuglevels are considered as errors!
        if (debuglevel < 0) {
            outError(message);
            return;
        }

        if (debuglevel <= getDebugLevel()) {
            System.out.println(message);
        }
    }

    public void outError(String message) {
        System.err.println("[ERROR] " + message);
        System.exit(-1);
    }

    public void outWarning(String message) {
        System.out.println("[WARN] " + message);
    }

    int getDebugLevel() {
        return debugLevel;
    }
}
