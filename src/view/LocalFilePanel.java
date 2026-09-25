package view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class LocalFilePanel extends JPanel {
    private final JLabel pathLabel = new JLabel("Đường dẫn cục bộ: ");
    private final JTable fileTable;
    private final DefaultTableModel tableModel;

    private final JButton btnUp = new JButton("⬆ Lên thư mục cha");
    private final JButton btnBrowse = new JButton("📂 Chọn File...");
    private final JButton btnUpload = new JButton(" Upload -> ");

    private File currentDir;
    private File customSelectedFile = null; // Lưu trữ file được chọn qua JFileChooser

    public LocalFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Cục bộ (Local) "));

        tableModel = new DefaultTableModel(new String[]{"Tên File / Thư mục", "Kích thước (Bytes)", "Loại"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        fileTable = new JTable(tableModel);

        // Thanh điều hướng đường dẫn phía trên
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(pathLabel, BorderLayout.CENTER);
        topPanel.add(btnUp, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        add(new JScrollPane(fileTable), BorderLayout.CENTER);

        // Thanh công cụ phía dưới
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(btnBrowse);
        bottomPanel.add(btnUpload);
        add(bottomPanel, BorderLayout.SOUTH);

        // 1. Sự kiện Nhấp đôi chuột vào thư mục để đi vào trong
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = fileTable.getSelectedRow();
                    if (row != -1 && "Thư mục".equals(tableModel.getValueAt(row, 2))) {
                        String dirName = tableModel.getValueAt(row, 0).toString();
                        loadDirectory(new File(currentDir, dirName));
                    }
                }
            }
        });

        // 2. Sự kiện Nút "Lên thư mục cha"
        btnUp.addActionListener(e -> {
            if (currentDir != null && currentDir.getParentFile() != null) {
                loadDirectory(currentDir.getParentFile());
            }
        });

        // 3. Sự kiện Nút "Chọn File..." bằng JFileChooser (Cho phép chọn file ở bất kỳ đâu trên máy)
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(currentDir != null ? currentDir : new File("."));
            chooser.setDialogTitle("Chọn tập tin trong máy tính của bạn");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                customSelectedFile = chooser.getSelectedFile();
                // Tải thư mục chứa file đó lên giao diện
                if (customSelectedFile.getParentFile() != null) {
                    loadDirectory(customSelectedFile.getParentFile());
                }
                // Tự động highlight chọn file vừa chọn trong bảng
                highlightFileInTable(customSelectedFile.getName());
            }
        });

        // Mặc định khởi tạo ở thư mục project
        loadDirectory(new File(System.getProperty("user.dir")));
    }

    public void loadDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        this.currentDir = dir;
        this.customSelectedFile = null;
        pathLabel.setText("Đường dẫn: " + dir.getAbsolutePath());
        tableModel.setRowCount(0);

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                // Bỏ qua các file ẩn/sơ cấp của hệ thống nếu muốn
                if (f.isHidden()) continue;

                tableModel.addRow(new Object[]{
                        f.getName(),
                        f.isDirectory() ? "-" : f.length(),
                        f.isDirectory() ? "Thư mục" : "Tập tin"
                });
            }
        }
    }

    private void highlightFileInTable(String fileName) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 0).equals(fileName)) {
                fileTable.setRowSelectionInterval(i, i);
                fileTable.scrollRectToVisible(fileTable.getCellRect(i, 0, true));
                break;
            }
        }
    }

    public File getSelectedFile() {
        // Ưu tiên file được chọn từ JFileChooser
        if (customSelectedFile != null && customSelectedFile.exists()) {
            return customSelectedFile;
        }

        // Lấy file đang chọn trong bảng
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow != -1 && currentDir != null) {
            String fileName = tableModel.getValueAt(selectedRow, 0).toString();
            return new File(currentDir, fileName);
        }
        return null;
    }

    public JButton getBtnUpload() { return btnUpload; }
}