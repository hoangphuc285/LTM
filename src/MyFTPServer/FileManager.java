package MyFTPServer;

import java.io.File;

public class FileManager {
    private final String baseStoragePath;

    public FileManager(String baseStoragePath) {
        this.baseStoragePath = baseStoragePath;
    }

    public File getUserHomeDir(String username) {
        File dir = new File(baseStoragePath, username);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}