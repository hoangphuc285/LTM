package MyFTPServer;

public class FTPResponse {
    public static final String R_220_WELCOME = "220 Welcome to MyFTPServer";
    public static final String R_331_NEED_PASS = "331 Password required for ";
    public static final String R_230_LOGGED_IN = "230 User logged in, proceed.";
    public static final String R_221_GOODBYE = "221 Goodbye.";
    public static final String R_530_NOT_LOGGED_IN = "530 Please login with USER and PASS.";
    public static final String R_530_AUTH_FAILED = "530 Authentication failed.";
    public static final String R_500_UNKNOWN = "500 Command not recognized.";

    public static String formatPWD(String path) {
        return "257 \"" + path + "\" is current directory.";
    }
}