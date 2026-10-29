package client.service;

import client.network.FTPConnection;
import client.network.FTPResponse;
import java.io.IOException;

public class DirectoryService {
    private final FTPConnection connection;

    public DirectoryService(FTPConnection connection) {
        this.connection = connection;
    }

    public boolean createDirectory(String dirName) throws IOException {
        FTPResponse res = connection.sendCommand("MKD", dirName);
        return res.isSuccess();
    }

    public boolean deleteDirectory(String dirName) throws IOException {
        FTPResponse res = connection.sendCommand("RMD", dirName);
        return res.isSuccess();
    }

    public boolean deleteFile(String fileName) throws IOException {
        FTPResponse res = connection.sendCommand("DELE", fileName);
        return res.isSuccess();
    }

    public boolean rename(String oldName, String newName) throws IOException {
        FTPResponse res1 = connection.sendCommand("RNFR", oldName);
        if (res1.getCode() == 350) {
            FTPResponse res2 = connection.sendCommand("RNTO", newName);
            return res2.isSuccess();
        }
        return false;
    }

    public boolean changeDirectory(String path) throws IOException {
        FTPResponse res = connection.sendCommand("CWD", path);
        return res.isSuccess();
    }
}

