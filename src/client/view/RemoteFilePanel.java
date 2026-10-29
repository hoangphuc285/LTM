package client.view;

import client.model.FTPFile;

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

    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem menuDownload = new JMenuItem("⬇ Tải về tập tin này (Download)");
    private final JMenuItem menuDownloadFolder = new JMenuItem("📁 Tải về cả thư mục này (Đệ quy)");
    private final JMenuItem menuOpenFolder = new JMenuItem("📂 Mở thư mục này");
    private final JMenuItem menuNewFolder = new JMenuItem("📁 Tạo thư mục mới");
    private final JMenuItem menuRename = new JMenuItem("✏ Đổi tên");
    private final JMenuItem menuDelete = new JMenuItem("🗑 Xóa");
    private final JMenuItem menuRefresh = new JMenuItem("🔄 Làm mới danh sách");

    private Consumer<String> onNavigateCallback;
    private Runnable onRefreshCallback;
    private Consumer<String> onDownloadFolderCallback;

    public RemoteFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Máy chủ (FTP Server) "));

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
        fileTable.getColumnModel().getColumn(0).setCellRenderer(new FileTableCellRenderer());

        sorter = new TableRowSorter<>(tableModel);
        fileTable.setRowSorter(sorter);

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

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(btnDownload);
        bottomPanel.add(btnNewFolder);
        bottomPanel.add(btnRename);
        bottomPanel.add(btnDelete);
        add(bottomPanel, BorderLayout.SOUTH);

        contextMenu.add(menuDownload);
        contextMenu.add(menuDownloadFolder);
        contextMenu.add(menuOpenFolder);
        contextMenu.addSeparator();
        contextMenu.add(menuNewFolder);
        contextMenu.add(menuRename);
        contextMenu.add(menuDelete);
        contextMenu.addSeparator();
        contextMenu.add(menuRefresh);

        ActionListener navigateAction = e -> {
            String targetPath = pathField.getText().trim();
            if (!targetPath.isEmpty() && onNavigateCallback != null) {
                onNavigateCallback.accept(targetPath);
            }
        };
        btnGo.addActionListener(navigateAction);
        pathField.addActionListener(navigateAction);

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
                    menuDownloadFolder.setEnabled(hasSelection && isDir);
                    menuOpenFolder.setEnabled(isDir);
                    menuRename.setEnabled(hasSelection);
                    menuDelete.setEnabled(hasSelection);

                    contextMenu.show(fileTable, e.getX(), e.getY());
                }
            }
        });

        menuDownload.addActionListener(e -> btnDownload.doClick());
        menuDownloadFolder.addActionListener(e -> {
            String folder = getSelectedFileName();
            if (folder != null && onDownloadFolderCallback != null) {
                onDownloadFolderCallback.accept(folder);
            }
        });

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

    public void setOnDownloadFolderCallback(Consumer<String> callback) {
        this.onDownloadFolderCallback = callback;
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
