package MyFTPServer;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    private final int port;
    private final ServerGUI gui;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    private static final String CONFIG_PATH = "config/users.conf";
    private static final String STORAGE_PATH = "storage";

    public Server(int port, ServerGUI gui) {
        this.port = port;
        this.gui = gui;
    }

    public void start() {
        UserManager userManager = new UserManager(CONFIG_PATH);
        FileManager fileManager = new FileManager(STORAGE_PATH);
//        File testFile = new File(CONFIG_PATH);
//        System.out.println("Đường dẫn tuyệt đối Java đang tìm: " + testFile.getAbsolutePath());
//        System.out.println("File có thực sự tồn tại không?: " + testFile.exists());
        try {
            serverSocket = new ServerSocket(port);
            isRunning = true;
            gui.log("FTP Server đã khởi động thành công trên cổng " + port);

            while (isRunning && !serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                String clientIp = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();

                gui.log("Client kết nối mới từ IP: " + clientIp);
                gui.addClient(clientIp);

                ClientSession session = new ClientSession(clientSocket, userManager, fileManager, gui, clientIp);
                new Thread(session).start();
            }
        } catch (IOException e) {
            if (isRunning) {
                gui.log("Lỗi Server: " + e.getMessage());
            }
        }
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            gui.log("FTP Server đã dừng hoạt động.");
        } catch (IOException e) {
            gui.log("Lỗi khi đóng Server: " + e.getMessage());
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}