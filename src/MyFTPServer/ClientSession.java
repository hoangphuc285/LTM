package MyFTPServer;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ClientSession implements Runnable {
    private final Socket clientSocket;
    private final UserManager userManager;
    private final FileManager fileManager;
    private final ServerGUI gui;
    private final String clientIp;

    private BufferedReader reader;
    private BufferedWriter writer;

    private String pendingUser = null;
    private String currentUser = null;
    private boolean isLoggedIn = false;
    private String currentPath = "/";
    private String renameFromPath = null;

    // Socket phục vụ Kênh dữ liệu
    private ServerSocket passiveServerSocket = null;

    public ClientSession(Socket socket, UserManager userManager, FileManager fileManager, ServerGUI gui, String clientIp) {
        this.clientSocket = socket;
        this.userManager = userManager;
        this.fileManager = fileManager;
        this.gui = gui;
        this.clientIp = clientIp;
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

                gui.log("[Client " + clientIp + "]: " + line);

                if ("QUIT".equals(cmd)) {
                    sendResponse(FTPResponse.R_221_GOODBYE);
                    break;
                }

                handleCommand(cmd, arg);
            }
        } catch (IOException e) {
            gui.log("Lỗi phiên làm việc [" + clientIp + "]: " + e.getMessage());
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

                    // 2. Cập nhật bảng Client và ghi log Đăng nhập thành công
                    gui.updateClientUser(clientIp, currentUser);
                    gui.log("Client [" + clientIp + "] đăng nhập thành công tài khoản: " + currentUser);

                    sendResponse(FTPResponse.R_230_LOGGED_IN);
                } else {
                    gui.log("Client [" + clientIp + "] đăng nhập thất bại.");
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
                if (passiveServerSocket != null && !passiveServerSocket.isClosed()) {
                    passiveServerSocket.close();
                }
                passiveServerSocket = new ServerSocket(0); // Lấy cổng ngẫu nhiên
                int port = passiveServerSocket.getLocalPort();
                int p1 = port / 256;
                int p2 = port % 256;

                // Lấy IP mạng LAN thực tế của Server mà Client vừa kết nối tới
                String serverIp = clientSocket.getLocalAddress().getHostAddress();
                // Chuyển định dạng dấu chấm '.' thành dấu phẩy ',' theo chuẩn RFC 959 (ví dụ: 192.168.1.15 -> 192,168,1,15)
                String formattedIp = serverIp.replace('.', ',');

                sendResponse("227 Entering Passive Mode (" + formattedIp + "," + p1 + "," + p2 + ")");
                break;

            case "LIST":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                sendResponse("150 Opening ASCII mode data connection for file list.");

                try (Socket dataSocket = passiveServerSocket.accept();
                     BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream()))) {

                    File currentDir = new File(fileManager.getUserHomeDir(currentUser), currentPath.equals("/") ? "" : currentPath);
                    File[] files = currentDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            dataWriter.write(f.getName() + ";" + f.length() + ";" + f.isDirectory() + "\r\n");
                        }
                    }
                    dataWriter.flush();
                }
                sendResponse("226 Transfer complete.");
                break;

            case "RETR":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File downloadFile = getFileInCurrentDir(argument);
                if (!downloadFile.exists()) {
                    sendResponse("550 File not found.");
                    break;
                }
                sendResponse("150 Opening BINARY mode data connection for download.");

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
                gui.log("Client [" + clientIp + "] đã tải xuống tập tin: " + argument);
                sendResponse("226 Transfer complete.");
                break;

            case "STOR":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                sendResponse("150 Ok to send data.");

                File uploadFile = getFileInCurrentDir(argument);
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
                gui.log("Client [" + clientIp + "] đã tải lên tập tin: " + argument);
                gui.refreshUserFiles(currentUser);
                sendResponse("226 Transfer complete.");
                break;
            case "MKD":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File newDir = getFileInCurrentDir(argument);

                // Sử dụng mkdirs() thay vì mkdir() để tự tạo đủ cây thư mục
                if (newDir.mkdirs()) {
                    gui.log("Client [" + clientIp + "] đã tạo thư mục: " + argument);
                    gui.refreshUserFiles(currentUser);
                    sendResponse("257 \"" + argument + "\" directory created.");
                } else {
                    sendResponse("550 Create directory failed.");
                }
                break;

            case "RMD":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File dirToDelete = getFileInCurrentDir(argument);
                if (dirToDelete.exists() && dirToDelete.isDirectory() && dirToDelete.delete()) {
                    gui.log("Client [" + clientIp + "] đã xóa thư mục: " + argument);
                    gui.refreshUserFiles(currentUser);
                    sendResponse("250 Directory removed.");
                } else {
                    sendResponse("550 Remove directory failed.");
                }
                break;

            case "DELE":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File fileToDelete = getFileInCurrentDir(argument);
                if (fileToDelete.exists() && fileToDelete.isFile() && fileToDelete.delete()) {
                    gui.log("Client [" + clientIp + "] đã xóa tập tin: " + argument);
                    gui.refreshUserFiles(currentUser);
                    sendResponse("250 File deleted.");
                } else {
                    sendResponse("550 Delete file failed.");
                }
                break;
            case "RNFR":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File fileToRename = getFileInCurrentDir(argument); // Đã sửa: Tìm file trong thư mục hiện tại
                if (fileToRename.exists()) {
                    renameFromPath = argument;
                    sendResponse("350 Requested file action pending further information.");
                } else {
                    sendResponse("550 File not found.");
                }
                break;

            case "RNTO":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                if (renameFromPath == null) {
                    sendResponse("503 Bad sequence of commands.");
                    break;
                }
                File srcFile = getFileInCurrentDir(renameFromPath); // Đã sửa: Ghép đúng vị trí file cũ
                File destFile = getFileInCurrentDir(argument);      // Đã sửa: Ghép đúng vị trí tên mới

                if (srcFile.renameTo(destFile)) {
                    gui.log("Client [" + clientIp + "] đã đổi tên: " + renameFromPath + " -> " + argument);
                    gui.refreshUserFiles(currentUser);
                    sendResponse("250 File renamed successfully.");
                } else {
                    sendResponse("550 Rename failed.");
                }
                renameFromPath = null;
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
        } catch (IOException ignored) {
        }finally {
            if (gui != null) {
                gui.removeClient(clientIp);
                gui.log("Client [" + clientIp + "] đã ngắt kết nối.");
            }
        }
    }
    private File getFileInCurrentDir(String argument) {
        File userHome = fileManager.getUserHomeDir(currentUser);

        // Bóc tách dấu '/' ở đầu currentPath để tránh lỗi đường dẫn tuyệt đối trong Java
        String relativePath = currentPath;
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        // Bóc tách dấu '/' ở đầu argument
        String cleanArg = argument;
        while (cleanArg.startsWith("/")) {
            cleanArg = cleanArg.substring(1);
        }

        File targetDir = relativePath.isEmpty() ? userHome : new File(userHome, relativePath);
        return new File(targetDir, cleanArg);
    }
}