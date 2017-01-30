package org.lilleypad.featherloggerapp.util;

/**
 * This class serves to provide various constants used in the parsing of data received
 * from the FeatherLogger.
 * <p>
 * Created by Duncan Lilley on 11/2/2016.
 */

public class FeatherLoggerCmd {

    // String parsing constants
    private final static String CMD_IDENTIFIER = "%";
    public final static String END = toCmd("END"); // The command ending for input from the FeatherLogger
    public final static String DELIM = "&"; // The command delimiter

    // Commands from FeatherLogger
    public final static String LOGGER_INFO_CMD = toCmd("INFO");
    public final static String LOGGER_FILES_CMD = toCmd("FILES");
    public final static String LOGGER_FILE_DOWNLOAD = toCmd("FILEDL");

    // Commands to FeatherLogger
    public final static String REQUEST_DATA = "REQ+DATA";


    private static String toCmd(String str) {
        return CMD_IDENTIFIER + str + CMD_IDENTIFIER;
    }
}
