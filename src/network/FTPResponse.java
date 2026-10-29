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

    /**
     * BUG FIX: Kiểm tra response 1xx (Preliminary) – server báo đã sẵn sàng truyền data.
     * Client PHẢI nhận được 150/125 trước khi đọc/ghi data socket.
     * Ví dụ: "150 Opening data connection", "125 Data connection already open".
     */
    public boolean isPreliminary() {
        return code >= 100 && code < 200;
    }

    public boolean isPositiveIntermediate() {
        return code >= 300 && code < 400;
    }

    @Override
    public String toString() {
        return code + " " + message;
    }
}
