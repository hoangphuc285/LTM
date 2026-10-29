package server;

import client.view.UITheme;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServerGUI extends JFrame {
    // 1. Thanh điều khiển phía trên
    private final JToggleButton btnStartStop = new JToggleButton("Khoi chay Server");
    private final JLabel lblStatus = new JLabel("Trạng thái: ĐÃ DỪNG", SwingConstants.LEFT);
    private final JTextField txtPort = new JTextField("2121", 5);
    private final JTextField txtStorage = new JTextField("storage", 10);
    private final JButton btnConfig = new JButton("Luu Cau hinh");
    private final JToggleButton btnThemeToggle = new JToggleButton("Giao dien Toi");

    // Tab 1: Clients Table
    private final DefaultTableModel clientTableModel = new DefaultTableModel(
            new String[]{"IP:Port", "Tài khoản", "Trạng thái", "Thư mục hiện tại", "Lệnh gần nhất", "Dữ liệu (Gửi/Nhận)", "Thời điểm kết nối"}, 0
    ) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    private final JTable clientTable = new JTable(clientTableModel);
    private final JButton btnKickClient = new JButton("Ngat ket noi client (Kick)");

    // Tab 2: Users Management Table
    private final DefaultTableModel userTableModel = new DefaultTableModel(
            new String[]{"Tài khoản", "Quyền hạn", "Thư mục riêng", "Hạn mức (Quota)"}, 0
    ) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    private final JTable userTable = new JTable(userTableModel);
    private final JButton btnAddUser    = new JButton("Them User");
    private final JButton btnDeleteUser = new JButton("Xoa User");

    // Tab 3: File Storage Table
    private final DefaultTableModel fileTableModel = new DefaultTableModel(
            new String[]{"Tên Tập tin / Thư mục", "Kích thước", "Loại", "Ngày sửa đổi"}, 0
    ) { @Override public boolean isCellEditable(int r, int c) { return false; } };
    private final JTable fileTable = new JTable(fileTableModel);
    private final JLabel lblUserStorageHeader = new JLabel("Thư mục lưu trữ: (Chọn client hoặc user để xem)");

    // Tab 4: System Logs
    private final JTextPane logTextPane = new JTextPane();
    private final StyledDocument logDoc = logTextPane.getStyledDocument();
    private final JTextField txtLogFilter = new JTextField(15);
    private final JButton btnClearLog = new JButton("Xóa log");
    private final JButton btnExportLog = new JButton("Lưu log ra file...");

    // Status bar dưới cùng
    private final JLabel lblStatClients = new JLabel("Clients online: 0");
    private final JLabel lblStatTransfers = new JLabel("Tệp đã truyền: 0");
    private final JLabel lblStatBandwidth = new JLabel("Tổng dung lượng: 0 MB");
    private final JLabel lblStatUptime = new JLabel("Thời gian chạy: 00:00:00");

    private Server serverInstance;
    private final FileManager fileManager = new FileManager("storage");
    private long serverStartTime = 0;
    private javax.swing.Timer uptimeTimer;
    private int transferCount = 0;

    public ServerGUI() {
        setTitle("MyFTPServer - Dashboard Quản lý");
        setSize(1060, 700);
        setMinimumSize(new Dimension(880, 560));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        // 1. Toolbar trên cùng
        JPanel topToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        btnStartStop.setFont(UITheme.APP_FONT_BOLD);
        lblStatus.setForeground(client.view.AppColors.error());
        topToolbar.add(btnStartStop);
        topToolbar.add(lblStatus);
        topToolbar.add(new JSeparator(SwingConstants.VERTICAL));
        topToolbar.add(new JLabel("Cổng:"));
        topToolbar.add(txtPort);
        topToolbar.add(new JLabel("Thư mục gốc:"));
        topToolbar.add(txtStorage);
        topToolbar.add(btnConfig);
        topToolbar.add(new JSeparator(SwingConstants.VERTICAL));
        topToolbar.add(btnThemeToggle);
        add(topToolbar, BorderLayout.NORTH);

        // 2. Hệ thống các Tab quản lý
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Clients Dang Ket Noi",  createClientsPanel());
        tabbedPane.addTab("Quan Ly Nguoi Dung",     createUsersPanel());
        tabbedPane.addTab("Quan Ly Tap Tin",         createFilesPanel());
        tabbedPane.addTab("Nhat Ky He Thong",        createLogsPanel());
        add(tabbedPane, BorderLayout.CENTER);

        // 3. Thanh thống kê dưới đáy
        JPanel statusBar = new JPanel(new GridLayout(1, 4, 10, 0));
        statusBar.setBorder(BorderFactory.createEmptyBorder(6, 15, 6, 15));
        statusBar.add(lblStatClients);
        statusBar.add(lblStatTransfers);
        statusBar.add(lblStatBandwidth);
        statusBar.add(lblStatUptime);
        add(statusBar, BorderLayout.SOUTH);

        initCenterTableRenderers();
        initEvents();
        initDefaultUsers();
    }

    private void initCenterTableRenderers() {
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < clientTable.getColumnCount(); i++) {
            if (i != 0) clientTable.getColumnModel().getColumn(i).setCellRenderer(center);
        }
    }

    private JPanel createClientsPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(new JScrollPane(clientTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(btnKickClient);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createUsersPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(new JScrollPane(userTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(btnAddUser);
        actions.add(btnDeleteUser);
        p.add(actions, BorderLayout.SOUTH);
        return p;
    }

    private JPanel createFilesPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        p.add(lblUserStorageHeader, BorderLayout.NORTH);
        p.add(new JScrollPane(fileTable), BorderLayout.CENTER);
        return p;
    }

    private JPanel createLogsPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        logTextPane.setEditable(false);
        logTextPane.setFont(UITheme.MONO_FONT);

        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        toolBar.add(new JLabel("Lọc theo Client/Keyword:"));
        toolBar.add(txtLogFilter);
        toolBar.add(btnClearLog);
        toolBar.add(btnExportLog);

        p.add(toolBar, BorderLayout.NORTH);
        p.add(new JScrollPane(logTextPane), BorderLayout.CENTER);
        return p;
    }

    private void initEvents() {
        btnThemeToggle.setSelected(client.view.AppColors.isDark());
        btnThemeToggle.setText(client.view.AppColors.isDark() ? "Giao dien Sang" : "Giao dien Toi");
        btnThemeToggle.addActionListener(e -> {
            boolean dark = btnThemeToggle.isSelected();
            btnThemeToggle.setText(dark ? "Giao dien Sang" : "Giao dien Toi");
            client.view.ThemeManager.toggle(dark);
        });

        btnStartStop.addActionListener(e -> toggleServer());

        btnConfig.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Đã cập nhật cấu hình Server thành công!", "Cấu hình", JOptionPane.INFORMATION_MESSAGE);
        });

        clientTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int r = clientTable.getSelectedRow();
                if (r != -1) {
                    String user = (String) clientTableModel.getValueAt(r, 1);
                    refreshUserFiles(user);
                }
            }
        });

        btnKickClient.addActionListener(e -> {
            int r = clientTable.getSelectedRow();
            if (r == -1) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn client cần ngắt kết nối!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String ip = (String) clientTableModel.getValueAt(r, 0);
            int confirm = JOptionPane.showConfirmDialog(this, "Bạn chắc chắn muốn ngắt kết nối client: " + ip + "?", "Xác nhận Kick", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                removeClient(ip);
                log("Quản trị viên đã ngắt kết nối cưỡng chế client: " + ip);
            }
        });

        btnAddUser.addActionListener(e -> {
            String u = JOptionPane.showInputDialog(this, "Nhập tên tài khoản mới:");
            if (u != null && !u.trim().isEmpty()) {
                String pwd = JOptionPane.showInputDialog(this, "Nhập mật khẩu cho " + u + ":");
                if (pwd != null) {
                    userTableModel.addRow(new Object[]{u.trim(), "Đọc / Ghi / Xóa", "storage/" + u.trim(), "1000 MB"});
                    log("Quản trị viên đã tạo tài khoản người dùng: " + u);
                }
            }
        });

        btnDeleteUser.addActionListener(e -> {
            int r = userTable.getSelectedRow();
            if (r != -1) {
                String u = (String) userTableModel.getValueAt(r, 0);
                userTableModel.removeRow(r);
                log("Quản trị viên đã xóa tài khoản người dùng: " + u);
            }
        });

        btnClearLog.addActionListener(e -> logTextPane.setText(""));
        btnExportLog.addActionListener(e -> exportLogToFile());

        uptimeTimer = new javax.swing.Timer(1000, e -> updateUptime());
    }

    private void initDefaultUsers() {
        userTableModel.addRow(new Object[]{"user01", "Đọc / Ghi / Xóa", "storage/user01", "Không giới hạn"});
        userTableModel.addRow(new Object[]{"user02", "Đọc / Ghi / Xóa", "storage/user02", "500 MB"});
        userTableModel.addRow(new Object[]{"anonymous", "Chỉ đọc", "storage/anonymous", "200 MB"});
    }

    private void toggleServer() {
        if (serverInstance == null || !serverInstance.isRunning()) {
            int port = 2121;
            try { port = Integer.parseInt(txtPort.getText().trim()); } catch (Exception ignored) {}
            serverInstance = new Server(port, this);
            new Thread(() -> serverInstance.start()).start();

            btnStartStop.setText("Dung Server");
            btnStartStop.setSelected(true);
            lblStatus.setText("Trang thai: DANG CHAY (Cong " + port + ")");
            lblStatus.setForeground(client.view.AppColors.success());
            txtPort.setEnabled(false);
            txtStorage.setEnabled(false);
            serverStartTime = System.currentTimeMillis();
            uptimeTimer.start();
        } else {
            serverInstance.stop();
            btnStartStop.setText("Khoi chay Server");
            btnStartStop.setSelected(false);
            lblStatus.setText("Trang thai: DA DUNG");
            lblStatus.setForeground(client.view.AppColors.error());
            txtPort.setEnabled(true);
            txtStorage.setEnabled(true);
            clientTableModel.setRowCount(0);
            fileTableModel.setRowCount(0);
            uptimeTimer.stop();
            lblStatClients.setText("Clients online: 0");
        }
    }

    private void updateUptime() {
        if (serverStartTime <= 0) return;
        long sec = (System.currentTimeMillis() - serverStartTime) / 1000;
        long h = sec / 3600;
        long m = (sec % 3600) / 60;
        long s = sec % 60;
        lblStatUptime.setText(String.format("Thời gian chạy: %02d:%02d:%02d", h, m, s));
    }

    private void exportLogToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("server_system_log.txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter w = new FileWriter(chooser.getSelectedFile())) {
                w.write(logTextPane.getText());
                JOptionPane.showMessageDialog(this, "Đã xuất file log thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Lỗi khi lưu file: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- CÁC HÀM CÔNG KHAI TƯƠNG THÍCH HOÀN TOÀN VỚI CLIENTSESSION VÀ SERVER ---

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            try {
                String filter = txtLogFilter.getText().trim();
                if (!filter.isEmpty() && !message.toLowerCase().contains(filter.toLowerCase())) {
                    return;
                }

                String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
                String line = "[" + time + "] " + message + "\n";

                SimpleAttributeSet attr = new SimpleAttributeSet();
                if (message.contains("Loi") || message.contains("Error") || message.contains("that bai")) {
                    StyleConstants.setForeground(attr, client.view.AppColors.error());
                    StyleConstants.setBold(attr, true);
                } else if (message.contains("thanh cong") || message.contains("khoi dong")) {
                    StyleConstants.setForeground(attr, client.view.AppColors.success());
                } else if (message.contains("Client [")) {
                    StyleConstants.setForeground(attr, client.view.AppColors.accent());
                } else {
                    StyleConstants.setForeground(attr, client.view.AppColors.textSecondary());
                }
                logTextPane.setBackground(client.view.AppColors.logBg());

                logDoc.insertString(logDoc.getLength(), line, attr);
                logTextPane.setCaretPosition(logDoc.getLength());

                if (message.contains("đã tải lên") || message.contains("đã tải xuống")) {
                    transferCount++;
                    lblStatTransfers.setText("Tệp đã truyền: " + transferCount);
                }
            } catch (Exception ignored) {}
        });
    }

    public void addClient(String ip) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            clientTableModel.addRow(new Object[]{ip, "Chưa đăng nhập", "Connected", "/", "None", "0 B / 0 B", time});
            lblStatClients.setText("Clients online: " + clientTableModel.getRowCount());
        });
    }

    public void updateClientUser(String ip, String username) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < clientTableModel.getRowCount(); i++) {
                if (clientTableModel.getValueAt(i, 0).equals(ip)) {
                    clientTableModel.setValueAt(username, i, 1);
                    clientTableModel.setValueAt("Logged In", i, 2);
                    break;
                }
            }
            refreshUserFiles(username);
        });
    }

    public void removeClient(String ip) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < clientTableModel.getRowCount(); i++) {
                if (clientTableModel.getValueAt(i, 0).equals(ip)) {
                    clientTableModel.removeRow(i);
                    break;
                }
            }
            lblStatClients.setText("Clients online: " + clientTableModel.getRowCount());
        });
    }

    public void refreshUserFiles(String username) {
        if (username == null || username.equals("Chưa đăng nhập")) {
            fileTableModel.setRowCount(0);
            lblUserStorageHeader.setText("Thư mục lưu trữ: Chưa đăng nhập");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            File userHome = fileManager.getUserHomeDir(username);
            lblUserStorageHeader.setText("Thư mục lưu trữ của [" + username + "]: " + userHome.getAbsolutePath());
            fileTableModel.setRowCount(0);

            File[] files = userHome.listFiles();
            if (files != null) {
                for (File f : files) {
                    fileTableModel.addRow(new Object[]{
                            f.getName(),
                            f.isDirectory() ? "-" : client.model.FTPFile.formatSize(f.length()),
                            f.isDirectory() ? "Thư mục" : "Tập tin",
                            new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(f.lastModified()))
                    });
                }
            }
        });
    }

    public static void main(String[] args) {
        client.view.ThemeManager.loadSaved();
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
