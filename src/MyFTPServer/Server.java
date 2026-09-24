package MyFTPServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {
    // Dùng cổng 2121 để tránh cần quyền Administrator/Root khi chạy ứng dụng
    private static final int PORT = 2121;
    private static final String CONFIG_PATH = "config/users.conf";
    private static final String STORAGE_PATH = "storage";

    public static void main(String[] args) {
        UserManager userManager = new UserManager(CONFIG_PATH);
        FileManager fileManager = new FileManager(STORAGE_PATH);

        System.out.println("=== MY FTP SERVER IS STARTING ===");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server đang lắng nghe tại cổng " + PORT + "...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Có Client mới kết nối từ: " + clientSocket.getRemoteSocketAddress());

                // Mở một Thread riêng cho mỗi Client kết nối
                ClientSession session = new ClientSession(clientSocket, userManager, fileManager);
                new Thread(session).start();
            }
        } catch (IOException e) {
            System.err.println("Lỗi Server: " + e.getMessage());
        }
    }
}