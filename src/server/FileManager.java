package server;

import java.io.File;

public class FileManager {
    private final String baseStoragePath;

    public FileManager(String baseStoragePath) {
        // Tìm vị trí hợp lý cho base storage
        File direct = new File(baseStoragePath);
        if (direct.exists()) {
            this.baseStoragePath = direct.getAbsolutePath();
        } else {
            File alt = new File("src/server/storage");
            if (alt.exists()) {
                this.baseStoragePath = alt.getAbsolutePath();
            } else {
                this.baseStoragePath = baseStoragePath;
            }
        }
    }

    // Tạo đường dẫn dạng: storage/[username]
    public File getUserHomeDir(String username) {
        File dir = new File(baseStoragePath, username);
        if (!dir.exists()) {
            dir.mkdirs(); // Tự động khởi tạo cây thư mục nếu chưa tồn tại
        }
        return dir;
    }
}

