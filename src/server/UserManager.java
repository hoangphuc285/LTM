package server;

import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public class UserManager {
    private final Properties fallbackUsers = new Properties();
    private boolean isDatabaseConnected = false;

    public UserManager(String configPath) {
        // 1. Thử kết nối và khởi tạo MySQL Database
        isDatabaseConnected = DBConnection.initializeDatabase();

        // 2. Nạp file cấu hình dự phòng (tìm ở nhiều vị trí)
        loadFallbackUsers(configPath);
    }

    private void loadFallbackUsers(String preferredPath) {
        String[] potentialPaths = {
                preferredPath,
                "config/users.conf",
                "src/server/config/users.conf",
                "../config/users.conf"
        };

        boolean loaded = false;
        for (String p : potentialPaths) {
            if (p == null) continue;
            File file = new File(p);
            if (file.exists()) {
                try (FileInputStream fis = new FileInputStream(file)) {
                    fallbackUsers.load(fis);
                    loaded = true;
                    break;
                } catch (Exception ignored) {}
            }
        }

        if (!loaded) {
            // Mặc định nạp 2 user cơ bản trong bộ nhớ nếu không tìm thấy file nào
            fallbackUsers.setProperty("user01", "123456");
            fallbackUsers.setProperty("user02", "123456");
        }
    }

    public boolean isDatabaseConnected() {
        return isDatabaseConnected;
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
        } catch (SQLException ignored) {}

        // Fallback kiểm tra cấu hình dự phòng
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
        } catch (SQLException ignored) {}

        // Fallback kiểm tra cấu hình dự phòng
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

