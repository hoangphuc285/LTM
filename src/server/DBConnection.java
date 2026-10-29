package server;

import java.sql.*;

/**
 * Quản lý kết nối MySQL (XAMPP root, không mật khẩu)
 * Tự động khởi tạo Database và Bảng nếu chưa có!
 */
public class DBConnection {
    private static final String HOST = "localhost";
    private static final int PORT = 3306;
    private static final String DB_NAME = "ftp_server_db";
    private static final String USER = "root";
    private static final String PASSWORD = ""; // XAMPP mặc định không có mật khẩu

    private static boolean initialized = false;
    private static String statusMessage = "Chưa kết nối";

    public static String getStatusMessage() {
        return statusMessage;
    }

    /**
     * Tự động khởi tạo CSDL và các Bảng khi khởi động Server
     * @return true nếu kết nối và khởi tạo MySQL thành công
     */
    public static synchronized boolean initializeDatabase() {
        if (initialized) return true;

        try {
            // Nạp Driver MySQL
            Class.forName("com.mysql.cj.jdbc.Driver");

            // 1. Kết nối tạm vào MySQL Server để tạo Database nếu chưa có
            String serverUrl = "jdbc:mysql://" + HOST + ":" + PORT + "/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
            try (Connection conn = DriverManager.getConnection(serverUrl, USER, PASSWORD);
                 Statement stmt = conn.createStatement()) {

                stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DB_NAME + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            }

            // 2. Kết nối vào Database vừa tạo để tạo bảng users và transfer_logs
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {

                // Tạo bảng users
                String createUsersTable = "CREATE TABLE IF NOT EXISTS users ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "username VARCHAR(50) NOT NULL UNIQUE, "
                        + "password VARCHAR(255) NOT NULL, "
                        + "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ");";
                stmt.executeUpdate(createUsersTable);

                // Tạo bảng transfer_logs để ghi nhật ký truyền tải file
                String createLogsTable = "CREATE TABLE IF NOT EXISTS transfer_logs ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY, "
                        + "username VARCHAR(50) NOT NULL, "
                        + "client_ip VARCHAR(50), "
                        + "action_type ENUM('UPLOAD', 'DOWNLOAD') NOT NULL, "
                        + "file_name VARCHAR(255) NOT NULL, "
                        + "file_size BIGINT DEFAULT 0, "
                        + "transfer_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                        + ");";
                stmt.executeUpdate(createLogsTable);

                // Kiểm tra nếu bảng users chưa có dữ liệu thì chèn 2 tài khoản mẫu
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.executeUpdate("INSERT INTO users (username, password) VALUES "
                            + "('user01', '123456'), "
                            + "('user02', '123456')");
                }
            }

            initialized = true;
            statusMessage = "Đã kết nối CSDL MySQL (ftp_server_db)";
            return true;
        } catch (Exception e) {
            initialized = false;
            statusMessage = "Chưa bật MySQL trong XAMPP hoặc sai cổng (" + e.getMessage() + ")";
            return false;
        }
    }

    /**
     * Lấy kết nối tới Database ftp_server_db
     */
    public static Connection getConnection() throws SQLException {
        String dbUrl = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB_NAME
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(dbUrl, USER, PASSWORD);
    }

    /**
     * Ghi lại lịch sử truyền tải file (Upload / Download) vào bảng transfer_logs
     */
    public static void logTransfer(String username, String clientIp, String actionType, String fileName, long fileSize) {
        String sql = "INSERT INTO transfer_logs (username, client_ip, action_type, file_name, file_size) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username != null ? username : "anonymous");
            ps.setString(2, clientIp != null ? clientIp : "");
            ps.setString(3, actionType);
            ps.setString(4, fileName);
            ps.setLong(5, fileSize);
            ps.executeUpdate();
        } catch (Exception e) {
            System.err.println("[MySQL Log Error] " + e.getMessage());
        }
    }
}

