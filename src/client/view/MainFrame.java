package client.view;

import client.model.FTPFile;
import client.model.TransferStatus;
import client.model.TransferTask;
import client.network.FTPCommand;
import client.network.FTPConnection;
import client.network.FTPResponse;
import client.service.DirectoryService;
import client.service.FileTransferService;
import client.service.TransferManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainFrame extends JFrame {
    private final LoginPanel loginPanel = new LoginPanel();
    private final LocalFilePanel localFilePanel = new LocalFilePanel();
    private final RemoteFilePanel remoteFilePanel = new RemoteFilePanel();
    private final TransferPanel transferPanel = new TransferPanel();

    private final JTextArea logArea = new JTextArea();

    private FTPConnection mainConnection;
    private FileTransferService transferService;
    private TransferManager transferManager;
    private boolean isRefreshing = false;
    private DirectoryService directoryService;

    private int taskCounter = 1;
    private File currentLocalDownloadDir = new File(System.getProperty("user.dir"));

    public MainFrame() {
        setTitle("Java FTP Client (FileZilla Style)");
        setSize(1050, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(250, 250, 250));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder(" Nhật ký trao đổi giao thức (Message Log) "));
        logScrollPane.setPreferredSize(new Dimension(0, 110));

        JPanel northPanel = new JPanel(new BorderLayout(3, 3));
        northPanel.add(loginPanel, BorderLayout.NORTH);
        northPanel.add(logScrollPane, BorderLayout.CENTER);
        add(northPanel, BorderLayout.NORTH);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localFilePanel, remoteFilePanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);

        add(transferPanel, BorderLayout.SOUTH);

        loginPanel.setConnectAction(e -> handleConnectDisconnect());
        localFilePanel.getBtnUpload().addActionListener(e -> handleUpload());
        remoteFilePanel.getBtnDownload().addActionListener(e -> handleDownload());
        remoteFilePanel.setOnRefreshCallback(this::refreshRemoteFiles);

        remoteFilePanel.setOnNavigateCallback(targetPath -> {
            if (!isConnected()) return;
            new Thread(() -> {
                try {
                    FTPResponse res = mainConnection.sendCommand("CWD", targetPath);
                    if (res.isSuccess()) {
                        refreshRemoteFiles();
                    } else {
                        SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(MainFrame.this,
                                "Không thể chuyển đến thư mục: " + targetPath, "Lỗi", JOptionPane.ERROR_MESSAGE));
                    }
                } catch (Exception ex) {
                    showConnectionError(ex);
                }
            }).start();
        });

        remoteFilePanel.getBtnUp().addActionListener(e -> {
            if (!isConnected()) return;
            new Thread(() -> {
                try {
                    FTPResponse res = mainConnection.sendCommand("CDUP", null);
                    if (res.isSuccess()) {
                        refreshRemoteFiles();
                    } else {
                        SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Đã ở thư mục gốc!", "Thông báo", JOptionPane.INFORMATION_MESSAGE));
                    }
                } catch (Exception ex) {
                    showConnectionError(ex);
                }
            }).start();
        });

        remoteFilePanel.getFileTable().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (remoteFilePanel.isSelectedDirectory()) {
                        String folderName = remoteFilePanel.getSelectedFileName();
                        if (folderName == null) return;
                        new Thread(() -> {
                            try {
                                FTPResponse res = mainConnection.sendCommand("CWD", folderName);
                                if (res.isSuccess()) {
                                    refreshRemoteFiles();
                                } else {
                                    SwingUtilities.invokeLater(() ->
                                        JOptionPane.showMessageDialog(MainFrame.this, "Không thể truy cập thư mục!", "Lỗi", JOptionPane.ERROR_MESSAGE));
                                }
                            } catch (Exception ex) {
                                showConnectionError(ex);
                            }
                        }).start();
                    }
                }
            }
        });

        remoteFilePanel.getBtnNewFolder().addActionListener(e -> {
            if (!isConnected()) return;
            String folderName = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới:", "Tạo Thư Mục Máy Chủ", JOptionPane.PLAIN_MESSAGE);
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
                        showConnectionError(ex);
                    }
                }).start();
            }
        });

        remoteFilePanel.getBtnDelete().addActionListener(e -> {
            if (!isConnected()) return;
            String selected = remoteFilePanel.getSelectedFileName();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn file hoặc thư mục cần xóa!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            boolean isDir = remoteFilePanel.isSelectedDirectory();
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Bạn có chắc chắn muốn xóa " + (isDir ? "thư mục" : "tập tin") + ": \"" + selected + "\"?",
                    "Xác nhận xóa trên máy chủ",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION) {
                new Thread(() -> {
                    try {
                        boolean success = isDir
                                ? directoryService.deleteDirectory(selected)
                                : directoryService.deleteFile(selected);
                        if (success) {
                            refreshRemoteFiles();
                        } else {
                            SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this, "Xóa thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE));
                        }
                    } catch (Exception ex) {
                        showConnectionError(ex);
                    }
                }).start();
            }
        });

        remoteFilePanel.getBtnRename().addActionListener(e -> {
            if (!isConnected()) return;
            String selected = remoteFilePanel.getSelectedFileName();
            if (selected == null) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn mục cần đổi tên!");
                return;
            }
            String newName = JOptionPane.showInputDialog(this, "Nhập tên mới:", selected);
            if (newName != null && !newName.trim().isEmpty() && !newName.trim().equals(selected)) {
                new Thread(() -> {
                    try {
                        if (directoryService.rename(selected, newName.trim())) {
                            refreshRemoteFiles();
                        } else {
                            SwingUtilities.invokeLater(() ->
                                JOptionPane.showMessageDialog(this, "Đổi tên thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE));
                        }
                    } catch (Exception ex) {
                        showConnectionError(ex);
                    }
                }).start();
            }
        });

        appendLog("Sẵn sàng. Nhập thông tin và bấm 'Kết nối nhanh' để bắt đầu.");
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private boolean isConnected() {
        if (mainConnection == null || !mainConnection.isConnected()) {
            JOptionPane.showMessageDialog(this, "Vui lòng kết nối đến FTP Server trước!", "Chưa kết nối", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        return true;
    }

    private void showConnectionError(Exception ex) {
        appendLog("Lỗi kết nối ngoại lệ: " + ex.getMessage());
        ex.printStackTrace();
        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(this,
                "Lỗi kết nối: " + ex.getMessage() + "\nVui lòng kiểm tra lại kết nối.",
                "Lỗi kết nối",
                JOptionPane.ERROR_MESSAGE));
    }

    private void handleConnectDisconnect() {
        if (mainConnection != null && mainConnection.isConnected()) {
            new Thread(() -> {
                mainConnection.disconnect();
                if (transferManager != null) transferManager.shutdown();
                SwingUtilities.invokeLater(() -> {
                    loginPanel.setConnectedState(false);
                    remoteFilePanel.updateFileList(null);
                    remoteFilePanel.setCurrentPath("/");
                    transferPanel.updateProgress(0, "Đã ngắt kết nối.");
                });
            }).start();
        } else {
            String host = loginPanel.getHost();
            int port    = loginPanel.getPort();
            String user = loginPanel.getUsername();
            String pass = loginPanel.getPassword();

            loginPanel.setConnectingState(true);
            transferPanel.updateProgress(0, "Đang kết nối đến " + host + ":" + port + " ...");

            new Thread(() -> {
                try {
                    FTPConnection conn = new FTPConnection();
                    conn.setLogListener(this::appendLog);

                    conn.connect(host, port);
                    conn.sendCommand(FTPCommand.USER, user);
                    FTPResponse passResp = conn.sendCommand(FTPCommand.PASS, pass);

                    if (passResp.isSuccess()) {
                        mainConnection  = conn;
                        transferService = new FileTransferService(mainConnection);
                        directoryService = new DirectoryService(mainConnection);
                        transferManager  = new TransferManager(2, host, port, user, pass);

                        SwingUtilities.invokeLater(() -> {
                            loginPanel.setConnectedState(true);
                            transferPanel.updateProgress(0, "Đã kết nối đến " + host + " với tài khoản " + user);
                        });
                        refreshRemoteFiles();
                    } else {
                        conn.disconnect();
                        SwingUtilities.invokeLater(() -> {
                            loginPanel.setConnectingState(false);
                            JOptionPane.showMessageDialog(this,
                                "Đăng nhập thất bại!\nMã lỗi: " + passResp.getCode() + " – " + passResp.getMessage(),
                                "Xác thực thất bại", JOptionPane.ERROR_MESSAGE);
                            transferPanel.updateProgress(0, "Đăng nhập thất bại.");
                        });
                    }
                } catch (Exception ex) {
                    appendLog("Lỗi kết nối: " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        loginPanel.setConnectingState(false);
                        JOptionPane.showMessageDialog(this,
                            "Không thể kết nối!\n" + ex.getMessage()
                            + "\n\nKiểm tra lại:\n  • Host/Port có đúng không?\n  • Server có đang chạy không?",
                            "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                        transferPanel.updateProgress(0, "Kết nối thất bại.");
                    });
                }
            }).start();
        }
    }

    private void refreshRemoteFiles() {
        synchronized (this) {
            if (isRefreshing) return;
            isRefreshing = true;
        }

        new Thread(() -> {
            try {
                FTPResponse pwdRes = mainConnection.sendCommand("PWD", null);
                if (pwdRes.isSuccess()) {
                    String msg = pwdRes.getMessage();
                    int firstQuote = msg.indexOf('"');
                    int lastQuote  = msg.lastIndexOf('"');
                    if (firstQuote != -1 && lastQuote > firstQuote) {
                        String currentPwd = msg.substring(firstQuote + 1, lastQuote);
                        SwingUtilities.invokeLater(() -> remoteFilePanel.setCurrentPath(currentPwd));
                    }
                }

                List<FTPFile> files = transferService.listFiles();
                SwingUtilities.invokeLater(() -> remoteFilePanel.updateFileList(files));
            } catch (Exception e) {
                appendLog("Lỗi khi tải danh sách tệp từ máy chủ: " + e.getMessage());
                e.printStackTrace();
            } finally {
                synchronized (this) {
                    isRefreshing = false;
                }
            }
        }).start();
    }

    private void handleUpload() {
        if (!isConnected()) return;

        File selectedFile = localFilePanel.getSelectedFile();
        if (selectedFile == null || selectedFile.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 file ở bảng Cục bộ để Upload!");
            return;
        }

        String taskId = "UP-" + (taskCounter++);
        TransferTask task = new TransferTask(taskId, TransferTask.Type.UPLOAD, selectedFile, selectedFile.getName());
        transferPanel.enqueueTask(task);
        appendLog("Bắt đầu tải lên: " + selectedFile.getName() + " -> " + task.getRemotePath());

        task.setListener(new TransferTask.TransferListener() {
            @Override
            public void onProgressUpdate(TransferTask t) {
                transferPanel.updateTask(t);
                transferPanel.updateProgress(
                    (int) t.getProgressPercentage(),
                    String.format("Đang Upload %s: %.1f%% (%s) [ETA: %s]",
                        t.getLocalFile().getName(),
                        t.getProgressPercentage(),
                        FTPFile.formatSpeed(t.getSpeedBytesPerSec()),
                        t.getETA()));
            }

            @Override
            public void onStatusChange(TransferTask t) {
                transferPanel.updateTask(t);
                if (t.getStatus() == TransferStatus.COMPLETED) {
                    appendLog("Tải lên hoàn tất: " + t.getLocalFile().getName());
                    transferPanel.updateProgress(100, "Upload hoàn tất: " + t.getLocalFile().getName());
                    refreshRemoteFiles();
                } else if (t.getStatus() == TransferStatus.FAILED) {
                    appendLog("Tải lên thất bại: " + t.getLocalFile().getName() + " (Lỗi: " + t.getErrorMessage() + ")");
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(MainFrame.this,
                            "Upload thất bại: " + t.getErrorMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
                }
            }
        });

        transferManager.submitTask(task);
    }

    private void handleDownload() {
        if (!isConnected()) return;

        String remoteFileName = remoteFilePanel.getSelectedFileName();
        if (remoteFileName == null || remoteFilePanel.isSelectedDirectory()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 file (không phải thư mục) ở bảng Server để Download!");
            return;
        }

        JFileChooser chooser = new JFileChooser(currentLocalDownloadDir);
        chooser.setDialogTitle("Chọn nơi lưu file: " + remoteFileName);
        chooser.setSelectedFile(new File(currentLocalDownloadDir, remoteFileName));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File localDest = chooser.getSelectedFile();
        currentLocalDownloadDir = localDest.getParentFile();

        if (localDest.exists()) {
            int overwrite = JOptionPane.showConfirmDialog(this,
                "File \"" + localDest.getName() + "\" đã tồn tại. Ghi đè?",
                "Xác nhận ghi đè", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (overwrite != JOptionPane.YES_OPTION) return;
        }

        String taskId = "DOWN-" + (taskCounter++);
        TransferTask task = new TransferTask(taskId, TransferTask.Type.DOWNLOAD, localDest, remoteFileName);
        transferPanel.enqueueTask(task);
        appendLog("Bắt đầu tải xuống: " + remoteFileName + " -> " + localDest.getAbsolutePath());

        task.setListener(new TransferTask.TransferListener() {
            @Override
            public void onProgressUpdate(TransferTask t) {
                transferPanel.updateTask(t);
                transferPanel.updateProgress(
                    (int) t.getProgressPercentage(),
                    String.format("Đang Download %s: %.1f%% (%s) [ETA: %s]",
                        remoteFileName,
                        t.getProgressPercentage(),
                        FTPFile.formatSpeed(t.getSpeedBytesPerSec()),
                        t.getETA()));
            }

            @Override
            public void onStatusChange(TransferTask t) {
                transferPanel.updateTask(t);
                if (t.getStatus() == TransferStatus.COMPLETED) {
                    appendLog("Tải xuống hoàn tất: " + remoteFileName);
                    transferPanel.updateProgress(100, "Download hoàn tất: " + remoteFileName);
                    localFilePanel.loadDirectory(localDest.getParentFile());
                } else if (t.getStatus() == TransferStatus.FAILED) {
                    appendLog("Tải xuống thất bại: " + remoteFileName + " (Lỗi: " + t.getErrorMessage() + ")");
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(MainFrame.this,
                            "Download thất bại: " + t.getErrorMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE));
                }
            }
        });

        transferManager.submitTask(task);
    }
}

