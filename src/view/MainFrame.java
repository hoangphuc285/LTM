package view;

import model.FTPFile;
import model.TransferTask;
import network.FTPCommand;
import network.FTPConnection;
import network.FTPResponse;
import service.DirectoryService;
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
    // Thêm thuộc tính này trong lớp MainFrame
    private DirectoryService directoryService;



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
        // 1. Sự kiện nhấn nút "Lên thư mục cha"
        remoteFilePanel.getBtnUp().addActionListener(e -> {
            new Thread(() -> {
                try {
                    FTPResponse res = mainConnection.sendCommand("CDUP", null);
                    if (res.isSuccess()) {
                        refreshRemoteFiles();
                    } else {
                        JOptionPane.showMessageDialog(this, "Đã ở thư mục gốc!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }).start();
        });
        // 2. Sự kiện Nhấp đôi chuột (Double Click) vào dòng trong Bảng File để Mở thư mục
        remoteFilePanel.getFileTable().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) { // Double click
                    if (remoteFilePanel.isSelectedDirectory()) {
                        String folderName = remoteFilePanel.getSelectedFileName();
                        new Thread(() -> {
                            try {
                                FTPResponse res = mainConnection.sendCommand("CWD", folderName);
                                if (res.isSuccess()) {
                                    refreshRemoteFiles();
                                } else {
                                    JOptionPane.showMessageDialog(MainFrame.this, "Không thể truy cập thư mục!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                                }
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }).start();
                    }
                }
            }
        });
        // --- 1. SỰ KIỆN NÚT TẠO THƯ MỤC (MKD) ---
        remoteFilePanel.getBtnNewFolder().addActionListener(e -> {
            String folderName = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới:", "Tạo Thư Mục", JOptionPane.PLAIN_MESSAGE);
            if (folderName != null && !folderName.trim().isEmpty()) {
                new Thread(() -> {
                    try {
                        if (directoryService.createDirectory(folderName.trim())) {
                            refreshRemoteFiles();
                        } else {
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(this, "Tạo thư mục thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }
        });

// --- 2. SỰ KIỆN NÚT XÓA (DELE / RMD) ---
        remoteFilePanel.getBtnDelete().addActionListener(e -> {
            String selected = remoteFilePanel.getSelectedFileName();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn file hoặc thư mục cần xóa!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean isDir = remoteFilePanel.isSelectedDirectory();
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Bạn có chắc chắn muốn xóa " + (isDir ? "thư mục" : "tập tin") + ": \"" + selected + "\"?",
                    "Xác nhận xóa",
                    JOptionPane.YES_NO_OPTION
            );

            if (confirm == JOptionPane.YES_OPTION) {
                new Thread(() -> {
                    try {
                        boolean success = isDir ? directoryService.deleteDirectory(selected)
                                : directoryService.deleteFile(selected);
                        if (success) {
                            refreshRemoteFiles();
                        } else {
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(this, "Xóa thất bại! (Có thể thư mục không rỗng)", "Lỗi", JOptionPane.ERROR_MESSAGE));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }
        });

// --- 3. SỰ KIỆN NÚT ĐỔI TÊN (RNFR / RNTO) ---
        // Sự kiện nút Đổi Tên
        remoteFilePanel.getBtnRename().addActionListener(e -> {
            // Kiểm tra nếu chưa kết nối / chưa khởi tạo Service
            if (directoryService == null || mainConnection == null || !mainConnection.isConnected()) {
                JOptionPane.showMessageDialog(this, "Vui lòng kết nối đến Server trước!", "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String selected = remoteFilePanel.getSelectedFileName();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn mục cần đổi tên!");
                return;
            }

            String newName = JOptionPane.showInputDialog(this, "Nhập tên mới:", selected);
            if (newName != null && !newName.trim().isEmpty() && !newName.equals(selected)) {
                new Thread(() -> {
                    try {
                        if (directoryService.rename(selected, newName.trim())) {
                            refreshRemoteFiles();
                        } else {
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(this, "Đổi tên thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE));
                        }
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }).start();
            }
        });
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
                    // Khởi tạo sau khi người dùng đăng nhập FTP thành công (trong hàm handleConnectDisconnect):
                    directoryService = new DirectoryService(mainConnection);
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
            if (isRefreshing) return;
            isRefreshing = true;
        }

        new Thread(() -> {
            try {
                // Lấy đường dẫn hiện tại từ Server bằng PWD
                FTPResponse pwdRes = mainConnection.sendCommand("PWD", null);
                if (pwdRes.isSuccess()) {
                    String msg = pwdRes.getMessage();
                    int firstQuote = msg.indexOf('"');
                    int lastQuote = msg.lastIndexOf('"');
                    if (firstQuote != -1 && lastQuote > firstQuote) {
                        String currentPwd = msg.substring(firstQuote + 1, lastQuote);
                        SwingUtilities.invokeLater(() -> remoteFilePanel.setCurrentPath(currentPwd));
                    }
                }

                // Cập nhật danh sách file
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