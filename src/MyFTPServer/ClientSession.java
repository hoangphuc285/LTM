package MyFTPServer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class ClientSession implements Runnable {
    private final Socket clientSocket;
    private final UserManager userManager;
    private final FileManager fileManager;

    private BufferedReader reader;
    private BufferedWriter writer;

    private String pendingUser = null;
    private String currentUser = null;
    private boolean isLoggedIn = false;
    private String currentPath = "/";

    public ClientSession(Socket socket, UserManager userManager, FileManager fileManager) {
        this.clientSocket = socket;
        this.userManager = userManager;
        this.fileManager = fileManager;
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            // Gửi Banner chào mừng khi vừa kết nối
            sendResponse(FTPResponse.R_220_WELCOME);

            String line;
            while ((line = reader.readLine()) != null) {
                FTPCommandParser parsed = new FTPCommandParser(line);
                String cmd = parsed.getCommand();
                String arg = parsed.getArgument();

                System.out.println("[Client " + clientSocket.getInetAddress() + "]: " + line);

                if ("QUIT".equals(cmd)) {
                    sendResponse(FTPResponse.R_221_GOODBYE);
                    break;
                }

                handleCommand(cmd, arg);
            }
        } catch (IOException e) {
            System.err.println("[Session Error]: " + e.getMessage());
        } finally {
            closeSession();
        }
    }

    private void handleCommand(String command, String argument) throws IOException {
        switch (command) {
            case "USER":
                if (userManager.userExists(argument)) {
                    pendingUser = argument;
                    sendResponse(FTPResponse.R_331_NEED_PASS + argument);
                } else {
                    sendResponse(FTPResponse.R_530_AUTH_FAILED);
                }
                break;

            case "PASS":
                if (pendingUser != null && userManager.authenticate(pendingUser, argument)) {
                    currentUser = pendingUser;
                    isLoggedIn = true;
                    pendingUser = null;
                    sendResponse(FTPResponse.R_230_LOGGED_IN);
                } else {
                    sendResponse(FTPResponse.R_530_AUTH_FAILED);
                }
                break;

            case "PWD":
                if (!isLoggedIn) {
                    sendResponse(FTPResponse.R_530_NOT_LOGGED_IN);
                } else {
                    sendResponse(FTPResponse.formatPWD(currentPath));
                }
                break;

            default:
                sendResponse(FTPResponse.R_500_UNKNOWN);
                break;
        }
    }

    private void sendResponse(String response) throws IOException {
        writer.write(response + "\r\n");
        writer.flush();
    }

    private void closeSession() {
        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }
        } catch (IOException ignored) {}
    }
}