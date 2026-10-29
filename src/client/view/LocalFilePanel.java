package client.view;

import client.model.FTPFile;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class LocalFilePanel extends JPanel {
    private final JTextField pathField = new JTextField();
    private final JButton btnGo = new JButton("Đi tới");
    private final JButton btnUp = new JButton("Len");
    private final JTextField filterField = new JTextField();
    private final JButton btnClearFilter = new JButton("X");

    private final JTable fileTable;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> sorter;

    private final JButton btnBrowse = new JButton("Chon File...");
    private final JButton btnUpload = new JButton("Upload ->");

    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem menuUpload       = new JMenuItem("Tai len tap tin nay (Upload)");
    private final JMenuItem menuUploadFolder = new JMenuItem("Tai len ca thu muc (De quy)");
    private final JMenuItem menuOpen         = new JMenuItem("Mo thu muc (Enter)");
    private final JMenuItem menuNewFolder    = new JMenuItem("Tao thu muc moi");
    private final JMenuItem menuRename       = new JMenuItem("Doi ten (F2)");
    private final JMenuItem menuDelete       = new JMenuItem("Xoa (Delete)");
    private final JMenuItem menuViewEdit     = new JMenuItem("Xem / Sua tap tin");
    private final JMenuItem menuProperties   = new JMenuItem("Thuoc tinh tap tin");
    private final JMenuItem menuRefresh      = new JMenuItem("Lam moi danh sach (F5)");

    private Consumer<File> onUploadFolderCallback;
    private BiConsumer<String, Boolean> onDropRemoteItemCallback;
    private File currentDir = new File(System.getProperty("user.dir"));

    public LocalFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Cục bộ (Local) "));

        String[] columnNames = {"Tên Tập tin / Thư mục", "Kích thước", "Ngày sửa đổi"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override
            public Class<?> getColumnClass(int col) {
                if (col == 1) return FTPFile.FileSizeItem.class;
                if (col == 2) return FTPFile.FileDateItem.class;
                return String.class;
            }
        };

        fileTable = new JTable(tableModel);
        fileTable.getColumnModel().getColumn(0).setCellRenderer(new FileTableCellRenderer());
        fileTable.getColumnModel().getColumn(1).setCellRenderer(new FileTableCellRenderer());
        fileTable.getColumnModel().getColumn(2).setCellRenderer(new FileTableCellRenderer());

        sorter = new TableRowSorter<>(tableModel);
        sorter.setComparator(0, (Comparator<String>) (s1, s2) -> {
            if ("..".equals(s1)) return -1;
            if ("..".equals(s2)) return 1;
            return s1.compareToIgnoreCase(s2);
        });
        fileTable.setRowSorter(sorter);

        setupDragAndDrop();
        setupKeyBindings();

        // 1. Thanh điều hướng
        JPanel navPanel = new JPanel(new BorderLayout(4, 4));
        navPanel.add(new JLabel("Đường dẫn: "), BorderLayout.WEST);
        navPanel.add(pathField, BorderLayout.CENTER);
        JPanel navEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        navEast.add(btnGo);
        navEast.add(btnUp);
        navPanel.add(navEast, BorderLayout.EAST);

        // 2. Thanh lọc file
        JPanel filterPanel = new JPanel(new BorderLayout(4, 4));
        filterPanel.add(new JLabel("Loc file: "), BorderLayout.WEST);
        filterPanel.add(filterField, BorderLayout.CENTER);
        btnClearFilter.setToolTipText("Xóa bộ lọc");
        btnClearFilter.setMargin(new Insets(1, 5, 1, 5));
        filterPanel.add(btnClearFilter, BorderLayout.EAST);

        JPanel topContainer = new JPanel(new GridLayout(2, 1, 2, 4));
        topContainer.add(navPanel);
        topContainer.add(filterPanel);
        add(topContainer, BorderLayout.NORTH);

        add(new JScrollPane(fileTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        bottomPanel.add(btnBrowse);
        bottomPanel.add(btnUpload);
        add(bottomPanel, BorderLayout.SOUTH);

        // Context menu
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

        initEvents();
        loadDirectory(currentDir);
    }

    private void setupDragAndDrop() {
        fileTable.setDragEnabled(true);
        fileTable.setDropMode(DropMode.ON);

        TransferHandler handler = new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return COPY; }
            @Override
            protected Transferable createTransferable(JComponent c) {
                File sel = getSelectedFile();
                if (sel != null && sel.exists()) {
                    return new Transferable() {
                        @Override public DataFlavor[] getTransferDataFlavors() {
                            return new DataFlavor[]{DataFlavor.javaFileListFlavor, DataFlavor.stringFlavor};
                        }
                        @Override public boolean isDataFlavorSupported(DataFlavor f) {
                            return f.equals(DataFlavor.javaFileListFlavor) || f.equals(DataFlavor.stringFlavor);
                        }
                        @Override public Object getTransferData(DataFlavor f) {
                            if (f.equals(DataFlavor.javaFileListFlavor)) return Collections.singletonList(sel);
                            return sel.getAbsolutePath();
                        }
                    };
                }
                return null;
            }

            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.stringFlavor) || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    if (support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                        String data = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                        if (data != null && data.startsWith("FTP_REMOTE:") && onDropRemoteItemCallback != null) {
                            String[] p = data.split(":", 3);
                            onDropRemoteItemCallback.accept(p[2], "DIR".equals(p[1]));
                            return true;
                        }
                    }
                } catch (Exception ignored) {}
                return false;
            }
        };

        fileTable.setTransferHandler(handler);
        setTransferHandler(handler);
    }

    private void setupKeyBindings() {
        fileTable.registerKeyboardAction(e -> refresh(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        fileTable.registerKeyboardAction(e -> handleRename(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        fileTable.registerKeyboardAction(e -> handleDelete(),
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        fileTable.registerKeyboardAction(e -> handleEnterAction(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void initEvents() {
        btnGo.addActionListener(e -> loadDirectory(new File(pathField.getText().trim())));
        pathField.addActionListener(e -> btnGo.doClick());

        btnUp.addActionListener(e -> {
            if (currentDir != null && currentDir.getParentFile() != null) {
                loadDirectory(currentDir.getParentFile());
            }
        });

        btnClearFilter.addActionListener(e -> filterField.setText(""));
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        btnBrowse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(currentDir);
            chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                if (f.isDirectory()) loadDirectory(f);
                else loadDirectory(f.getParentFile());
            }
        });

        fileTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) handleEnterAction();
            }
            @Override public void mousePressed(MouseEvent e) { checkPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { checkPopup(e); }

            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int r = fileTable.rowAtPoint(e.getPoint());
                    if (r >= 0 && !fileTable.isRowSelected(r)) fileTable.setRowSelectionInterval(r, r);

                    File sel = getSelectedFile();
                    boolean hasSel = (sel != null);
                    boolean isDir = hasSel && sel.isDirectory();

                    menuUpload.setEnabled(hasSel && !isDir);
                    menuUploadFolder.setEnabled(isDir);
                    menuOpen.setEnabled(isDir);
                    menuRename.setEnabled(hasSel);
                    menuDelete.setEnabled(hasSel);
                    menuViewEdit.setEnabled(hasSel && !isDir);
                    menuProperties.setEnabled(hasSel);

                    contextMenu.show(fileTable, e.getX(), e.getY());
                }
            }
        });

        menuUpload.addActionListener(e -> btnUpload.doClick());
        menuUploadFolder.addActionListener(e -> {
            File sel = getSelectedFile();
            if (sel != null && sel.isDirectory() && onUploadFolderCallback != null) {
                onUploadFolderCallback.accept(sel);
            }
        });
        menuOpen.addActionListener(e -> handleEnterAction());
        menuNewFolder.addActionListener(e -> handleNewFolder());
        menuRename.addActionListener(e -> handleRename());
        menuDelete.addActionListener(e -> handleDelete());
        menuViewEdit.addActionListener(e -> {
            File sel = getSelectedFile();
            if (sel != null && sel.isFile()) {
                FileEditorDialog dlg = new FileEditorDialog((Frame) SwingUtilities.getWindowAncestor(this), sel, false, sel.getName());
                dlg.setVisible(true);
            }
        });
        menuProperties.addActionListener(e -> {
            File sel = getSelectedFile();
            if (sel != null) {
                FilePropertiesDialog dlg = new FilePropertiesDialog((Frame) SwingUtilities.getWindowAncestor(this),
                        sel.getName(), sel.getAbsolutePath(), sel.length(), sel.lastModified(), sel.isDirectory(), false);
                dlg.setVisible(true);
            }
        });
        menuRefresh.addActionListener(e -> refresh());
    }

    private void handleEnterAction() {
        int row = fileTable.getSelectedRow();
        if (row == -1) return;
        int mRow = fileTable.convertRowIndexToModel(row);
        String name = (String) tableModel.getValueAt(mRow, 0);

        if ("..".equals(name)) {
            if (currentDir.getParentFile() != null) loadDirectory(currentDir.getParentFile());
            return;
        }

        File target = new File(currentDir, name);
        if (target.isDirectory()) {
            loadDirectory(target);
        } else {
            btnUpload.doClick();
        }
    }

    private void handleNewFolder() {
        String dirName = JOptionPane.showInputDialog(this, "Nhập tên thư mục mới:", "Tạo Thư Mục Cục Bộ", JOptionPane.PLAIN_MESSAGE);
        if (dirName != null && !dirName.trim().isEmpty()) {
            File nd = new File(currentDir, dirName.trim());
            if (nd.mkdir()) refresh();
            else JOptionPane.showMessageDialog(this, "Không thể tạo thư mục!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleRename() {
        File sel = getSelectedFile();
        if (sel == null) return;
        String newName = JOptionPane.showInputDialog(this, "Đổi tên thành:", sel.getName());
        if (newName != null && !newName.trim().isEmpty()) {
            if (sel.renameTo(new File(sel.getParentFile(), newName.trim()))) refresh();
            else JOptionPane.showMessageDialog(this, "Đổi tên thất bại!", "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleDelete() {
        File sel = getSelectedFile();
        if (sel == null) return;
        int confirm = JOptionPane.showConfirmDialog(this, "Bạn chắc chắn muốn xóa: " + sel.getName() + "?", "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            deleteRecursive(sel);
            refresh();
        }
    }

    private boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] subs = f.listFiles();
            if (subs != null) for (File s : subs) deleteRecursive(s);
        }
        return f.delete();
    }

    private void applyFilter() {
        String text = filterField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 0));
        }
    }

    public void loadDirectory(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;
        this.currentDir = dir;
        this.pathField.setText(dir.getAbsolutePath());
        this.tableModel.setRowCount(0);

        if (dir.getParentFile() != null) {
            tableModel.addRow(new Object[]{"..", new FTPFile.FileSizeItem(-1, true), new FTPFile.FileDateItem(0)});
        }

        File[] files = dir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            for (File f : files) {
                tableModel.addRow(new Object[]{
                        f.getName(),
                        new FTPFile.FileSizeItem(f.isDirectory() ? -1 : f.length(), f.isDirectory()),
                        new FTPFile.FileDateItem(f.lastModified())
                });
            }
        }
    }

    public File getSelectedFile() {
        int r = fileTable.getSelectedRow();
        if (r == -1) return null;
        int mRow = fileTable.convertRowIndexToModel(r);
        String name = (String) tableModel.getValueAt(mRow, 0);
        if ("..".equals(name)) return null;
        return new File(currentDir, name);
    }

    public boolean isSelectedDirectory() {
        File sel = getSelectedFile();
        return sel != null && sel.isDirectory();
    }

    public void refresh() { loadDirectory(currentDir); }
    public File getCurrentDirectory() { return currentDir; }
    public JButton getBtnUpload() { return btnUpload; }
    public JButton getBtnBrowse() { return btnBrowse; }
    public JButton getBtnUp() { return btnUp; }
    public JButton getBtnGo() { return btnGo; }
    public JTable getFileTable() { return fileTable; }
    public void setOnUploadFolderCallback(Consumer<File> cb) { this.onUploadFolderCallback = cb; }
    public void setOnDropRemoteItemCallback(BiConsumer<String, Boolean> cb) { this.onDropRemoteItemCallback = cb; }
    public void focusPathField() { pathField.requestFocusInWindow(); pathField.selectAll(); }
}
