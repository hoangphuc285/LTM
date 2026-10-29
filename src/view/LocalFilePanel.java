package view;

import model.FTPFile;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

public class LocalFilePanel extends JPanel {
    // P5: Thanh địa chỉ có thể gõ đường dẫn
    private final JTextField pathField = new JTextField();
    private final JButton btnGo = new JButton("Đi tới");
    private final JButton btnUp = new JButton("⬆ Lên");

    private final JTable fileTable;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> sorter;

    private final JButton btnBrowse = new JButton("📂 Chọn File...");
    private final JButton btnUpload = new JButton(" Upload -> ");

    // P7: Context menu chuột phải
    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem menuUpload = new JMenuItem("⬆ Tải lên máy chủ (Upload)");
    private final JMenuItem menuOpen = new JMenuItem("📂 Mở thư mục");
    private final JMenuItem menuNewFolder = new JMenuItem("📁 Tạo thư mục mới");
    private final JMenuItem menuRename = new JMenuItem("✏ Đổi tên");
    private final JMenuItem menuDelete = new JMenuItem("🗑 Xóa");
    private final JMenuItem menuRefresh = new JMenuItem("🔄 Làm mới danh sách");

    private File currentDir;
    private File customSelectedFile = null;

    public LocalFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Cục bộ (Local) "));

        // P1 & P3: Bảng có 4 cột: Tên, Kích thước, Loại, Ngày sửa đổi
        String[] columnNames = {"Tên File / Thư mục", "Kích thước", "Loại", "Ngày sửa đổi"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) return FTPFile.FileSizeItem.class;
                if (columnIndex == 3) return FTPFile.FileDateItem.class;
                return String.class;
            }
        };

        fileTable = new JTable(tableModel);

        // P6: Gán icon cho cột tên file/thư mục
        fileTable.getColumnModel().getColumn(0).setCellRenderer(new FileTableCellRenderer());

        // P4: Bật tính năng Sort theo cột bằng TableRowSorter
        sorter = new TableRowSorter<>(tableModel);
        fileTable.setRowSorter(sorter);

        // P5: Thanh điều hướng phía trên gồm: Label, TextField (nhập đường dẫn), Nút Go, Nút Lên
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JPanel navPanel = new JPanel(new BorderLayout(3, 3));
        navPanel.add(new JLabel("Đường dẫn: "), BorderLayout.WEST);
        navPanel.add(pathField, BorderLayout.CENTER);

        JPanel btnNavPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btnNavPanel.add(btnGo);
        btnNavPanel.add(btnUp);
        navPanel.add(btnNavPanel, BorderLayout.EAST);

        topPanel.add(navPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        add(new JScrollPane(fileTable), BorderLayout.CENTER);

        // Thanh công cụ phía dưới
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(btnBrowse);
        bottomPanel.add(btnUpload);
        add(bottomPanel, BorderLayout.SOUTH);

        // P7: Thiết lập Menu chuột phải (Context Menu)
        contextMenu.add(menuUpload);
        contextMenu.add(menuOpen);
        contextMenu.addSeparator();
        contextMenu.add(menuNewFolder);
        contextMenu.add(menuRename);
        contextMenu.add(menuDelete);
        contextMenu.addSeparator();
        contextMenu.add(menuRefresh);

        // P5: Xử lý sự kiện khi gõ đường dẫn và ấn Enter hoặc nút "Đi tới"
        ActionListener navigateAction = e -> {
            String inputPath = pathField.getText().trim();
            if (!inputPath.isEmpty()) {
                File target = new File(inputPath);
                if (target.exists() && target.isDirectory()) {
                    loadDirectory(target);
                } else {
                    JOptionPane.showMessageDialog(this, "Đường dẫn thư mục không hợp lệ hoặc không tồn tại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        btnGo.addActionListener(navigateAction);
        pathField.addActionListener(navigateAction);

        // 1. Nhấp đúp chuột để vào thư mục con & Chuột phải mở Context Menu
        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = fileTable.getSelectedRow();
                    if (viewRow != -1) {
                        int modelRow = fileTable.convertRowIndexToModel(viewRow);
                        if ("Thư mục".equals(tableModel.getValueAt(modelRow, 2))) {
                            String dirName = tableModel.getValueAt(modelRow, 0).toString();
                            loadDirectory(new File(currentDir, dirName));
                        }
                    }
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                checkPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                checkPopup(e);
            }

            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = fileTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && !fileTable.isRowSelected(row)) {
                        fileTable.setRowSelectionInterval(row, row);
                    }
                    boolean hasSelection = fileTable.getSelectedRow() != -1;
                    boolean isDir = hasSelection && isSelectedDirectory();

                    menuUpload.setEnabled(hasSelection && !isDir);
                    menuOpen.setEnabled(isDir);
                    menuRename.setEnabled(hasSelection);
                    menuDelete.setEnabled(hasSelection);

                    contextMenu.show(fileTable, e.getX(), e.getY());
                }
            }
        });

        // Xử lý các mục trong Context Menu
        menuUpload.addActionListener(e -> btnUpload.doClick());
        menuOpen.addActionListener(e -> {
            File selected = getSelectedFile();
            if (selected != null && selected.isDirectory()) {
                loadDirectory(selected);
            }
        });
        menuRefresh.addActionListener(e -> loadDirectory(currentDir));

        menuNewFolder.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới:", "Tạo Thư Mục Cục Bộ", JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.trim().isEmpty()) {
                File newDir = new File(currentDir, name.trim());
                if (newDir.mkdir()) {
                    loadDirectory(currentDir);
                } else {
                    JOptionPane.showMessageDialog(this, "Không thể tạo thư mục!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        menuRename.addActionListener(e -> {
            File selected = getSelectedFile();
            if (selected == null) return;
            String newName = JOptionPane.showInputDialog(this, "Nhập tên mới:", selected.getName());
            if (newName != null && !newName.trim().isEmpty() && !newName.trim().equals(selected.getName())) {
                File dest = new File(selected.getParentFile(), newName.trim());
                if (selected.renameTo(dest)) {
                    loadDirectory(currentDir);
                } else {
                    JOptionPane.showMessageDialog(this, "Đổi tên thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        menuDelete.addActionListener(e -> {
            File selected = getSelectedFile();
            if (selected == null) return;
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Bạn có chắc muốn xóa: " + selected.getName() + "?",
                    "Xác nhận xóa", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                if (deleteFileOrDir(selected)) {
                    loadDirectory(currentDir);
                } else {
                    JOptionPane.showMessageDialog(this, "Xóa thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // 2. Nút "Lên thư mục cha"
        btnUp.addActionListener(e -> {
            if (currentDir != null && currentDir.getParentFile() != null) {
                loadDirectory(currentDir.getParentFile());
            }
        });

        // 3. Nút "Chọn File..."
        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(currentDir != null ? currentDir : new File("."));
            chooser.setDialogTitle("Chọn tập tin trong máy tính");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);

            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                customSelectedFile = chooser.getSelectedFile();
                if (customSelectedFile.getParentFile() != null) {
                    loadDirectory(customSelectedFile.getParentFile());
                }
                highlightFileInTable(customSelectedFile.getName());
            }
        });

        // Mặc định khởi tạo ở thư mục hiện hành
        loadDirectory(new File(System.getProperty("user.dir")));
    }

    private boolean deleteFileOrDir(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFileOrDir(f);
                }
            }
        }
        return file.delete();
    }

    public void loadDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        this.currentDir = dir;
        this.customSelectedFile = null;
        pathField.setText(dir.getAbsolutePath());
        tableModel.setRowCount(0);

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isHidden()) continue;

                tableModel.addRow(new Object[]{
                        f.getName(),
                        new FTPFile.FileSizeItem(f.isDirectory() ? 0 : f.length(), f.isDirectory()),
                        f.isDirectory() ? "Thư mục" : "Tập tin",
                        new FTPFile.FileDateItem(f.lastModified())
                });
            }
        }
    }

    private void highlightFileInTable(String fileName) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 0).equals(fileName)) {
                int viewIndex = fileTable.convertRowIndexToView(i);
                fileTable.setRowSelectionInterval(viewIndex, viewIndex);
                fileTable.scrollRectToVisible(fileTable.getCellRect(viewIndex, 0, true));
                break;
            }
        }
    }

    public boolean isSelectedDirectory() {
        int viewRow = fileTable.getSelectedRow();
        if (viewRow == -1) return false;
        int modelRow = fileTable.convertRowIndexToModel(viewRow);
        return "Thư mục".equals(tableModel.getValueAt(modelRow, 2));
    }

    public File getSelectedFile() {
        if (customSelectedFile != null && customSelectedFile.exists()) {
            return customSelectedFile;
        }

        int viewRow = fileTable.getSelectedRow();
        if (viewRow != -1 && currentDir != null) {
            int modelRow = fileTable.convertRowIndexToModel(viewRow);
            String fileName = tableModel.getValueAt(modelRow, 0).toString();
            return new File(currentDir, fileName);
        }
        return null;
    }

    public JButton getBtnUpload() { return btnUpload; }
    public File getCurrentDirectory() { return currentDir; }
}
