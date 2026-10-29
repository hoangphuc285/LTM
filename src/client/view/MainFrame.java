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
import java.util.ArrayList;
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

    // Lưu danh sách file remote hiện tại để kiểm tra trùng lặp
    private List<FTPFile> currentRemoteFiles = new ArrayList<>();

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

        // Đăng ký callback khi bấm Hủy tác vụ trong bảng Queue
        transferPanel.setOnCancelTaskCallback(taskId -> {
            if (transferManager != null) {
                transferManager.cancelTaskById(taskId);
                appendLog("Đã yêu cầu hủy tác vụ: " + taskId);
            }
        });

        loginPanel.setConnectAction(e -> handleConnectDisconnect());
        localFilePanel.getBtnUpload().addActionListener(e -> handleUpload());
        remoteFilePanel.getBtnDownload().addActionListener(e -> handleDownload());
        remoteFilePanel.setOnRefreshCallback(this::refreshRemoteFiles);

        // Đăng ký callback Upload/Download cả thư mục (Đệ quy)
        localFilePanel.setOnUploadFolderCallback(this::handleUploadFolder);
        remoteFilePanel.setOnDownloadFolderCallback(this::handleDownloadFolder);

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
                    } else {
                        // Nhấp đúp vào tập tin -> Download ngay
                        handleDownload();
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
                        transferManager  = new TransferManager(3, host, port, user, pass);

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
                this.currentRemoteFiles = (files != null) ? files : new ArrayList<>();
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

    // =========================================================
    // XỬ LÝ XUNG ĐỘT FILE (Trùng tên: Ghi đè, Tự đổi tên, Bỏ qua)
    // =========================================================
    private int promptFileConflict(String fileName, boolean isUpload) {
        String message = String.format("Tập tin \"%s\" đã tồn tại trên %s!\nBạn muốn xử lý thế nào?",
                fileName, isUpload ? "Máy chủ" : "Máy cục bộ");
        Object[] options = {"Ghi đè", "Tự đổi tên", "Bỏ qua", "Hủy"};
        return JOptionPane.showOptionDialog(
                this,
                message,
                "Xung đột tập tin",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]
        );
    }

    private File getUniqueLocalFile(File file) {
        if (!file.exists()) return file;
        String name = file.getName();
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int count = 1;
        File parent = file.getParentFile();
        File newFile;
        do {
            newFile = new File(parent, base + "(" + count + ")" + ext);
            count++;
        } while (newFile.exists());
        return newFile;
    }

    private String getUniqueRemoteName(String name) {
        boolean exists = currentRemoteFiles.stream().anyMatch(f -> f.getName().equalsIgnoreCase(name));
        if (!exists) return name;

        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }

        int count = 1;
        String newName;
        do {
            newName = base + "(" + count + ")" + ext;
            String check = newName;
            boolean duplicate = currentRemoteFiles.stream().anyMatch(f -> f.getName().equalsIgnoreCase(check));
            if (!duplicate) break;
            count++;
        } while (count < 1000);

        return newName;
    }

    // =========================================================
    // UPLOAD TẬP TIN
    // =========================================================
    private void handleUpload() {
        if (!isConnected()) return;

        File selectedFile = localFilePanel.getSelectedFile();
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 mục ở bảng Cục bộ để Upload!");
            return;
        }

        // Nếu người dùng chọn thư mục -> Hỏi upload cả thư mục
        if (selectedFile.isDirectory()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn đã chọn thư mục \"" + selectedFile.getName() + "\".\nBạn có muốn tải lên toàn bộ thư mục này không?",
                    "Tải lên thư mục", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                handleUploadFolder(selectedFile);
            }
            return;
        }

        String remoteTargetName = selectedFile.getName();
        boolean alreadyExists = currentRemoteFiles.stream().anyMatch(f -> f.getName().equalsIgnoreCase(selectedFile.getName()));

        if (alreadyExists) {
            int choice = promptFileConflict(selectedFile.getName(), true);
            if (choice == 0) {
                // Ghi đè: giữ nguyên remoteTargetName
            } else if (choice == 1) {
                // Tự đổi tên
                remoteTargetName = getUniqueRemoteName(selectedFile.getName());
                appendLog("Tự động đổi tên tệp upload thành: " + remoteTargetName);
            } else if (choice == 2) {
                // Bỏ qua
                appendLog("Bỏ qua tải lên tệp: " + selectedFile.getName());
                return;
            } else {
                // Hủy
                return;
            }
        }

        submitUploadTask(selectedFile, remoteTargetName);
    }

    private void submitUploadTask(File localFile, String remoteName) {
        String taskId = "UP-" + (taskCounter++);
        TransferTask task = new TransferTask(taskId, TransferTask.Type.UPLOAD, localFile, remoteName);
        transferPanel.enqueueTask(task);
        appendLog("Bắt đầu tải lên: " + localFile.getName() + " -> " + task.getRemotePath());

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
                }
            }
        });

        transferManager.submitTask(task);
    }

    // =========================================================
    // UPLOAD CẢ THƯ MỤC (ĐỆ QUY)
    // =========================================================
    private void handleUploadFolder(File folder) {
        if (!isConnected() || folder == null || !folder.isDirectory()) return;

        new Thread(() -> {
            try {
                appendLog("Chuẩn bị tải lên toàn bộ thư mục: " + folder.getName());
                uploadFolderRecursive(folder, folder.getName());
                SwingUtilities.invokeLater(this::refreshRemoteFiles);
            } catch (Exception e) {
                appendLog("Lỗi khi tải lên thư mục: " + e.getMessage());
            }
        }).start();
    }

    private void uploadFolderRecursive(File localDir, String remoteRelativePath) {
        try {
            // Tạo thư mục trên server
            directoryService.createDirectory(remoteRelativePath);
        } catch (Exception ignored) {}

        File[] children = localDir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isHidden()) continue;
            String childRemotePath = remoteRelativePath + "/" + child.getName();
            if (child.isDirectory()) {
                uploadFolderRecursive(child, childRemotePath);
            } else {
                submitUploadTask(child, childRemotePath);
            }
        }
    }

    // =========================================================
    // DOWNLOAD TẬP TIN
    // =========================================================
    private void handleDownload() {
        if (!isConnected()) return;

        String remoteFileName = remoteFilePanel.getSelectedFileName();
        if (remoteFileName == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 mục ở bảng Server để Download!");
            return;
        }

        // Nếu là thư mục -> Hỏi download cả thư mục
        if (remoteFilePanel.isSelectedDirectory()) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn đã chọn thư mục \"" + remoteFileName + "\".\nBạn có muốn tải về toàn bộ thư mục này không?",
                    "Tải về thư mục", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                handleDownloadFolder(remoteFileName);
            }
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
            int choice = promptFileConflict(localDest.getName(), false);
            if (choice == 0) {
                // Ghi đè: giữ nguyên localDest
            } else if (choice == 1) {
                // Tự đổi tên
                localDest = getUniqueLocalFile(localDest);
                appendLog("Tự động đổi tên tệp tải về thành: " + localDest.getName());
            } else if (choice == 2) {
                // Bỏ qua
                appendLog("Bỏ qua tải về tệp: " + remoteFileName);
                return;
            } else {
                // Hủy
                return;
            }
        }

        submitDownloadTask(remoteFileName, localDest);
    }

    private void submitDownloadTask(String remoteFileName, File localDest) {
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
                }
            }
        });

        transferManager.submitTask(task);
    }

    // =========================================================
    // DOWNLOAD CẢ THƯ MỤC (ĐỆ QUY)
    // =========================================================
    private void handleDownloadFolder(String remoteFolderName) {
        if (!isConnected() || remoteFolderName == null) return;

        JFileChooser chooser = new JFileChooser(currentLocalDownloadDir);
        chooser.setDialogTitle("Chọn thư mục lưu về cho: " + remoteFolderName);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        File targetParentDir = chooser.getSelectedFile();
        File localFolder = new File(targetParentDir, remoteFolderName);
        localFolder.mkdirs();

        new Thread(() -> {
            FTPConnection helperConn = new FTPConnection();
            try {
                helperConn.connect(loginPanel.getHost(), loginPanel.getPort());
                helperConn.sendCommand(FTPCommand.USER, loginPanel.getUsername());
                helperConn.sendCommand(FTPCommand.PASS, loginPanel.getPassword());

                // Chuyển vào thư mục cha remote hiện tại
                String currentRemotePwd = remoteFilePanel.getCurrentPath();
                if (!"/".equals(currentRemotePwd)) {
                    helperConn.sendCommand(FTPCommand.CWD, currentRemotePwd);
                }

                appendLog("Bắt đầu tải về toàn bộ thư mục: " + remoteFolderName);
                downloadFolderRecursive(helperConn, remoteFolderName, localFolder);
                appendLog("Đã xếp hàng đợi tải về thư mục: " + remoteFolderName);
            } catch (Exception e) {
                appendLog("Lỗi tải về thư mục đệ quy: " + e.getMessage());
            } finally {
                helperConn.disconnect();
            }
        }).start();
    }

    private void downloadFolderRecursive(FTPConnection conn, String remoteDirName, File localDir) {
        try {
            conn.sendCommand(FTPCommand.CWD, remoteDirName);
            FileTransferService helperTransfer = new FileTransferService(conn);
            List<FTPFile> subFiles = helperTransfer.listFiles();

            for (FTPFile item : subFiles) {
                if (item.isDirectory()) {
                    File subLocalDir = new File(localDir, item.getName());
                    subLocalDir.mkdirs();
                    downloadFolderRecursive(conn, item.getName(), subLocalDir);
                } else {
                    File localDest = new File(localDir, item.getName());
                    if (localDest.exists()) {
                        localDest = getUniqueLocalFile(localDest);
                    }
                    String remoteFilePath = remoteFilePanel.getCurrentPath().equals("/")
                            ? remoteDirName + "/" + item.getName()
                            : remoteFilePanel.getCurrentPath() + "/" + remoteDirName + "/" + item.getName();
                    submitDownloadTask(remoteFilePath, localDest);
                }
            }

            conn.sendCommand(FTPCommand.CDUP, null);
        } catch (Exception e) {
            appendLog("Lỗi duyệt thư mục remote: " + e.getMessage());
        }
    }
}
