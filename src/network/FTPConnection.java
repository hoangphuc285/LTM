package network;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class FTPConnection {
    private Socket controlSocket;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean connected = false;

    public synchronized FTPResponse connect(String host, int port) throws IOException {
        controlSocket = new Socket(host, port);
        reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
        writer = new BufferedWriter(new OutputStreamWriter(controlSocket.getOutputStream()));
        connected = true;

        return readResponse();
    }

    public synchronized FTPResponse sendCommand(String command, String argument) throws IOException {
        if (!connected) {
            throw new IllegalStateException("Chưa kết nối đến FTP Server.");
        }

        // Dọn dẹp các thông điệp thừa còn đọng lại trong bộ nhớ đệm (nếu có)
        clearPendingResponses();

        String fullCommand = (argument != null && !argument.trim().isEmpty())
                ? command + " " + argument
                : command;

        writer.write(fullCommand + "\r\n");
        writer.flush();

        return readResponse();
    }

    public synchronized FTPResponse readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return new FTPResponse(500, "Kết nối bị ngắt từ Server.");
        }

        FTPResponse firstLineResponse = FTPResponseParser.parse(line);

        if (line.length() >= 4 && line.charAt(3) == '-') {
            StringBuilder fullMessage = new StringBuilder(firstLineResponse.getMessage());
            while ((line = reader.readLine()) != null) {
                fullMessage.append("\n").append(line);
                if (line.matches("^" + firstLineResponse.getCode() + " .*")) {
                    break;
                }
            }
            return new FTPResponse(firstLineResponse.getCode(), fullMessage.toString());
        }

        return firstLineResponse;
    }

    private void clearPendingResponses() throws IOException {
        while (reader != null && reader.ready()) {
            reader.readLine();
        }
    }

    public synchronized void disconnect() {
        try {
            if (connected) {
                sendCommand(FTPCommand.QUIT, null);
            }
        } catch (IOException ignored) {
        } finally {
            closeQuietly();
        }
    }

    private void closeQuietly() {
        try { if (reader != null) reader.close(); } catch (IOException ignored) {}
        try { if (writer != null) writer.close(); } catch (IOException ignored) {}
        try { if (controlSocket != null) controlSocket.close(); } catch (IOException ignored) {}
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }
}