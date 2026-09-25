package MyFTPServer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ServerGUI extends JFrame {
    private final JButton btnStartStop = new JButton("Khởi chạy Server");
    private final JLabel lblStatus = new JLabel("Trạng thái: ĐÃ DỪNG", SwingConstants.LEFT);

    // Bảng quản lý Client
    private final DefaultTableModel clientTableModel = new DefaultTableModel(
            new String[]{"IP Client", "Tài khoản", "Trạng thái"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable clientTable = new JTable(clientTableModel);

    // Bảng danh sách file của User trên Server
    private final DefaultTableModel fileTableModel = new DefaultTableModel(
            new String[]{"Tên File / Thư mục", "Kích thước (Bytes)", "Loại"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable fileTable = new JTable(fileTableModel);
    private final JLabel lblUserStorageHeader = new JLabel("Thư mục lưu trữ của User: (Chọn client bên trái)");

    // Khung Nhật ký Log
    private final JTextArea logArea = new JTextArea();

    private Server serverInstance;
    private final FileManager fileManager = new FileManager("storage");

    public ServerGUI() {
        setTitle("MyFTPServer - Dashboard Quản lý");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(5, 5));

        // 1. Thanh điều khiển phía trên
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        topPanel.add(btnStartStop);
        topPanel.add(lblStatus);
        add(topPanel, BorderLayout.NORTH);

        // 2. Bảng Client kết nối & Bảng File của User (Chia đôi màn hình)
        JPanel clientPanel = new JPanel(new BorderLayout(5, 5));
        clientPanel.setBorder(BorderFactory.createTitledBorder(" Danh sách Client kết nối "));
        clientPanel.add(new JScrollPane(clientTable), BorderLayout.CENTER);

        JPanel filePanel = new JPanel(new BorderLayout(5, 5));
        filePanel.setBorder(BorderFactory.createTitledBorder(" Quản lý tập tin của User trên Server "));
        filePanel.add(lblUserStorageHeader, BorderLayout.NORTH);
        filePanel.add(new JScrollPane(fileTable), BorderLayout.CENTER);

        JSplitPane centerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, clientPanel, filePanel);
        centerSplitPane.setResizeWeight(0.5);

        // 3. Khung Log phía dưới
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder(" Nhật ký hệ thống (Logs) "));
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        logPanel.setPreferredSize(new Dimension(0, 180));

        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerSplitPane, logPanel);
        mainSplitPane.setResizeWeight(0.65);
        add(mainSplitPane, BorderLayout.CENTER);

        // Sự kiện click vào 1 dòng trong bảng Client để xem danh sách File của User đó
        clientTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int selectedRow = clientTable.getSelectedRow();
                if (selectedRow != -1) {
                    String username = (String) clientTableModel.getValueAt(selectedRow, 1);
                    refreshUserFiles(username);
                }
            }
        });

        // Nút Khởi chạy / Dừng Server
        btnStartStop.addActionListener(e -> toggleServer());
    }

    private void toggleServer() {
        if (serverInstance == null || !serverInstance.isRunning()) {
            serverInstance = new Server(2121, this);
            new Thread(() -> serverInstance.start()).start();
            btnStartStop.setText("Dừng Server");
            lblStatus.setText("Trạng thái: ĐANG CHẠY (Cổng 2121)");
            lblStatus.setForeground(new Color(0, 128, 0));
        } else {
            serverInstance.stop();
            btnStartStop.setText("Khởi chạy Server");
            lblStatus.setText("Trạng thái: ĐÃ DỪNG");
            lblStatus.setForeground(Color.RED);
            clientTableModel.setRowCount(0);
            fileTableModel.setRowCount(0);
        }
    }

    // --- CÁC HÀM CẬP NHẬT GIAO DIỆN TỪ HOẠT ĐỘNG SERVER ---

    public void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void addClient(String ip) {
        SwingUtilities.invokeLater(() -> clientTableModel.addRow(new Object[]{ip, "Chưa đăng nhập", "Connected"}));
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
        });
    }

    public void refreshUserFiles(String username) {
        if (username == null || username.equals("Chưa đăng nhập")) {
            fileTableModel.setRowCount(0);
            lblUserStorageHeader.setText("Thư mục lưu trữ của User: Chưa đăng nhập");
            return;
        }

        SwingUtilities.invokeLater(() -> {
            File userHome = fileManager.getUserHomeDir(username);
            lblUserStorageHeader.setText("Đường dẫn: " + userHome.getAbsolutePath());
            fileTableModel.setRowCount(0);

            File[] files = userHome.listFiles();
            if (files != null) {
                for (File f : files) {
                    fileTableModel.addRow(new Object[]{
                            f.getName(),
                            f.length(),
                            f.isDirectory() ? "Thư mục" : "Tập tin"
                    });
                }
            }
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}