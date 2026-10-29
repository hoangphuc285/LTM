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
            // =========================================================
            // NHÓM LỆNH XÁC THỰC
            // =========================================================
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
                    gui.updateClientUser(clientIp, currentUser);
                    gui.log("Client [" + clientIp + "] đăng nhập thành công tài khoản: " + currentUser);
                    sendResponse(FTPResponse.R_230_LOGGED_IN);
                } else {
                    gui.log("Client [" + clientIp + "] đăng nhập thất bại.");
                    sendResponse(FTPResponse.R_530_AUTH_FAILED);
                }
                break;

            // =========================================================
            // NHÓM LỆNH THÔNG TIN / TIỆN ÍCH
            // =========================================================
            case "SYST":
                // BUG FIX: Thêm lệnh SYST – nhiều FTP client gửi ngay sau login
                sendResponse("215 UNIX Type: L8");
                break;

            case "NOOP":
                // BUG FIX: Thêm lệnh NOOP – giữ kết nối sống (keep-alive)
                sendResponse("200 NOOP ok.");
                break;

            case "TYPE":
                // Chấp nhận cả ASCII (A) lẫn Binary (I)
                sendResponse("200 Type set to " + argument);
                break;

            case "PWD":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                sendResponse(FTPResponse.formatPWD(currentPath));
                break;

            // =========================================================
            // NHÓM LỆNH THƯ MỤC
            // =========================================================
            case "CWD":
                // BUG FIX: Thêm case CWD – ClientSession trước đây thiếu, gây lỗi khi MainFrame gọi CWD
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                if (argument == null || argument.isEmpty()) {
                    sendResponse("550 Failed to change directory.");
                    break;
                }
                String newPath;
                if (argument.equals("/")) {
                    newPath = "/";
                } else if (argument.startsWith("/")) {
                    // Đường dẫn tuyệt đối
                    newPath = argument;
                } else {
                    // Đường dẫn tương đối
                    newPath = currentPath.equals("/")
                            ? "/" + argument
                            : currentPath + "/" + argument;
                }
                // Chuẩn hoá: loại bỏ dấu // trùng lặp
                newPath = newPath.replaceAll("/+", "/");
                File targetCwd = getAbsoluteFile(newPath);
                if (targetCwd.exists() && targetCwd.isDirectory()) {
                    currentPath = newPath;
                    sendResponse("250 Directory successfully changed to " + currentPath);
                } else {
                    sendResponse("550 Failed to change directory: not found.");
                }
                break;

            case "CDUP":
                // BUG FIX: Thêm case CDUP – ClientSession trước đây thiếu, gây lỗi khi MainFrame gọi CDUP
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                if (currentPath.equals("/")) {
                    sendResponse("550 Already at root directory.");
                } else {
                    int lastSlash = currentPath.lastIndexOf('/');
                    currentPath = (lastSlash <= 0) ? "/" : currentPath.substring(0, lastSlash);
                    sendResponse("200 Directory successfully changed.");
                }
                break;

            case "MKD":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File newDir = getFileInCurrentDir(argument);
                if (newDir.mkdirs()) {
                    gui.log("Client [" + clientIp + "] đã tạo thư mục: " + argument);
                    gui.refreshUserFiles(currentUser);
                    sendResponse("257 \"" + argument + "\" directory created.");
                } else {
                    sendResponse("550 Create directory failed.");
                }
                break;

            case "RMD":
                // BUG FIX: Dùng deleteRecursively() – Java's delete() chỉ xóa được thư mục rỗng
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File dirToDelete = getFileInCurrentDir(argument);
                if (dirToDelete.exists() && dirToDelete.isDirectory() && deleteRecursively(dirToDelete)) {
                    gui.log("Client [" + clientIp + "] đã xóa thư mục: " + argument);
                    gui.refreshUserFiles(currentUser);
                    sendResponse("250 Directory removed.");
                } else {
                    sendResponse("550 Remove directory failed.");
                }
                break;

            // =========================================================
            // NHÓM LỆNH TRUYỀN FILE
            // =========================================================
            case "PASV":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                if (passiveServerSocket != null && !passiveServerSocket.isClosed()) {
                    passiveServerSocket.close();
                }
                passiveServerSocket = new ServerSocket(0); // Lấy cổng ngẫu nhiên
                int pasvPort = passiveServerSocket.getLocalPort();
                int p1 = pasvPort / 256;
                int p2 = pasvPort % 256;
                String serverIp = clientSocket.getLocalAddress().getHostAddress();
                String formattedIp = serverIp.replace('.', ',');
                sendResponse("227 Entering Passive Mode (" + formattedIp + "," + p1 + "," + p2 + ")");
                break;

            case "SIZE":
                // BUG FIX: Thêm lệnh SIZE – client cần biết kích thước file để tính % tiến trình download
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File sizeFile = getFileInCurrentDir(argument);
                if (sizeFile.exists() && sizeFile.isFile()) {
                    sendResponse("213 " + sizeFile.length());
                } else {
                    sendResponse("550 File not found.");
                }
                break;

            case "LIST":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                sendResponse("150 Opening ASCII mode data connection for file list.");
                try (Socket dataSocket = passiveServerSocket.accept();
                     BufferedWriter dataWriter = new BufferedWriter(new OutputStreamWriter(dataSocket.getOutputStream()))) {
                    // BUG FIX: Dùng getAbsoluteFile(currentPath) thay vì ghép tay – đúng với mọi sub-directory
                    File listDir = getAbsoluteFile(currentPath);
                    File[] files = listDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            // P3: Thêm lastModified (epoch ms) vào giao thức LIST
                            // Định dạng: name;size;isDir;lastModified
                            dataWriter.write(f.getName() + ";" + f.length() + ";" + f.isDirectory() + ";" + f.lastModified() + "\r\n");
                        }
                    }
                    dataWriter.flush();
                }
                sendResponse("226 Transfer complete.");
                break;

            case "RETR":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File downloadFile = getFileInCurrentDir(argument);
                if (!downloadFile.exists() || !downloadFile.isFile()) {
                    sendResponse("550 File not found.");
                    break;
                }
                // Server gửi 150 TRƯỚC, sau đó client đọc 150 rồi mới đọc data
                sendResponse("150 Opening BINARY mode data connection for " + argument + " (" + downloadFile.length() + " bytes).");
                try (Socket dataSocket = passiveServerSocket.accept();
                     FileInputStream fis = new FileInputStream(downloadFile);
                     OutputStream os = dataSocket.getOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, len);
                    }
                    os.flush();
                }
                gui.log("Client [" + clientIp + "] đã tải xuống tập tin: " + argument);
                DBConnection.logTransfer(currentUser, clientIp, "DOWNLOAD", argument, downloadFile.length());
                sendResponse("226 Transfer complete.");
                break;

            case "STOR":
                if (!isLoggedIn) { sendResponse(FTPResponse.R_530_NOT_LOGGED_IN); break; }
                File uploadFile = getFileInCurrentDir(argument);
                // Tạo thư mục cha nếu chưa tồn tại
                if (uploadFile.getParentFile() != null && !uploadFile.getParentFile().exists()) {
                    uploadFile.getParentFile().mkdirs();
                }
                // Server gửi 150 TRƯỚC, sau đó client đọc 150 rồi mới ghi data lên socket
                sendResponse("150 Ok to send data.");
                try (Socket dataSocket = passiveServerSocket.accept();
                     InputStream is = dataSocket.getInputStream();
                     FileOutputStream fos = new FileOutputStream(uploadFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.flush();
                }
                gui.log("Client [" + clientIp + "] đã tải lên tập tin: " + argument);
                gui.refreshUserFiles(currentUser);
                DBConnection.logTransfer(currentUser, clientIp, "UPLOAD", argument, uploadFile.length());
                sendResponse("226 Transfer complete.");
                break;

            // =========================================================
            // NHÓM LỆNH THAO TÁC FILE
            // =========================================================
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
                File fileToRename = getFileInCurrentDir(argument);
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
                File srcFile = getFileInCurrentDir(renameFromPath);
                File destFile = getFileInCurrentDir(argument);
                if (srcFile.renameTo(destFile)) {
                    gui.log("Client [" + clientIp + "] đã đổi tên: " + renameFromPath + " -> " + argument);
                    gui.refreshUserFiles(currentUser);
                    sendResponse("250 File renamed successfully.");
                } else {
                    sendResponse("550 Rename failed.");
                }
                renameFromPath = null;
                break;

            default:
                // BUG FIX: Thêm default case – trả 500 cho lệnh không nhận dạng được thay vì bỏ qua im lặng
                sendResponse(FTPResponse.R_500_UNKNOWN + ": " + command);
                break;
        }
    }

    private void sendResponse(String response) throws IOException {
        writer.write(response + "\r\n");
        writer.flush();
    }

    /**
     * BUG FIX: Lấy File theo đường dẫn tuyệt đối bên trong user home.
     * Dùng cho LIST, CWD, CDUP để hoạt động đúng với mọi sub-directory.
     */
    private File getAbsoluteFile(String path) {
        File userHome = fileManager.getUserHomeDir(currentUser);
        String relativePath = path;
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        return relativePath.isEmpty() ? userHome : new File(userHome, relativePath);
    }

    /**
     * Lấy File trong thư mục hiện tại dựa theo tên file/thư mục (argument).
     */
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

    /**
     * BUG FIX: Xóa đệ quy thư mục (kể cả khi không rỗng).
     * Java's File.delete() chỉ xóa được thư mục rỗng.
     */
    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursively(child)) {
                        return false;
                    }
                }
            }
        }
        return file.delete();
    }

    private void closeSession() {
        try {
            if (passiveServerSocket != null && !passiveServerSocket.isClosed()) passiveServerSocket.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
        } catch (IOException ignored) {
        } finally {
            if (gui != null) {
                gui.removeClient(clientIp);
                gui.log("Client [" + clientIp + "] đã ngắt kết nối.");
            }
        }
    }
}
