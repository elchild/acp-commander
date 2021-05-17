package acpcommander.util;

public class ParameterReader {
    private String[] input;

    public ParameterReader(String[] input){
        this.input = input;
    }

    // private static boolean hasParam(String name, String[] args)
    // checks whether parameter "name" is specified in "args"
    public boolean hasParam(String name) {
        for (int i = 0; i < input.length; ++i) {
            if (input[i].equals(name)) {
                return true;
            }
        }

        return false;
    }

    // private static boolean hasParam(String[] names, String[] args) {
    // checks whether one of the parameters in "names" is specified in "args"
    public boolean hasParam(String[] names) {
        for (int i = 0; i < input.length; ++i) {
            for (int j = 0; j < names.length; ++j) {
                if (input[i].equals(names[j])) {
                    return true;
                }
            }
        }

        return false;
    }

    // private static String getParamValue(String name, String[] args, String defvalue)
    // retrieve the value passed to parameter "name" within the arguments "args",
    // returns "defvalue" if argument "name" could not be found.
    public String getParamValue(String name, String defaultValue) {
        // not looking at the last argument, as it would have no following parameter
        for (int i = 0; i < input.length - 1; ++i) {
            if (input[i].equals(name)) {
                return input[i + 1];
            }
        }

        return defaultValue;
    }
}
