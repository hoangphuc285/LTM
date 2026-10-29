package MyFTPServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public class UserManager {
    private final Properties fallbackUsers = new Properties();

    public UserManager(String configPath) {
        // Tự động khởi tạo MySQL Database & Table
        DBConnection.initializeDatabase();

        // Nạp file cấu hình dự phòng
        try (FileInputStream fis = new FileInputStream(configPath)) {
            fallbackUsers.load(fis);
        } catch (IOException e) {
            System.err.println("[Server Info] File users.conf không tồn tại hoặc không thể đọc: " + e.getMessage());
        }
    }

    /**
     * Kiểm tra username có tồn tại không (ưu tiên MySQL, fallback file cấu hình)
     */
    public boolean userExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return true;
            }
        } catch (SQLException e) {
            System.err.println("[MySQL Warning] Không thể kết nối CSDL, chuyển sang dùng users.conf: " + e.getMessage());
        }

        // Fallback kiểm tra file users.conf
        return fallbackUsers.containsKey(username);
    }

    /**
     * Xác thực thông tin đăng nhập (ưu tiên MySQL, fallback file cấu hình)
     */
    public boolean authenticate(String username, String password) {
        String sql = "SELECT 1 FROM users WHERE username = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return true;
            }
        } catch (SQLException e) {
            System.err.println("[MySQL Warning] Lỗi xác thực qua DB, chuyển sang users.conf: " + e.getMessage());
        }

        // Fallback kiểm tra file users.conf
        return fallbackUsers.containsKey(username) && fallbackUsers.getProperty(username).equals(password);
    }

    /**
     * Thêm tài khoản mới trực tiếp vào MySQL
     */
    public boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, password);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[MySQL Error] Không thể thêm tài khoản: " + e.getMessage());
            return false;
        }
    }
}
