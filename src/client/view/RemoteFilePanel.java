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
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class RemoteFilePanel extends JPanel {
    private final JTextField pathField = new JTextField("/");
    private final JButton btnGo = new JButton("Đi tới");
    private final JButton btnUp = new JButton("Len");
    private final JButton btnBookmarks = new JButton("Dau trang");
    private final JTextField filterField = new JTextField();
    private final JButton btnClearFilter = new JButton("X");
    private final JButton btnSearch = new JButton("Tim");

    private final JTable fileTable;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> sorter;

    private final JButton btnDownload = new JButton("Tai ve");
    private final JButton btnNewFolder = new JButton("Tạo Thư Mục");
    private final JButton btnRename = new JButton("Đổi Tên");
    private final JButton btnDelete = new JButton("Xóa");

    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem menuDownload       = new JMenuItem("Tai ve tap tin nay (Download)");
    private final JMenuItem menuDownloadFolder = new JMenuItem("Tai ve ca thu muc nay (De quy)");
    private final JMenuItem menuOpenFolder     = new JMenuItem("Mo thu muc nay (Enter)");
    private final JMenuItem menuNewFolder      = new JMenuItem("Tao thu muc moi");
    private final JMenuItem menuRename         = new JMenuItem("Doi ten (F2)");
    private final JMenuItem menuDelete         = new JMenuItem("Xoa (Delete)");
    private final JMenuItem menuViewEdit       = new JMenuItem("Xem / Sua tap tin");
    private final JMenuItem menuProperties     = new JMenuItem("Thuoc tinh & Doi quyen (CHMOD)");
    private final JMenuItem menuRefresh        = new JMenuItem("Lam moi danh sach (F5)");

    private Consumer<String> onNavigateCallback;
    private Runnable onRefreshCallback;
    private Consumer<String> onDownloadFolderCallback;
    private Consumer<List<File>> onDropLocalFilesCallback;
    private Consumer<String> onViewEditCallback;
    private Consumer<String> onPropertiesCallback;
    private Runnable onSearchCallback;

    public RemoteFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Máy chủ (FTP Server) "));

        String[] columnNames = {"Tên File / Thư mục", "Kích thước", "Quyền", "Chủ sở hữu", "Ngày sửa đổi"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override
            public Class<?> getColumnClass(int col) {
                if (col == 1) return FTPFile.FileSizeItem.class;
                if (col == 4) return FTPFile.FileDateItem.class;
                return String.class;
            }
        };

        fileTable = new JTable(tableModel);
        for (int i = 0; i < columnNames.length; i++) {
            fileTable.getColumnModel().getColumn(i).setCellRenderer(new FileTableCellRenderer());
        }

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
        navPanel.add(new JLabel("Đường dẫn máy chủ: "), BorderLayout.WEST);
        navPanel.add(pathField, BorderLayout.CENTER);
        JPanel navEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        navEast.add(btnBookmarks);
        navEast.add(btnGo);
        navEast.add(btnUp);
        navPanel.add(navEast, BorderLayout.EAST);

        // 2. Thanh lọc file & nút Tìm kiếm
        JPanel filterPanel = new JPanel(new BorderLayout(4, 4));
        filterPanel.add(new JLabel("Loc file: "), BorderLayout.WEST);
        filterPanel.add(filterField, BorderLayout.CENTER);
        btnClearFilter.setToolTipText("Xóa bộ lọc");
        btnClearFilter.setMargin(new Insets(1, 5, 1, 5));

        JPanel filterEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        filterEast.add(btnClearFilter);
        filterEast.add(btnSearch);
        filterPanel.add(filterEast, BorderLayout.EAST);

        JPanel topContainer = new JPanel(new GridLayout(2, 1, 2, 4));
        topContainer.add(navPanel);
        topContainer.add(filterPanel);
        add(topContainer, BorderLayout.NORTH);

        add(new JScrollPane(fileTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bottomPanel.add(btnDownload);
        bottomPanel.add(btnNewFolder);
        bottomPanel.add(btnRename);
        bottomPanel.add(btnDelete);
        add(bottomPanel, BorderLayout.SOUTH);

        // Context menu
        contextMenu.add(menuDownload);
        contextMenu.add(menuDownloadFolder);
        contextMenu.add(menuOpenFolder);
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
    }

    private void setupDragAndDrop() {
        fileTable.setDragEnabled(true);
        fileTable.setDropMode(DropMode.ON);

        TransferHandler handler = new TransferHandler() {
            @Override public int getSourceActions(JComponent c) { return COPY; }
            @Override
            protected Transferable createTransferable(JComponent c) {
                String sel = getSelectedFileName();
                if (sel != null && !"..".equals(sel)) {
                    boolean isDir = isSelectedDirectory();
                    return new StringSelection("FTP_REMOTE:" + (isDir ? "DIR:" : "FILE:") + sel);
                }
                return null;
            }

            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                try {
                    List<File> files = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (files != null && !files.isEmpty() && onDropLocalFilesCallback != null) {
                        onDropLocalFilesCallback.accept(files);
                        return true;
                    }
                } catch (Exception ignored) {}
                return false;
            }
        };

        fileTable.setTransferHandler(handler);
        setTransferHandler(handler);
    }

    private void setupKeyBindings() {
        fileTable.registerKeyboardAction(e -> { if (onRefreshCallback != null) onRefreshCallback.run(); },
                KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        fileTable.registerKeyboardAction(e -> btnRename.doClick(),
                KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        fileTable.registerKeyboardAction(e -> btnDelete.doClick(),
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        fileTable.registerKeyboardAction(e -> handleEnterAction(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void initEvents() {
        btnGo.addActionListener(e -> {
            if (onNavigateCallback != null) onNavigateCallback.accept(pathField.getText().trim());
        });
        pathField.addActionListener(e -> btnGo.doClick());

        btnClearFilter.addActionListener(e -> filterField.setText(""));
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
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

                    boolean hasSel = fileTable.getSelectedRow() != -1;
                    boolean isDir = hasSel && isSelectedDirectory();
                    String name = getSelectedFileName();
                    boolean isUp = "..".equals(name);

                    menuDownload.setEnabled(hasSel && !isDir && !isUp);
                    menuDownloadFolder.setEnabled(hasSel && isDir && !isUp);
                    menuOpenFolder.setEnabled(isDir);
                    menuRename.setEnabled(hasSel && !isUp);
                    menuDelete.setEnabled(hasSel && !isUp);
                    menuViewEdit.setEnabled(hasSel && !isDir && !isUp);
                    menuProperties.setEnabled(hasSel && !isUp);

                    contextMenu.show(fileTable, e.getX(), e.getY());
                }
            }
        });

        menuDownload.addActionListener(e -> btnDownload.doClick());
        menuDownloadFolder.addActionListener(e -> {
            if (onDownloadFolderCallback != null) onDownloadFolderCallback.accept(getSelectedFileName());
        });
        menuOpenFolder.addActionListener(e -> handleEnterAction());
        menuNewFolder.addActionListener(e -> btnNewFolder.doClick());
        menuRename.addActionListener(e -> btnRename.doClick());
        menuDelete.addActionListener(e -> btnDelete.doClick());
        menuViewEdit.addActionListener(e -> {
            if (onViewEditCallback != null) onViewEditCallback.accept(getSelectedFileName());
        });
        menuProperties.addActionListener(e -> {
            if (onPropertiesCallback != null) onPropertiesCallback.accept(getSelectedFileName());
        });
        menuRefresh.addActionListener(e -> {
            if (onRefreshCallback != null) onRefreshCallback.run();
        });
        btnSearch.addActionListener(e -> {
            if (onSearchCallback != null) onSearchCallback.run();
        });
    }

    private void handleEnterAction() {
        String sel = getSelectedFileName();
        if (sel == null) return;
        if ("..".equals(sel)) {
            btnUp.doClick();
            return;
        }
        if (isSelectedDirectory()) {
            if (onNavigateCallback != null) {
                String cur = pathField.getText().trim();
                String target = cur.endsWith("/") ? cur + sel : cur + "/" + sel;
                onNavigateCallback.accept(target);
            }
        } else {
            btnDownload.doClick();
        }
    }

    private void applyFilter() {
        String text = filterField.getText().trim();
        if (text.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, 0));
        }
    }

    public void updateFileList(List<FTPFile> files) {
        tableModel.setRowCount(0);
        String curPath = pathField.getText().trim();
        if (!curPath.equals("/") && !curPath.isEmpty()) {
            tableModel.addRow(new Object[]{"..", new FTPFile.FileSizeItem(-1, true), "drwxr-xr-x", "root", new FTPFile.FileDateItem(0)});
        }

        if (files != null) {
            files.sort((f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            for (FTPFile f : files) {
                tableModel.addRow(new Object[]{
                        f.getName(),
                        new FTPFile.FileSizeItem(f.isDirectory() ? -1 : f.getSize(), f.isDirectory()),
                        f.getPermissions(),
                        f.getOwner(),
                        new FTPFile.FileDateItem(f.getLastModified())
                });
            }
        }
    }

    public String getSelectedFileName() {
        int r = fileTable.getSelectedRow();
        if (r == -1) return null;
        int mRow = fileTable.convertRowIndexToModel(r);
        return (String) tableModel.getValueAt(mRow, 0);
    }

    public boolean isSelectedDirectory() {
        int r = fileTable.getSelectedRow();
        if (r == -1) return false;
        int mRow = fileTable.convertRowIndexToModel(r);
        Object val = tableModel.getValueAt(mRow, 1);
        return val != null && "-".equals(val.toString());
    }

    public void setCurrentPath(String p) { pathField.setText(p); }
    public String getCurrentPath() { return pathField.getText().trim(); }
    public JButton getBtnDownload() { return btnDownload; }
    public JButton getBtnNewFolder() { return btnNewFolder; }
    public JButton getBtnRename() { return btnRename; }
    public JButton getBtnDelete() { return btnDelete; }
    public JButton getBtnUp() { return btnUp; }
    public JButton getBtnGo() { return btnGo; }
    public JButton getBtnBookmarks() { return btnBookmarks; }
    public JButton getBtnSearch() { return btnSearch; }
    public JTable getFileTable() { return fileTable; }

    public void setOnNavigateCallback(Consumer<String> cb) { this.onNavigateCallback = cb; }
    public void setOnRefreshCallback(Runnable cb) { this.onRefreshCallback = cb; }
    public void setOnDownloadFolderCallback(Consumer<String> cb) { this.onDownloadFolderCallback = cb; }
    public void setOnDropLocalFilesCallback(Consumer<List<File>> cb) { this.onDropLocalFilesCallback = cb; }
    public void setOnViewEditCallback(Consumer<String> cb) { this.onViewEditCallback = cb; }
    public void setOnPropertiesCallback(Consumer<String> cb) { this.onPropertiesCallback = cb; }
    public void setOnSearchCallback(Runnable cb) { this.onSearchCallback = cb; }
    public void focusPathField() { pathField.requestFocusInWindow(); pathField.selectAll(); }
}
