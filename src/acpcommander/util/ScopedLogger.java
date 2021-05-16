package acpcommander.util;

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
}
