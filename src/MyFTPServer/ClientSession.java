package MyFTPServer;
import java.io.*;
import java.net.ServerSocket;
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

    // Socket phục vụ Kênh dữ liệu
    private ServerSocket passiveServerSocket = null;

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

            case "TYPE":
                sendResponse("200 Type set to " + argument);
                break;

            case "PASV":
                if (!isLoggedIn) {
                    sendResponse(FTPResponse.R_530_NOT_LOGGED_IN);
                    break;
                }
                // Mở ServerSocket trên 1 cổng ngẫu nhiên khả dụng (port 0)
                if (passiveServerSocket != null && !passiveServerSocket.isClosed()) {
                    passiveServerSocket.close();
                }
                passiveServerSocket = new ServerSocket(0);
                int port = passiveServerSocket.getLocalPort();
                int p1 = port / 256;
                int p2 = port % 256;

                sendResponse("227 Entering Passive Mode (127,0,0,1," + p1 + "," + p2 + ")");
                break;

            case "LIST":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                sendResponse("150 Opening ASCII mode data connection for file list.");

                try (Socket dataSocket = passiveServerSocket.accept();
                     BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream()))) {

                    File userHome = fileManager.getUserHomeDir(currentUser);
                    File[] files = userHome.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            // Định dạng tự định nghĩa: Tên;DungLượng;LàThưMục
                            dataWriter.write(f.getName() + ";" + f.length() + ";" + f.isDirectory() + "\r\n");
                        }
                    }
                    dataWriter.flush();
                }
                sendResponse("226 Transfer complete.");
                break;

            case "RETR":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                sendResponse("150 Opening BINARY mode data connection for download.");

                File downloadFile = new File(fileManager.getUserHomeDir(currentUser), argument);
                if (!downloadFile.exists()) {
                    sendResponse("550 File not found.");
                    break;
                }

                try (Socket dataSocket = passiveServerSocket.accept();
                     FileInputStream fis = new FileInputStream(downloadFile);
                     OutputStream os = dataSocket.getOutputStream()) {

                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                    os.flush();
                }
                sendResponse("226 Transfer complete.");
                break;

            case "STOR":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                sendResponse("150 Ok to send data.");

                File uploadFile = new File(fileManager.getUserHomeDir(currentUser), argument);
                try (Socket dataSocket = passiveServerSocket.accept();
                     InputStream is = dataSocket.getInputStream();
                     FileOutputStream fos = new FileOutputStream(uploadFile)) {

                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();
                }
                sendResponse("226 Transfer complete.");
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
            if (passiveServerSocket != null && !passiveServerSocket.isClosed()) passiveServerSocket.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException ignored) {}
    }
}