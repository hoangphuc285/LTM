package network;

public class FTPResponse {
    private final int code;
    private final String message;

    public FTPResponse(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSuccess() {
        return code >= 200 && code < 300;
    }

    public boolean isPositiveIntermediate() {
        return code >= 300 && code < 400;
    }

    @Override
    public String toString() {
        return code + " " + message;
    }
}