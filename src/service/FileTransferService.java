package service;

import model.FTPFile;
import model.TransferStatus;
import model.TransferTask;
import network.FTPCommand;
import network.FTPConnection;
import network.FTPResponse;

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

    // 1. Mở Kênh dữ liệu ở chế độ Passive Mode (PASV)
    private Socket openDataSocket() throws IOException {
        FTPResponse response = connection.sendCommand(FTPCommand.PASV, null);
        if (!response.isSuccess()) {
            throw new IOException("Không thể bật chế độ PASV: " + response.getMessage());
        }

        // Parse chuỗi PASV trả về: 227 Entering Passive Mode (127,0,0,1,195,80)
        Pattern pattern = Pattern.compile("\\((\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)\\)");
        Matcher matcher = pattern.matcher(response.getMessage());

        if (!matcher.find()) {
            throw new IOException("Định dạng phản hồi PASV không hợp lệ:" + response.getMessage());
        }

        String host = matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3) + "." + matcher.group(4);
        int port = Integer.parseInt(matcher.group(5)) * 256 + Integer.parseInt(matcher.group(6));

        return new Socket(host, port);
    }

    // Chuyển chế độ truyền sang Binary (TYPE I)
    public void setBinaryMode() throws IOException {
        connection.sendCommand(FTPCommand.TYPE, "I");
    }

    // 2. Lấy danh sách file/thư mục (LIST)
    public List<FTPFile> listFiles() throws IOException {
        Socket dataSocket = openDataSocket();
        connection.sendCommand("LIST", null);

        List<FTPFile> fileList = new ArrayList<>();
        try (BufferedReader dataReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()))) {
            String line;
            while ((line = dataReader.readLine()) != null) {
                // Parse đơn giản dạng: FILENAME;SIZE;TYPE (xử lý từ server)
                String[] parts = line.split(";");
                if (parts.length >= 3) {
                    String name = parts[0];
                    long size = Long.parseLong(parts[1]);
                    boolean isDir = Boolean.parseBoolean(parts[2]);
                    fileList.add(new FTPFile(name, size, isDir));
                }
            }
        } finally {
            dataSocket.close();
        }

        // Đọc phản hồi hoàn tất 226 từ Control Connection
        connection.readResponse();
        return fileList;
    }

    // 3. Tải file từ Server về máy cục bộ (RETR)
    public boolean downloadFile(String remoteFileName, File localDestination) throws IOException {
        setBinaryMode();
        Socket dataSocket = openDataSocket();

        connection.sendCommand("RETR", remoteFileName);

        try (InputStream in = dataSocket.getInputStream();
             FileOutputStream out = new FileOutputStream(localDestination)) {

            byte[] buffer = new byte[4096];
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

    // 4. Upload file từ máy cục bộ lên Server (STOR)
    public boolean uploadFile(File localFile, String remoteFileName) throws IOException {
        if (!localFile.exists()) {
            throw new FileNotFoundException("File nguồn không tồn tại: " + localFile.getAbsolutePath());
        }

        setBinaryMode();
        Socket dataSocket = openDataSocket();

        connection.sendCommand("STOR", remoteFileName);

        try (FileInputStream in = new FileInputStream(localFile);
             OutputStream out = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[4096];
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
    // Bổ sung vào src/service/FileTransferService.java

    public boolean uploadFileWithProgress(TransferTask task) throws IOException {
        task.setStatus(TransferStatus.IN_PROGRESS);
        File localFile = task.getLocalFile();
        task.setTotalBytes(localFile.length());

        setBinaryMode();
        Socket dataSocket = openDataSocket();

        connection.sendCommand("STOR", task.getRemotePath());

        long startTime = System.currentTimeMillis();
        long totalRead = 0;

        try (FileInputStream in = new FileInputStream(localFile);
             OutputStream out = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[8192]; // Buffer 8KB
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                // Kiểm tra nếu tác vụ bị hủy ngang
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
        Socket dataSocket = openDataSocket();

        connection.sendCommand("RETR", task.getRemotePath());

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