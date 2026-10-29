package network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.function.Consumer;

public class FTPConnection {
    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean connected = false;

    // P11: Listener để ghi nhật ký lệnh Client-side (tương tự Message Log của FileZilla)
    private Consumer<String> logListener;

    public void setLogListener(Consumer<String> logListener) {
        this.logListener = logListener;
    }

    private void log(String message) {
        if (logListener != null) {
            logListener.accept(message);
        }
    }

    public synchronized FTPResponse connect(String host, int port) throws IOException {
        log("Trạng thái: Đang kết nối tới " + host + ":" + port + " ...");
        controlSocket = new Socket(host, port);
        // Đặt SO_TIMEOUT để tránh block vô hạn khi đọc response
        controlSocket.setSoTimeout(15000); // 15 giây timeout
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream()));
        connected = true;

        FTPResponse welcome = readResponse();
        log("Trạng thái: Kết nối thành công, đang chờ phản hồi từ máy chủ.");
        return welcome;
    }

    public synchronized FTPResponse sendCommand(String command, String argument) throws IOException {
        if (!connected) {
            throw new IllegalStateException("Chưa kết nối đến FTP Server.");
        }

        String fullCommand = (argument != null && !argument.trim().isEmpty())
                ? command + " " + argument
                : command;

        // P11: Ghi log lệnh gửi đi (ẩn mật khẩu khi gửi lệnh PASS)
        if ("PASS".equalsIgnoreCase(command)) {
            log("Lệnh: PASS ********");
        } else {
            log("Lệnh: " + fullCommand);
        }

        writer.write(fullCommand + "\r\n");
        writer.flush();

        return readResponse();
    }

    public synchronized FTPResponse readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            log("Lỗi: Kết nối bị ngắt từ Server.");
            return new FTPResponse(500, "Kết nối bị ngắt từ Server");
        }

        FTPResponse firstLineResponse = FTPResponseParser.parse(line);

        // Xử lý multi-line response (ví dụ: "230-..." đến "230 ")
        if (line.length() >= 4 && line.charAt(3) == '-') {
            StringBuilder fullMessage = new StringBuilder(firstLineResponse.getMessage());
            log("Phản hồi: " + line);
            while ((line = reader.readLine()) != null) {
                fullMessage.append("\n").append(line);
                log("Phản hồi: " + line);
                if (line.matches("^" + firstLineResponse.getCode() + " .*")) {
                    break;
                }
            }
            return new FTPResponse(firstLineResponse.getCode(), fullMessage.toString());
        }

        log("Phản hồi: " + line);
        return firstLineResponse;
    }

    public synchronized void disconnect() {
        try {
            if (connected) {
                sendCommand(FTPCommand.QUIT, null);
            }
        } catch (IOException ignored) {
        } finally {
            closeQuietly();
            log("Trạng thái: Đã ngắt kết nối khỏi máy chủ.");
        }
    }

    private void closeQuietly() {
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        try { if (writer != null) writer.close(); } catch (IOException ignored) {}
        try { if (controlSocket != null) controlSocket.close(); } catch (IOException ignored) {}
        connected = false;
    }

    public boolean isConnected() {
        return connected && controlSocket != null && !controlSocket.isClosed();
    }
}
