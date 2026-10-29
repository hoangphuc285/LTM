package client.view;

import client.model.FTPFile;
import client.network.FTPCommand;
import client.network.FTPConnection;
import client.service.FileTransferService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SearchDialog extends JDialog {
    private final JTextField txtKeyword = new JTextField(20);
    private final JCheckBox chkRecursive = new JCheckBox("Tìm kiếm đệ quy thư mục con", true);
    private final JButton btnSearch = new JButton("🔍 Bắt đầu tìm");
    private final JButton btnStop = new JButton("⏹ Dừng");

    private final DefaultTableModel tableModel;
    private final JTable resultTable;
    private final JLabel lblStatus = new JLabel("Nhập từ khóa và bấm Tìm kiếm.");

    private final JButton btnDownload = new JButton("⬇ Tải về mục này");
    private final JButton btnGoToDir = new JButton("📂 Đi tới thư mục");
    private final JButton btnClose = new JButton("Đóng");

    private final String host;
    private final int port;
    private final String user;
    private final String pass;
    private final String currentPath;

    private AtomicBoolean isSearching = new AtomicBoolean(false);
    private FTPConnection activeConn = null;

    private Consumer<String> onNavigateCallback;
    private BiConsumer<String, Boolean> onDownloadItemCallback;

    public SearchDialog(Frame parent, String host, int port, String user, String pass, String currentPath) {
        super(parent, "Tìm kiếm tập tin trên Máy chủ (Remote Search)", false);
        this.host = host;
        this.port = port;
        this.user = user;
        this.pass = pass;
        this.currentPath = currentPath;

        setSize(700, 480);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(6, 6));

        // Thanh tìm kiếm phía trên
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        topPanel.setBorder(BorderFactory.createTitledBorder(" Điều kiện tìm kiếm "));
        topPanel.add(new JLabel("Từ khóa:"));
        topPanel.add(txtKeyword);
        topPanel.add(chkRecursive);
        topPanel.add(btnSearch);
        btnStop.setEnabled(false);
        topPanel.add(btnStop);
        add(topPanel, BorderLayout.NORTH);

        // Bảng kết quả ở giữa
        String[] columns = {"Tên tệp / thư mục", "Đường dẫn", "Kích thước", "Loại"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        resultTable = new JTable(tableModel);
        resultTable.getColumnModel().getColumn(0).setCellRenderer(new FileTableCellRenderer());
        add(new JScrollPane(resultTable), BorderLayout.CENTER);

        // Thanh trạng thái và nút dưới cùng
        JPanel bottomContainer = new JPanel(new BorderLayout(5, 5));
        bottomContainer.add(lblStatus, BorderLayout.NORTH);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        btnBar.add(btnDownload);
        btnBar.add(btnGoToDir);
        btnBar.add(btnClose);
        bottomContainer.add(btnBar, BorderLayout.SOUTH);

        add(bottomContainer, BorderLayout.SOUTH);

        // Sự kiện
        btnSearch.addActionListener(e -> startSearch());
        txtKeyword.addActionListener(e -> startSearch());
        btnStop.addActionListener(e -> stopSearch());

        btnDownload.addActionListener(e -> {
            int row = resultTable.getSelectedRow();
            if (row != -1 && onDownloadItemCallback != null) {
                String name = (String) tableModel.getValueAt(row, 0);
                String path = (String) tableModel.getValueAt(row, 1);
                String fullPath = path.endsWith("/") ? path + name : path + "/" + name;
                boolean isDir = "Thư mục".equals(tableModel.getValueAt(row, 3));
                onDownloadItemCallback.accept(fullPath, isDir);
            }
        });

        btnGoToDir.addActionListener(e -> {
            int row = resultTable.getSelectedRow();
            if (row != -1 && onNavigateCallback != null) {
                String path = (String) tableModel.getValueAt(row, 1);
                onNavigateCallback.accept(path);
                dispose();
            }
        });

        btnClose.addActionListener(e -> {
            stopSearch();
            dispose();
        });
    }

    public void setOnNavigateCallback(Consumer<String> callback) {
        this.onNavigateCallback = callback;
    }

    public void setOnDownloadItemCallback(BiConsumer<String, Boolean> callback) {
        this.onDownloadItemCallback = callback;
    }

    private void startSearch() {
        String keyword = txtKeyword.getText().trim();
        if (keyword.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập từ khóa tìm kiếm!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        tableModel.setRowCount(0);
        isSearching.set(true);
        btnSearch.setEnabled(false);
        btnStop.setEnabled(true);
        lblStatus.setText("Đang tìm kiếm \"" + keyword + "\" ...");

        new Thread(() -> {
            try {
                activeConn = new FTPConnection();
                activeConn.connect(host, port);
                activeConn.sendCommand(FTPCommand.USER, user);
                activeConn.sendCommand(FTPCommand.PASS, pass);

                boolean recursive = chkRecursive.isSelected();
                searchInDirectory(activeConn, currentPath, keyword.toLowerCase(), recursive);

                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Tìm kiếm hoàn tất. Tìm thấy " + tableModel.getRowCount() + " kết quả.");
                    btnSearch.setEnabled(true);
                    btnStop.setEnabled(false);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("Lỗi tìm kiếm: " + ex.getMessage());
                    btnSearch.setEnabled(true);
                    btnStop.setEnabled(false);
                });
            } finally {
                if (activeConn != null) activeConn.disconnect();
                isSearching.set(false);
            }
        }).start();
    }

    private void searchInDirectory(FTPConnection conn, String dirPath, String keywordLower, boolean recursive) {
        if (!isSearching.get()) return;

        try {
            conn.sendCommand(FTPCommand.CWD, dirPath);
            FileTransferService helperService = new FileTransferService(conn);
            List<FTPFile> files = helperService.listFiles();

            for (FTPFile f : files) {
                if (!isSearching.get()) return;

                if (f.getName().toLowerCase().contains(keywordLower)) {
                    SwingUtilities.invokeLater(() -> {
                        tableModel.addRow(new Object[]{
                                f.getName(),
                                dirPath,
                                f.isDirectory() ? "Thư mục" : FTPFile.formatSize(f.getSize()),
                                f.isDirectory() ? "Thư mục" : "Tập tin"
                        });
                    });
                }

                if (recursive && f.isDirectory()) {
                    String subPath = dirPath.equals("/") ? "/" + f.getName() : dirPath + "/" + f.getName();
                    searchInDirectory(conn, subPath, keywordLower, true);
                    // Quay lại thư mục hiện tại sau khi duyệt xong subDir
                    conn.sendCommand(FTPCommand.CWD, dirPath);
                }
            }
        } catch (Exception ignored) {}
    }

    private void stopSearch() {
        isSearching.set(false);
        if (activeConn != null) {
            new Thread(() -> activeConn.disconnect()).start();
        }
        btnSearch.setEnabled(true);
        btnStop.setEnabled(false);
        lblStatus.setText("Đã dừng tìm kiếm.");
    }
}

