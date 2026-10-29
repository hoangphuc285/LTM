package view;

import model.FTPFile;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;

public class RemoteFilePanel extends JPanel {
    // P5: Thanh địa chỉ có thể gõ đường dẫn remote
    private final JTextField pathField = new JTextField("/");
    private final JButton btnGo = new JButton("Đi tới");
    private final JButton btnUp = new JButton("⬆ Lên");

    private final JTable fileTable;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> sorter;

    private final JButton btnDownload = new JButton("Tải về");
    private final JButton btnNewFolder = new JButton("Tạo Thư Mục");
    private final JButton btnDelete = new JButton("Xóa");
    private final JButton btnRename = new JButton("Đổi Tên");

    // P7: Menu chuột phải
    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem menuDownload = new JMenuItem("⬇ Tải về máy (Download)");
    private final JMenuItem menuOpenFolder = new JMenuItem("📂 Mở thư mục này");
    private final JMenuItem menuNewFolder = new JMenuItem("📁 Tạo thư mục mới");
    private final JMenuItem menuRename = new JMenuItem("✏ Đổi tên");
    private final JMenuItem menuDelete = new JMenuItem("🗑 Xóa");
    private final JMenuItem menuRefresh = new JMenuItem("🔄 Làm mới danh sách");

    private Consumer<String> onNavigateCallback;
    private Runnable onRefreshCallback;

    public RemoteFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Máy chủ (FTP Server) "));

        // P1 & P3: Bảng gồm 4 cột: Tên, Kích thước, Loại, Ngày sửa đổi
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

        // P5: Thanh trên gồm: Label, TextField đường dẫn remote, Nút Go, Nút Lên thư mục cha
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        JPanel navPanel = new JPanel(new BorderLayout(3, 3));
        navPanel.add(new JLabel("Đường dẫn máy chủ: "), BorderLayout.WEST);
        navPanel.add(pathField, BorderLayout.CENTER);

        JPanel btnNavPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btnNavPanel.add(btnGo);
        btnNavPanel.add(btnUp);
        navPanel.add(btnNavPanel, BorderLayout.EAST);

        topPanel.add(navPanel, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);

        add(new JScrollPane(fileTable), BorderLayout.CENTER);

        // Thanh công cụ thao tác phía dưới
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(btnDownload);
        bottomPanel.add(btnNewFolder);
        bottomPanel.add(btnRename);
        bottomPanel.add(btnDelete);
        add(bottomPanel, BorderLayout.SOUTH);

        // P7: Thiết lập Menu chuột phải
        contextMenu.add(menuDownload);
        contextMenu.add(menuOpenFolder);
        contextMenu.addSeparator();
        contextMenu.add(menuNewFolder);
        contextMenu.add(menuRename);
        contextMenu.add(menuDelete);
        contextMenu.addSeparator();
        contextMenu.add(menuRefresh);

        // P5: Sự kiện khi người dùng gõ đường dẫn và ấn Enter hoặc nút "Đi tới"
        ActionListener navigateAction = e -> {
            String targetPath = pathField.getText().trim();
            if (!targetPath.isEmpty() && onNavigateCallback != null) {
                onNavigateCallback.accept(targetPath);
            }
        };
        btnGo.addActionListener(navigateAction);
        pathField.addActionListener(navigateAction);

        // Chuột phải mở Context Menu
        fileTable.addMouseListener(new MouseAdapter() {
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

                    menuDownload.setEnabled(hasSelection && !isDir);
                    menuOpenFolder.setEnabled(isDir);
                    menuRename.setEnabled(hasSelection);
                    menuDelete.setEnabled(hasSelection);

                    contextMenu.show(fileTable, e.getX(), e.getY());
                }
            }
        });

        // Xử lý các mục trong Context Menu
        menuDownload.addActionListener(e -> btnDownload.doClick());
        menuNewFolder.addActionListener(e -> btnNewFolder.doClick());
        menuRename.addActionListener(e -> btnRename.doClick());
        menuDelete.addActionListener(e -> btnDelete.doClick());
        menuOpenFolder.addActionListener(e -> {
            String folder = getSelectedFileName();
            if (folder != null && onNavigateCallback != null) {
                onNavigateCallback.accept(folder);
            }
        });
        menuRefresh.addActionListener(e -> {
            if (onRefreshCallback != null) {
                onRefreshCallback.run();
            }
        });
    }

    public void setOnNavigateCallback(Consumer<String> callback) {
        this.onNavigateCallback = callback;
    }

    public void setOnRefreshCallback(Runnable callback) {
        this.onRefreshCallback = callback;
    }

    public void setCurrentPath(String path) {
        pathField.setText(path);
    }

    public String getCurrentPath() {
        return pathField.getText().trim();
    }

    public void updateFileList(List<FTPFile> files) {
        tableModel.setRowCount(0);
        if (files != null) {
            for (FTPFile file : files) {
                tableModel.addRow(new Object[]{
                        file.getName(),
                        new FTPFile.FileSizeItem(file.isDirectory() ? 0 : file.getSize(), file.isDirectory()),
                        file.isDirectory() ? "Thư mục" : "Tập tin",
                        new FTPFile.FileDateItem(file.getLastModified())
                });
            }
        }
    }

    public String getSelectedFileName() {
        int viewRow = fileTable.getSelectedRow();
        if (viewRow == -1) return null;
        int modelRow = fileTable.convertRowIndexToModel(viewRow);
        return (String) tableModel.getValueAt(modelRow, 0);
    }

    public boolean isSelectedDirectory() {
        int viewRow = fileTable.getSelectedRow();
        if (viewRow == -1) return false;
        int modelRow = fileTable.convertRowIndexToModel(viewRow);
        return "Thư mục".equals(tableModel.getValueAt(modelRow, 2));
    }

    public JTable getFileTable() { return fileTable; }
    public DefaultTableModel getTableModel() { return tableModel; }
    public JButton getBtnUp() { return btnUp; }
    public JButton getBtnDownload() { return btnDownload; }
    public JButton getBtnNewFolder() { return btnNewFolder; }
    public JButton getBtnDelete() { return btnDelete; }
    public JButton getBtnRename() { return btnRename; }
}
