package MyFTPServer;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class UserManager {
    private final Properties users = new Properties();

    public UserManager(String configPath) {
        try (FileInputStream fis = new FileInputStream(configPath)) {
            users.load(fis);
        } catch (IOException e) {
            System.err.println("[Server Error] Không thể tải file cấu hình người dùng: " + e.getMessage());
        }
    }

    public boolean userExists(String username) {
        return users.containsKey(username);
    }

    public boolean authenticate(String username, String password) {
        return userExists(username) && users.getProperty(username).equals(password);
    }
}