package client.service;

import client.model.FTPFile;
import client.model.TransferStatus;
import client.model.TransferTask;
import client.network.FTPCommand;
import client.network.FTPConnection;
import client.network.FTPResponse;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileTransferService {
    private final FTPConnection connection;

    public FileTransferService(FTPConnection connection) {
        this.connection = connection;
    }

    private Socket openDataSocket() throws IOException {
        FTPResponse response = connection.sendCommand(FTPCommand.PASV, null);
        if (!response.isSuccess()) {
            throw new IOException("Không thể bật chế độ PASV: " + response.getMessage());
        }

        Pattern pattern = Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");
        Matcher matcher = pattern.matcher(response.getMessage());

        if (!matcher.find()) {
            throw new IOException("Định dạng phản hồi PASV không hợp lệ: " + response.getMessage());
        }

        String host = matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) + "." + matcher.group(4);
        int port = Integer.parseInt(matcher.group(5)) * 256 + Integer.parseInt(matcher.group(6));

        return new Socket(host, port);
    }

    public void setBinaryMode() throws IOException {
        connection.sendCommand(FTPCommand.TYPE, "I");
    }

    public long getRemoteFileSize(String remoteFileName) {
        try {
            FTPResponse res = connection.sendCommand("SIZE", remoteFileName);
            if (res.getCode() == 213) {
                return Long.parseLong(res.getMessage().trim());
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public List<FTPFile> listFiles() throws IOException {
        Socket dataSocket = openDataSocket();
        connection.sendCommand("LIST", null);

        List<FTPFile> fileList = new ArrayList<>();
        try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()))) {
            String line;
            while ((line = dataReader.readLine()) != null) {
                String[] parts = line.split(";");
                if (parts.length >= 3) {
                    String name      = parts[0];
                    long size        = Long.parseLong(parts[1]);
                    boolean isDir    = Boolean.parseBoolean(parts[2]);
                    long lastMod     = parts.length >= 4 ? Long.parseLong(parts[3]) : 0L;
                    fileList.add(new FTPFile(name, size, isDir, lastMod));
                }
            }
        } finally {
            dataSocket.close();
        }

        connection.readResponse();
        return fileList;
    }

    public boolean downloadFile(String remoteFileName, File localDestination) throws IOException {
        setBinaryMode();
        Socket dataSocket = openDataSocket();

        FTPResponse initiateResponse = connection.sendCommand("RETR", remoteFileName);

        if (!initiateResponse.isPreliminary()) {
            dataSocket.close();
            return false;
        }

        try (InputStream in = dataSocket.getInputStream();
             FileOutputStream out = new FileOutputStream(localDestination)) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } finally {
            dataSocket.close();
        }

        FTPResponse finalResponse = connection.readResponse();
        return finalResponse.isSuccess();
    }

    public boolean uploadFile(File localFile, String remoteFileName) throws IOException {
        if (!localFile.exists()) {
            throw new FileNotFoundException("File nguồn không tồn tại: " + localFile.getAbsolutePath());
        }

        setBinaryMode();
        Socket dataSocket = openDataSocket();

        FTPResponse initiateResponse = connection.sendCommand("STOR", remoteFileName);

        if (!initiateResponse.isPreliminary()) {
            dataSocket.close();
            return false;
        }

        try (FileInputStream in = new FileInputStream(localFile);
             OutputStream out = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
        } finally {
            dataSocket.close();
        }

        FTPResponse finalResponse = connection.readResponse();
        return finalResponse.isSuccess();
    }

    public boolean uploadFileWithProgress(TransferTask task) throws IOException {
        task.setStatus(TransferStatus.IN_PROGRESS);
        File localFile = task.getLocalFile();
        task.setTotalBytes(localFile.length());

        setBinaryMode();
        Socket dataSocket = openDataSocket();

        FTPResponse initiateResponse = connection.sendCommand("STOR", task.getRemotePath());

        if (!initiateResponse.isPreliminary()) {
            dataSocket.close();
            task.setStatus(TransferStatus.FAILED);
            task.setErrorMessage("Server từ chối: " + initiateResponse.getMessage());
            return false;
        }

        long startTime = System.currentTimeMillis();
        long totalRead = 0;

        try (FileInputStream in = new FileInputStream(localFile);
             OutputStream out = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (task.getStatus() == TransferStatus.CANCELLED) {
                    throw new IOException("Tác vụ truyền file bị hủy bởi người dùng.");
                }

                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                long elapsedTime = System.currentTimeMillis() - startTime;
                double speed = elapsedTime > 0 ? (totalRead * 1000.0 / elapsedTime) : 0;
                task.updateProgress(totalRead, speed);
            }
            out.flush();
        } finally {
            dataSocket.close();
        }

        FTPResponse response = connection.readResponse();
        if (response.isSuccess()) {
            task.setStatus(TransferStatus.COMPLETED);
            return true;
        } else {
            task.setStatus(TransferStatus.FAILED);
            task.setErrorMessage(response.getMessage());
            return false;
        }
    }

    public boolean downloadFileWithProgress(TransferTask task) throws IOException {
        task.setStatus(TransferStatus.IN_PROGRESS);
        setBinaryMode();

        long remoteSize = getRemoteFileSize(task.getRemotePath());
        if (remoteSize > 0) {
            task.setTotalBytes(remoteSize);
        }

        Socket dataSocket = openDataSocket();

        FTPResponse initiateResponse = connection.sendCommand("RETR", task.getRemotePath());

        if (!initiateResponse.isPreliminary()) {
            dataSocket.close();
            task.setStatus(TransferStatus.FAILED);
            task.setErrorMessage("Server từ chối: " + initiateResponse.getMessage());
            return false;
        }

        long startTime = System.currentTimeMillis();
        long totalRead = 0;

        try (InputStream in = dataSocket.getInputStream();
             FileOutputStream out = new FileOutputStream(task.getLocalFile())) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                if (task.getStatus() == TransferStatus.CANCELLED) {
                    throw new IOException("Tác vụ truyền file bị hủy bởi người dùng.");
                }

                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                long elapsedTime = System.currentTimeMillis() - startTime;
                double speed = elapsedTime > 0 ? (totalRead * 1000.0 / elapsedTime) : 0;
                task.updateProgress(totalRead, speed);
            }
            out.flush();
        } finally {
            dataSocket.close();
        }

        FTPResponse response = connection.readResponse();
        if (response.isSuccess()) {
            task.setStatus(TransferStatus.COMPLETED);
            return true;
        } else {
            task.setStatus(TransferStatus.FAILED);
            task.setErrorMessage(response.getMessage());
            return false;
        }
    }
}

