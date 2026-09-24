package network;

public class FTPResponseParser {
    public static FTPResponse parse(String rawResponse) {
        if (rawResponse == null || rawResponse.length() < 3) {
            return new FTPResponse(500, "Invalid or empty response");
        }
        try {
            int code = Integer.parseInt(rawResponse.substring(0, 3));
            String message = rawResponse.length() > 4 ? rawResponse.substring(4).trim() : "";
            return new FTPResponse(code, message);
        } catch (NumberFormatException e) {
            return new FTPResponse(500, rawResponse);
        }
    }
}