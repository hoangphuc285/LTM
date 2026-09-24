import network.FTPCommand;
import network.FTPConnection;
import network.FTPResponse;

public class Main {
    public static void main(String[] args) {
        // Có thể thay thế bằng FTP Server nội bộ (VD: FileZilla Server) hoặc public test server
        String host = "127.0.0.1";
        int port = 2121;
        String username = "user01";
        String password = "123456";

        FTPConnection connection = new FTPConnection();

        try {
            System.out.println("=== THỬ NGHIỆM GIAI ĐOẠN 1: FTP CLIENT CƠ BẢN ===");

            // 1. Mở kết nối Socket
            FTPResponse response = connection.connect(host, port);
            System.out.println("Connect Response: " + response);

            // 2. Gửi USER
            response = connection.sendCommand(FTPCommand.USER, username);
            System.out.println("USER Response: " + response);

            // 3. Gửi PASS
            response = connection.sendCommand(FTPCommand.PASS, password);
            System.out.println("PASS Response: " + response);

            // 4. Kiểm tra thư mục làm việc hiện tại
            if (response.isSuccess()) {
                response = connection.sendCommand(FTPCommand.PWD, null);
                System.out.println("PWD Response: " + response);
            }

        } catch (Exception e) {
            System.err.println("Lỗi kết nối: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 5. Ngắt kết nối
            connection.disconnect();
            System.out.println("Đã đóng kết nối FTP thành công.");
        }
    }
}