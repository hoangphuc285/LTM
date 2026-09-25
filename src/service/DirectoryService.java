package service;

import network.FTPConnection;
import network.FTPResponse;
import java.io.IOException;

public class DirectoryService {
    private final FTPConnection connection;

    public DirectoryService(FTPConnection connection) {
        this.connection = connection;
    }

    /**
     * Tạo thư mục mới trên Server (Lệnh MKD)
     */
    public boolean createDirectory(String dirName) throws IOException {
        FTPResponse res = connection.sendCommand("MKD", dirName);
        return res.isSuccess();
    }

    /**
     * Xóa thư mục trên Server (Lệnh RMD)
     */
    public boolean deleteDirectory(String dirName) throws IOException {
        FTPResponse res = connection.sendCommand("RMD", dirName);
        return res.isSuccess();
    }

    /**
     * Xóa file trên Server (Lệnh DELE)
     */
    public boolean deleteFile(String fileName) throws IOException {
        FTPResponse res = connection.sendCommand("DELE", fileName);
        return res.isSuccess();
    }

    /**
     * Đổi tên file hoặc thư mục (Lệnh RNFR -> RNTO)
     */
    public boolean rename(String oldName, String newName) throws IOException {
        FTPResponse res1 = connection.sendCommand("RNFR", oldName);
        if (res1.getCode() == 350) { // 350: Requested file action pending further information
            FTPResponse res2 = connection.sendCommand("RNTO", newName);
            return res2.isSuccess();
        }
        return false;
    }

    /**
     * Chuyển thư mục làm việc (Lệnh CWD)
     */
    public boolean changeDirectory(String path) throws IOException {
        FTPResponse res = connection.sendCommand("CWD", path);
        return res.isSuccess();
    }
}