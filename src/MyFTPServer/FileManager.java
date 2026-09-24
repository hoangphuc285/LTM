package MyFTPServer;

import java.io.File;

public class FileManager {
    private final String baseStoragePath;

    public FileManager(String baseStoragePath) {
        this.baseStoragePath = baseStoragePath;
    }
    // Tạo đường dẫn dạng: MyFTPServer/storage/[username]
    public File getUserHomeDir(String username) {
        File dir = new File(baseStoragePath, username);
        if (!dir.exists()) {
            dir.mkdirs();// Tự động khởi tạo cây thư mục nếu chưa tồn tại
        }
        return dir;
    }
}