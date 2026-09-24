package service;

import model.TransferStatus;
import model.TransferTask;
import network.FTPCommand;
import network.FTPConnection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransferManager {
    private final ExecutorService threadPool;
    private final List<TransferTask> taskList;
    private final String host;
    private final int port;
    private final String username;
    private final String password;

    public TransferManager(int maxConcurrentTransfers, String host, int port, String username, String password) {
        this.threadPool = Executors.newFixedThreadPool(maxConcurrentTransfers);
        this.taskList = Collections.synchronizedList(new ArrayList<>());
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    // Thêm tác vụ truyền file vào hàng đợi chạy ngầm
    public void submitTask(TransferTask task) {
        taskList.add(task);
        threadPool.submit(() -> executeTask(task));
    }

    private void executeTask(TransferTask task) {
        // Mỗi worker thread mở kết nối FTP riêng
        FTPConnection workerConnection = new FTPConnection();
        try {
            workerConnection.connect(host, port);
            workerConnection.sendCommand(FTPCommand.USER, username);
            workerConnection.sendCommand(FTPCommand.PASS, password);

            FileTransferService transferService = new FileTransferService(workerConnection);

            if (task.getType() == TransferTask.Type.UPLOAD) {
                transferService.uploadFileWithProgress(task);
            } else if (task.getType() == TransferTask.Type.DOWNLOAD) {
                transferService.downloadFileWithProgress(task);
            }
        } catch (Exception e) {
            task.setStatus(TransferStatus.FAILED);
            task.setErrorMessage(e.getMessage());
        } finally {
            workerConnection.disconnect();
        }
    }

    public void cancelTask(TransferTask task) {
        task.setStatus(TransferStatus.CANCELLED);
    }

    public List<TransferTask> getTaskList() {
        return taskList;
    }

    public void shutdown() {
        threadPool.shutdownNow();
    }
}