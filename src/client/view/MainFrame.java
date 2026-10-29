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
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {
    private final LoginPanel loginPanel = new LoginPanel();
    private final ProtocolLogPanel logPanel = new ProtocolLogPanel();
    private final LocalFilePanel localFilePanel = new LocalFilePanel();
    private final RemoteFilePanel remoteFilePanel = new RemoteFilePanel();
    private final TransferPanel transferPanel = new TransferPanel();

    // Thanh trạng thái phía dưới
    private final JLabel lblConnectionStatus = new JLabel("Trạng thái: Chưa kết nối");
    private final JLabel lblTransferMode = new JLabel("Chế độ: Bị động (Passive) | Kiểu: Nhị phân (Binary)");
    private final JToggleButton btnThemeToggle = new JToggleButton("Giao dien Toi");

    private FTPConnection mainConnection;
    private FileTransferService transferService;
    private TransferManager transferManager;
    private boolean isRefreshing = false;
    private DirectoryService directoryService;

    private int taskCounter = 1;
    private File currentLocalDownloadDir = new File(System.getProperty("user.dir"));
    private List<FTPFile> currentRemoteFiles = new ArrayList<>();
    private javax.swing.Timer keepAliveTimer;

    public MainFrame() {
        setTitle("Java FTP Client (FileZilla Style)");
        setSize(1100, 750);
        setMinimumSize(new Dimension(880, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(4, 4));

        // Phía trên: Quickconnect + Protocol Log
        JPanel northPanel = new JPanel(new BorderLayout(3, 3));
        northPanel.add(loginPanel, BorderLayout.NORTH);
        northPanel.add(logPanel, BorderLayout.CENTER);
        add(northPanel, BorderLayout.NORTH);

        // Khung Local và Remote
        JSplitPane splitPanels = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, localFilePanel, remoteFilePanel);
        splitPanels.setResizeWeight(0.5);

        // Phân tách khung File và khung Queue
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, splitPanels, transferPanel);
        mainSplit.setResizeWeight(0.72);
        add(mainSplit, BorderLayout.CENTER);

        // Thanh trạng thái dưới cùng
        JPanel statusBar = new JPanel(new BorderLayout(10, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        statusBar.add(lblConnectionStatus, BorderLayout.WEST);

        JPanel statusEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        statusEast.add(lblTransferMode);
        statusEast.add(btnThemeToggle);
        statusBar.add(statusEast, BorderLayout.EAST);
        add(statusBar, BorderLayout.SOUTH);

        setupGlobalShortcuts();
        initListeners();

        // Keep-alive định kỳ bằng lệnh NOOP
        keepAliveTimer = new javax.swing.Timer(30000, e -> {
            if (mainConnection != null && mainConnection.isConnected()) {
                new Thread(() -> {
                    try {
                        synchronized (mainConnection) {
                            if (mainConnection.isConnected() && !isRefreshing) {
                                mainConnection.sendCommand(FTPCommand.NOOP, null);
                            }
                        }
                    } catch (Exception ignored) {}
                }).start();
            }
        });

        appendLog("Sẵn sàng. Nhập thông tin và bấm 'Kết nối nhanh' để bắt đầu.");
    }

    private void setupGlobalShortcuts() {
        // Ctrl+L: Nhảy con trỏ tới thanh đường dẫn Remote
        getRootPane().registerKeyboardAction(e -> remoteFilePanel.focusPathField(),
                KeyStroke.getKeyStroke(KeyEvent.VK_L, KeyEvent.CTRL_DOWN_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void initListeners() {
        btnThemeToggle.setSelected(AppColors.isDark());
        btnThemeToggle.setText(AppColors.isDark() ? "Giao dien Sang" : "Giao dien Toi");
        btnThemeToggle.addActionListener(e -> {
            boolean dark = btnThemeToggle.isSelected();
            btnThemeToggle.setText(dark ? "Giao dien Sang" : "Giao dien Toi");
            ThemeManager.toggle(dark);
            logPanel.applyTheme();
            SwingUtilities.updateComponentTreeUI(this);
        });

        transferPanel.setOnCancelTaskCallback(taskId -> {
            if (transferManager != null) {
                transferManager.cancelTaskById(taskId);
                appendLog("Đã yêu cầu hủy tác vụ: " + taskId);
            }
        });

        loginPanel.setConnectAction(e -> handleConnectDisconnect());

        loginPanel.getBtnSiteManager().addActionListener(e -> {
            SiteManagerDialog dialog = new SiteManagerDialog(this);
            dialog.setOnConnectCallback(site -> {
                loginPanel.applySite(site);
                loginPanel.triggerConnect();
            });
            dialog.setVisible(true);
        });

        localFilePanel.getBtnUpload().addActionListener(e -> handleUpload());
        remoteFilePanel.getBtnDownload().addActionListener(e -> handleDownload());
        remoteFilePanel.setOnRefreshCallback(this::refreshRemoteFiles);

        localFilePanel.setOnUploadFolderCallback(this::handleUploadFolder);
        remoteFilePanel.setOnDownloadFolderCallback(this::handleDownloadFolder);

        remoteFilePanel.setOnDropLocalFilesCallback(this::handleDropFilesToRemote);
        localFilePanel.setOnDropRemoteItemCallback(this::handleDropRemoteItemToLocal);

        remoteFilePanel.setOnViewEditCallback(this::handleRemoteViewEdit);
        remoteFilePanel.setOnPropertiesCallback(this::handleRemoteProperties);
        remoteFilePanel.setOnSearchCallback(this::handleOpenSearchDialog);

        remoteFilePanel.getBtnBookmarks().addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Đường dẫn hiện tại: " + remoteFilePanel.getCurrentPath()
                    + "\nĐã lưu vào danh sách đánh dấu nhanh!", "Dấu trang", JOptionPane.INFORMATION_MESSAGE);
        });

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
            if (selected == null || "..".equals(selected)) {
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
            if (selected == null || "..".equals(selected)) {
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
    }

    public void appendLog(String message) {
        logPanel.appendLog(message);
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
        SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(this,
                        "Lỗi kết nối: " + ex.getMessage() + "\nVui lòng kiểm tra lại trạng thái Server.",
                        "Lỗi kết nối",
                        JOptionPane.ERROR_MESSAGE));
    }

    private void handleConnectDisconnect() {
        if (keepAliveTimer != null) keepAliveTimer.stop();
        if (mainConnection != null && mainConnection.isConnected()) {
            new Thread(() -> {
                mainConnection.disconnect();
                if (transferManager != null) transferManager.shutdown();
                SwingUtilities.invokeLater(() -> {
                    loginPanel.setConnectedState(false);
                    remoteFilePanel.updateFileList(null);
                    remoteFilePanel.setCurrentPath("/");
                    transferPanel.updateProgress(0, "Đã ngắt kết nối.");
                    lblConnectionStatus.setText("Trạng thái: Chưa kết nối");
                    lblConnectionStatus.setForeground(Color.DARK_GRAY);
                });
            }).start();
        } else {
            String host = loginPanel.getHost();
            int port = loginPanel.getPort();
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
                        mainConnection = conn;
                        transferService = new FileTransferService(mainConnection);
                        directoryService = new DirectoryService(mainConnection);
                        transferManager = new TransferManager(3, host, port, user, pass);

                        if (keepAliveTimer != null) keepAliveTimer.start();

                        SwingUtilities.invokeLater(() -> {
                            loginPanel.setConnectedState(true);
                            transferPanel.updateProgress(0, "Đã kết nối đến " + host + " với tài khoản " + user);
                            lblConnectionStatus.setText("Đã kết nối đến: " + host + ":" + port + " (" + user + ")");
                            lblConnectionStatus.setForeground(new Color(34, 139, 34));
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
                            lblConnectionStatus.setText("Trạng thái: Đăng nhập thất bại");
                            lblConnectionStatus.setForeground(Color.RED);
                        });
                    }
                } catch (Exception ex) {
                    appendLog("Lỗi kết nối: " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        loginPanel.setConnectingState(false);
                        JOptionPane.showMessageDialog(this,
                                "Không thể kết nối!\n" + ex.getMessage()
                                        + "\n\nKiểm tra lại:\n  • Máy chủ/Cổng có đúng không?\n  • Server có đang hoạt động không?",
                                "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
                        transferPanel.updateProgress(0, "Kết nối thất bại.");
                        lblConnectionStatus.setText("Trạng thái: Lỗi kết nối");
                        lblConnectionStatus.setForeground(Color.RED);
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
                    int lastQuote = msg.lastIndexOf('"');
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
            } finally {
                synchronized (this) {
                    isRefreshing = false;
                }
            }
        }).start();
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

    private void handleUpload() {
        if (!isConnected()) return;
        File selectedFile = localFilePanel.getSelectedFile();
        if (selectedFile == null) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 mục ở bảng Cục bộ để Upload!");
            return;
        }
        handleUploadFile(selectedFile);
    }

    private void handleDropFilesToRemote(List<File> files) {
        if (!isConnected() || files == null || files.isEmpty()) return;
        new Thread(() -> {
            for (File file : files) {
                if (file.exists()) {
                    handleUploadFile(file);
                }
            }
        }).start();
    }

    private void handleUploadFile(File selectedFile) {
        if (!isConnected() || selectedFile == null) return;

        if (selectedFile.isDirectory()) {
            handleUploadFolder(selectedFile);
            return;
        }

        String remoteTargetName = selectedFile.getName();
        boolean alreadyExists = currentRemoteFiles.stream().anyMatch(f -> f.getName().equalsIgnoreCase(selectedFile.getName()));
        long resumeOffset = 0;

        if (alreadyExists) {
            FileConflictDialog dlg = new FileConflictDialog(this, selectedFile.getName(), 0, selectedFile.length());
            dlg.setVisible(true);
            if (dlg.getSelectedAction() == FileConflictDialog.ConflictAction.CANCEL ||
                dlg.getSelectedAction() == FileConflictDialog.ConflictAction.SKIP) {
                appendLog("Bỏ qua tải lên tệp: " + selectedFile.getName());
                return;
            } else if (dlg.getSelectedAction() == FileConflictDialog.ConflictAction.RENAME) {
                remoteTargetName = getUniqueRemoteName(selectedFile.getName());
                appendLog("Tự động đổi tên tệp upload thành: " + remoteTargetName);
            }
        }

        submitUploadTask(selectedFile, remoteTargetName, resumeOffset);
    }

    private void submitUploadTask(File localFile, String remoteName) {
        submitUploadTask(localFile, remoteName, 0);
    }

    private void submitUploadTask(File localFile, String remoteName, long resumeOffset) {
        String taskId = "UP-" + (taskCounter++);
        TransferTask task = new TransferTask(taskId, TransferTask.Type.UPLOAD, localFile, remoteName);
        if (resumeOffset > 0) {
            task.setResumeOffset(resumeOffset);
        }
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

    private void handleDownload() {
        if (!isConnected()) return;

        String remoteFileName = remoteFilePanel.getSelectedFileName();
        if (remoteFileName == null || "..".equals(remoteFileName)) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 mục ở bảng Server để Download!");
            return;
        }

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
        long resumeOffset = 0;

        if (localDest.exists()) {
            FileConflictDialog dlg = new FileConflictDialog(this, localDest.getName(), localDest.length(), 0);
            dlg.setVisible(true);
            if (dlg.getSelectedAction() == FileConflictDialog.ConflictAction.CANCEL ||
                dlg.getSelectedAction() == FileConflictDialog.ConflictAction.SKIP) {
                appendLog("Bỏ qua tải về tệp: " + remoteFileName);
                return;
            } else if (dlg.getSelectedAction() == FileConflictDialog.ConflictAction.RENAME) {
                localDest = getUniqueLocalFile(localDest);
                appendLog("Tự động đổi tên tệp tải về thành: " + localDest.getName());
            }
        }

        submitDownloadTask(remoteFileName, localDest, resumeOffset);
    }

    private void handleDropRemoteItemToLocal(String remoteName, boolean isDir) {
        if (!isConnected() || remoteName == null || remoteName.isEmpty() || "..".equals(remoteName)) return;

        File localTargetDir = localFilePanel.getCurrentDirectory();
        if (localTargetDir == null || !localTargetDir.exists()) {
            localTargetDir = new File(System.getProperty("user.dir"));
        }

        if (isDir) {
            File targetFolder = new File(localTargetDir, remoteName);
            targetFolder.mkdirs();
            startDownloadFolderTask(remoteName, targetFolder);
        } else {
            File localDest = new File(localTargetDir, remoteName);
            long resumeOffset = 0;
            if (localDest.exists()) {
                FileConflictDialog dlg = new FileConflictDialog(this, localDest.getName(), localDest.length(), 0);
                dlg.setVisible(true);
                if (dlg.getSelectedAction() == FileConflictDialog.ConflictAction.CANCEL ||
                    dlg.getSelectedAction() == FileConflictDialog.ConflictAction.SKIP) return;
                if (dlg.getSelectedAction() == FileConflictDialog.ConflictAction.RENAME) {
                    localDest = getUniqueLocalFile(localDest);
                }
            }
            submitDownloadTask(remoteName, localDest, resumeOffset);
        }
    }

    private void submitDownloadTask(String remoteFileName, File localDest) {
        submitDownloadTask(remoteFileName, localDest, 0);
    }

    private void submitDownloadTask(String remoteFileName, File localDest, long resumeOffset) {
        String taskId = "DOWN-" + (taskCounter++);
        TransferTask task = new TransferTask(taskId, TransferTask.Type.DOWNLOAD, localDest, remoteFileName);
        if (resumeOffset > 0) {
            task.setResumeOffset(resumeOffset);
        }
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

        startDownloadFolderTask(remoteFolderName, localFolder);
    }

    private void startDownloadFolderTask(String remoteFolderName, File localFolder) {
        new Thread(() -> {
            FTPConnection helperConn = new FTPConnection();
            try {
                helperConn.connect(loginPanel.getHost(), loginPanel.getPort());
                helperConn.sendCommand(FTPCommand.USER, loginPanel.getUsername());
                helperConn.sendCommand(FTPCommand.PASS, loginPanel.getPassword());

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

    private void handleRemoteViewEdit(String remoteFileName) {
        if (!isConnected() || remoteFileName == null) return;

        appendLog("Đang tải tạm tập tin để xem/sửa: " + remoteFileName);
        new Thread(() -> {
            try {
                File tempDir = new File(System.getProperty("java.io.tmpdir"), "ftp_preview_cache");
                if (!tempDir.exists()) tempDir.mkdirs();
                File tempFile = new File(tempDir, remoteFileName);

                boolean success = transferService.downloadFile(remoteFileName, tempFile);
                if (success) {
                    SwingUtilities.invokeLater(() -> {
                        FileEditorDialog dialog = new FileEditorDialog(this, tempFile, true, remoteFileName);
                        dialog.setOnSaveRemoteCallback(savedFile -> {
                            appendLog("Đang cập nhật tệp vừa sửa lên máy chủ: " + remoteFileName);
                            new Thread(() -> {
                                try {
                                    boolean upSuccess = transferService.uploadFile(savedFile, remoteFileName);
                                    if (upSuccess) {
                                        appendLog("Cập nhật tệp thành công lên máy chủ: " + remoteFileName);
                                        refreshRemoteFiles();
                                        SwingUtilities.invokeLater(() ->
                                                JOptionPane.showMessageDialog(this, "Đã lưu và tải lên máy chủ thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE));
                                    } else {
                                        appendLog("Cập nhật tệp thất bại lên máy chủ!");
                                    }
                                } catch (Exception ex) {
                                    appendLog("Lỗi khi tải tệp cập nhật lên máy chủ: " + ex.getMessage());
                                }
                            }).start();
                        });
                        dialog.setVisible(true);
                    });
                } else {
                    SwingUtilities.invokeLater(() ->
                            JOptionPane.showMessageDialog(this, "Không thể tải tệp tạm để xem!", "Lỗi", JOptionPane.ERROR_MESSAGE));
                }
            } catch (Exception ex) {
                appendLog("Lỗi khi xem/sửa tập tin: " + ex.getMessage());
            }
        }).start();
    }

    private void handleRemoteProperties(String fileName) {
        if (!isConnected() || fileName == null) return;

        FTPFile target = currentRemoteFiles.stream()
                .filter(f -> f.getName().equalsIgnoreCase(fileName))
                .findFirst()
                .orElse(null);

        long size = (target != null) ? target.getSize() : 0;
        long lastMod = (target != null) ? target.getLastModified() : 0;
        boolean isDir = (target != null) && target.isDirectory();
        String fullPath = remoteFilePanel.getCurrentPath().equals("/")
                ? "/" + fileName
                : remoteFilePanel.getCurrentPath() + "/" + fileName;

        FilePropertiesDialog dialog = new FilePropertiesDialog(this, fileName, fullPath, size, lastMod, isDir, true);
        dialog.setOnChmodCallback((mode, name) -> {
            new Thread(() -> {
                try {
                    FTPResponse res = mainConnection.sendCommand(FTPCommand.SITE, "CHMOD " + mode + " " + name);
                    appendLog("SITE CHMOD: " + res.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        if (res.isSuccess()) {
                            JOptionPane.showMessageDialog(this, "Đổi quyền thành công (" + mode + ") cho " + name, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(this, "Đổi quyền thất bại: " + res.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                } catch (Exception ex) {
                    showConnectionError(ex);
                }
            }).start();
        });
        dialog.setVisible(true);
    }

    private void handleOpenSearchDialog() {
        if (!isConnected()) return;

        SearchDialog dialog = new SearchDialog(
                this,
                loginPanel.getHost(),
                loginPanel.getPort(),
                loginPanel.getUsername(),
                loginPanel.getPassword(),
                remoteFilePanel.getCurrentPath()
        );

        dialog.setOnNavigateCallback(targetDir -> {
            new Thread(() -> {
                try {
                    FTPResponse res = mainConnection.sendCommand(FTPCommand.CWD, targetDir);
                    if (res.isSuccess()) {
                        refreshRemoteFiles();
                    }
                } catch (Exception ex) {
                    showConnectionError(ex);
                }
            }).start();
        });

        dialog.setOnDownloadItemCallback((remotePath, isDir) -> {
            String fileName = remotePath.contains("/") ? remotePath.substring(remotePath.lastIndexOf('/') + 1) : remotePath;
            if (isDir) {
                File targetFolder = new File(currentLocalDownloadDir, fileName);
                targetFolder.mkdirs();
                startDownloadFolderTask(remotePath, targetFolder);
            } else {
                File localDest = new File(currentLocalDownloadDir, fileName);
                if (localDest.exists()) {
                    localDest = getUniqueLocalFile(localDest);
                }
                submitDownloadTask(remotePath, localDest);
            }
        });

        dialog.setVisible(true);
    }
}
