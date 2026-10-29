package client.view;

import client.model.FTPFile;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class LocalFilePanel extends JPanel {
    private final JTextField pathField = new JTextField();
    private final JButton btnGo = new JButton("Đi tới");
    private final JButton btnUp = new JButton("⬆ Lên");

    // P6 (Ưu tiên 2): Thanh lọc file nhanh
    private final JTextField filterField = new JTextField();
    private final JButton btnClearFilter = new JButton("✖");

    private final JTable fileTable;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> sorter;

    private final JButton btnBrowse = new JButton("📂 Chọn File...");
    private final JButton btnUpload = new JButton(" Upload -> ");

    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem menuUpload = new JMenuItem("⬆ Tải lên tập tin này (Upload)");
    private final JMenuItem menuUploadFolder = new JMenuItem("📁 Tải lên cả thư mục này (Đệ quy)");
    private final JMenuItem menuOpen = new JMenuItem("📂 Mở thư mục");
    private final JMenuItem menuNewFolder = new JMenuItem("📁 Tạo thư mục mới");
    private final JMenuItem menuRename = new JMenuItem("✏ Đổi tên");
    private final JMenuItem menuDelete = new JMenuItem("🗑 Xóa");
    private final JMenuItem menuViewEdit = new JMenuItem("👁 Xem / Sửa tập tin (View/Edit)");
    private final JMenuItem menuProperties = new JMenuItem("📋 Thuộc tính (Properties)");
    private final JMenuItem menuRefresh = new JMenuItem("🔄 Làm mới danh sách");

    private Consumer<File> onUploadFolderCallback;
    private BiConsumer<String, Boolean> onDropRemoteItemCallback;

    private File currentDir;
    private File customSelectedFile = null;

    public LocalFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Cục bộ (Local) "));

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

        // Kéo thả chuột (Drag & Drop)
        fileTable.setDragEnabled(true);
        fileTable.setDropMode(DropMode.ON);

        TransferHandler localTransferHandler = new TransferHandler() {
            @Override
            public int getSourceActions(JComponent c) {
                return COPY;
            }

            @Override
            protected Transferable createTransferable(JComponent c) {
                File sel = getSelectedFile();
                if (sel != null) {
                    return new Transferable() {
                        @Override
                        public DataFlavor[] getTransferDataFlavors() {
                            return new DataFlavor[]{DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor};
                        }

                        @Override
                        public boolean isDataFlavorSupported(DataFlavor flavor) {
                            return flavor.equals(DataFlavor.javaFileListFlavor) || flavor.equals(DataFlavor.stringFlavor);
                        }

                        @Override
                        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                            if (flavor.equals(DataFlavor.javaFileListFlavor)) {
                                return Collections.singletonList(sel);
                            } else if (flavor.equals(DataFlavor.stringFlavor)) {
                                return sel.getAbsolutePath();
                            }
                            throw new UnsupportedFlavorException(flavor);
                        }
                    };
                }
                return null;
            }

            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                    if (data != null && data.startsWith("FTP_REMOTE:")) {
                        String[] parts = data.split(":", 3);
                        if (parts.length == 3) {
                            boolean isDir = "DIR".equals(parts[1]);
                            String remoteName = parts[2];
                            if (onDropRemoteItemCallback != null) {
                                onDropRemoteItemCallback.accept(remoteName, isDir);
                                return true;
                            }
                        }
                    }
                } catch (Exception ignored) {}
                return false;
            }
        };

        fileTable.setTransferHandler(localTransferHandler);
        setTransferHandler(localTransferHandler);

        // Thanh điều hướng đường dẫn
        JPanel navPanel = new JPanel(new BorderLayout(3, 3));
        navPanel.add(new JLabel("Đường dẫn: "), BorderLayout.WEST);
        navPanel.add(pathField, BorderLayout.CENTER);

        JPanel btnNavPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        btnNavPanel.add(btnGo);
        btnNavPanel.add(btnUp);
        navPanel.add(btnNavPanel, BorderLayout.EAST);

        // Thanh lọc file nhanh
        JPanel filterPanel = new JPanel(new BorderLayout(3, 3));
        filterPanel.add(new JLabel("🔍 Lọc file: "), BorderLayout.WEST);
        filterPanel.add(filterField, BorderLayout.CENTER);
        btnClearFilter.setToolTipText("Xóa bộ lọc");
        btnClearFilter.setMargin(new Insets(1, 5, 1, 5));
        filterPanel.add(btnClearFilter, BorderLayout.EAST);

        JPanel topContainer = new JPanel(new GridLayout(2, 1, 2, 3));
        topContainer.add(navPanel);
        topContainer.add(filterPanel);
        add(topContainer, BorderLayout.NORTH);

        add(new JScrollPane(fileTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(btnBrowse);
        bottomPanel.add(btnUpload);
        add(bottomPanel, BorderLayout.SOUTH);

        contextMenu.add(menuUpload);
        contextMenu.add(menuUploadFolder);
        contextMenu.add(menuOpen);
        contextMenu.addSeparator();
        contextMenu.add(menuNewFolder);
        contextMenu.add(menuRename);
        contextMenu.add(menuDelete);
        contextMenu.addSeparator();
        contextMenu.add(menuViewEdit);
        contextMenu.add(menuProperties);
        contextMenu.addSeparator();
        contextMenu.add(menuRefresh);

        // Sự kiện lọc file tự động khi gõ phím
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        btnClearFilter.addActionListener(e -> filterField.setText(""));

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
                        } else {
                            btnUpload.doClick();
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
                    menuUploadFolder.setEnabled(hasSelection && isDir);
                    menuOpen.setEnabled(isDir);
                    menuRename.setEnabled(hasSelection);
                    menuDelete.setEnabled(hasSelection);
                    menuViewEdit.setEnabled(hasSelection && !isDir);
                    menuProperties.setEnabled(hasSelection);

                    contextMenu.show(fileTable, e.getX(), e.getY());
                }
            }
        });

        menuUpload.addActionListener(e -> btnUpload.doClick());
        menuUploadFolder.addActionListener(e -> {
            File selected = getSelectedFile();
            if (selected != null && selected.isDirectory() && onUploadFolderCallback != null) {
                onUploadFolderCallback.accept(selected);
            }
        });

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

        menuViewEdit.addActionListener(e -> {
            File selected = getSelectedFile();
            if (selected != null && selected.isFile()) {
                Frame topFrame = (Frame) SwingUtilities.getWindowAncestor(this);
                FileEditorDialog dialog = new FileEditorDialog(topFrame, selected, false, selected.getName());
                dialog.setVisible(true);
            }
        });

        menuProperties.addActionListener(e -> {
            File selected = getSelectedFile();
            if (selected != null) {
                Frame topFrame = (Frame) SwingUtilities.getWindowAncestor(this);
                FilePropertiesDialog dialog = new FilePropertiesDialog(
                        topFrame,
                        selected.getName(),
                        selected.getAbsolutePath(),
                        selected.length(),
                        selected.lastModified(),
                        selected.isDirectory(),
                        false
                );
                dialog.setVisible(true);
            }
        });

        btnUp.addActionListener(e -> {
            if (currentDir != null && currentDir.getParentFile() != null) {
                loadDirectory(currentDir.getParentFile());
            }
        });

        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(currentDir != null ? currentDir : new File("."));
            chooser.setDialogTitle("Chọn tập tin hoặc thư mục");
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                customSelectedFile = chooser.getSelectedFile();
                if (customSelectedFile.isDirectory()) {
                    loadDirectory(customSelectedFile);
                } else {
                    if (customSelectedFile.getParentFile() != null) {
                        loadDirectory(customSelectedFile.getParentFile());
                    }
                    highlightFileInTable(customSelectedFile.getName());
                }
            }
        });

        loadDirectory(new File(System.getProperty("user.dir")));
    }

    private void applyFilter() {
        String text = filterField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(text), 0));
        }
    }

    public void setOnUploadFolderCallback(Consumer<File> callback) {
        this.onUploadFolderCallback = callback;
    }

    public void setOnDropRemoteItemCallback(BiConsumer<String, Boolean> callback) {
        this.onDropRemoteItemCallback = callback;
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
        applyFilter();
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
