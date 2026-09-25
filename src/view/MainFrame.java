package view;

import model.FTPFile;
import model.TransferTask;
import network.FTPCommand;
import network.FTPConnection;
import service.FileTransferService;
import service.TransferManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;

public class MainFrame extends JFrame {
    private final LoginPanel loginPanel = new LoginPanel();
    private final LocalFilePanel localFilePanel = new LocalFilePanel();
    private final RemoteFilePanel remoteFilePanel = new RemoteFilePanel();
    private final TransferPanel transferPanel = new TransferPanel();

    private FTPConnection mainConnection;
    private FileTransferService transferService;
    private TransferManager transferManager;
    private boolean isRefreshing = false;

    public MainFrame() {
        setTitle("Java FTP Client - Phase 4 (Simple Swing GUI)");
        setSize(850, 550);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        // Phân chia layout
        add(loginPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localFilePanel, remoteFilePanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);

        add(transferPanel, BorderLayout.SOUTH);

        // Đăng ký sự kiện nút Đăng nhập
        loginPanel.setConnectAction(e -> handleConnectDisconnect());

        // Đăng ký sự kiện nút Upload
        localFilePanel.getBtnUpload().addActionListener(e -> handleUpload());

        // Đăng ký sự kiện nút Download
        remoteFilePanel.getBtnDownload().addActionListener(e -> handleDownload());
    }

    private void handleConnectDisconnect() {
        if (mainConnection != null && mainConnection.isConnected()) {
            // Thực hiện Ngắt kết nối
            mainConnection.disconnect();
            if (transferManager != null) transferManager.shutdown();
            loginPanel.setConnectedState(false);
            remoteFilePanel.updateFileList(null);
            transferPanel.updateProgress(0, "Đã ngắt kết nối.");
        } else {
            // Thực hiện Kết nối
            try {
                String host = loginPanel.getHost();
                int port = loginPanel.getPort();
                String user = loginPanel.getUsername();
                String pass = loginPanel.getPassword();

                mainConnection = new FTPConnection();
                mainConnection.connect(host, port);
                mainConnection.sendCommand(FTPCommand.USER, user);
                var passResp = mainConnection.sendCommand(FTPCommand.PASS, pass);

                if (passResp.isSuccess()) {
                    transferService = new FileTransferService(mainConnection);
                    transferManager = new TransferManager(2, host, port, user, pass);

                    loginPanel.setConnectedState(true);
                    transferPanel.updateProgress(0, "Đăng nhập thành công!");
                    refreshRemoteFiles();
                } else {
                    JOptionPane.showMessageDialog(this, "Đăng nhập thất bại: " + passResp.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi kết nối: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void refreshRemoteFiles() {
        synchronized (this) {
            if (isRefreshing) {
                return; // Nếu đang có 1 luồng refresh danh sách file rồi thì bỏ qua các yêu cầu trùng
            }
            isRefreshing = true;
        }

        new Thread(() -> {
            try {
                List<FTPFile> files = transferService.listFiles();
                SwingUtilities.invokeLater(() -> remoteFilePanel.updateFileList(files));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                synchronized (this) {
                    isRefreshing = false;
                }
            }
        }).start();
    }

    private void handleUpload() {
        if (mainConnection == null || !mainConnection.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới FTP Server!");
            return;
        }

        File selectedFile = localFilePanel.getSelectedFile();
        if (selectedFile == null || selectedFile.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 file ở bảng Cục bộ để Upload!");
            return;
        }

        TransferTask task = new TransferTask("TASK-UP", TransferTask.Type.UPLOAD, selectedFile, selectedFile.getName());
        task.setListener(new TransferTask.TransferListener() {
            @Override
            public void onProgressUpdate(TransferTask task) {
                transferPanel.updateProgress((int) task.getProgressPercentage(),
                        String.format("Đang Upload %s: %.1f%% (%.1f KB/s)",
                                task.getLocalFile().getName(),
                                task.getProgressPercentage(),
                                task.getSpeedBytesPerSec() / 1024.0));
            }

            @Override
            public void onStatusChange(TransferTask task) {
                if (task.getStatus() == model.TransferStatus.COMPLETED) {
                    transferPanel.updateProgress(100, "Upload hoàn tất!");
                    refreshRemoteFiles();
                }
            }
        });

        transferManager.submitTask(task);
    }

    private void handleDownload() {
        if (mainConnection == null || !mainConnection.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới FTP Server!");
            return;
        }

        String remoteFileName = remoteFilePanel.getSelectedFileName();
        if (remoteFileName == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 file ở bảng Server để Download!");
            return;
        }

        File localDest = new File(System.getProperty("user.dir"), "downloaded_" + remoteFileName);
        TransferTask task = new TransferTask("TASK-DOWN", TransferTask.Type.DOWNLOAD, localDest, remoteFileName);

        task.setListener(new TransferTask.TransferListener() {
            @Override
            public void onProgressUpdate(TransferTask task) {
                transferPanel.updateProgress((int) task.getProgressPercentage(),
                        String.format("Đang Download %s: %.1f%% (%.1f KB/s)",
                                remoteFileName,
                                task.getProgressPercentage(),
                                task.getSpeedBytesPerSec() / 1024.0));
            }

            @Override
            public void onStatusChange(TransferTask task) {
                if (task.getStatus() == model.TransferStatus.COMPLETED) {
                    transferPanel.updateProgress(100, "Download hoàn tất!");
                    localFilePanel.loadDirectory(new File(System.getProperty("user.dir")));
                }
            }
        });

        transferManager.submitTask(task);
    }
}