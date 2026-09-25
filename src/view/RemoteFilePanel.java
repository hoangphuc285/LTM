package view;

import model.FTPFile;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class RemoteFilePanel extends JPanel {
    private final JLabel pathLabel = new JLabel("Đường dẫn máy chủ: /");
    private final JTable fileTable;
    private final DefaultTableModel tableModel;

    private final JButton btnUp = new JButton("⬆ Lên thư mục cha");
    private final JButton btnDownload = new JButton("Tải về");
    private final JButton btnNewFolder = new JButton("Tạo Thư Mục");
    private final JButton btnDelete = new JButton("Xóa");
    private final JButton btnRename = new JButton("Đổi Tên");

    public RemoteFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Máy chủ (FTP Server) "));

        tableModel = new DefaultTableModel(new String[]{"Tên File / Thư mục", "Kích thước (Bytes)", "Loại"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        fileTable = new JTable(tableModel);

        // Thanh trên chứa Đường dẫn & Nút Lên Thư Mục Cha
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        topPanel.add(pathLabel, BorderLayout.CENTER);
        topPanel.add(btnUp, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);

        add(new JScrollPane(fileTable), BorderLayout.CENTER);

        // Thanh công cụ thao tác phía dưới
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(btnDownload);
        bottomPanel.add(btnNewFolder);
        bottomPanel.add(btnRename);
        bottomPanel.add(btnDelete);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void setCurrentPath(String path) {
        pathLabel.setText("Đường dẫn máy chủ: " + path);
    }

    public void updateFileList(List<FTPFile> files) {
        tableModel.setRowCount(0);
        if (files != null) {
            for (FTPFile file : files) {
                tableModel.addRow(new Object[]{
                        file.getName(),
                        file.isDirectory() ? "-" : file.getSize(),
                        file.isDirectory() ? "Thư mục" : "Tập tin"
                });
            }
        }
    }

    public String getSelectedFileName() {
        int row = fileTable.getSelectedRow();
        return (row != -1) ? (String) tableModel.getValueAt(row, 0) : null;
    }

    public boolean isSelectedDirectory() {
        int row = fileTable.getSelectedRow();
        return (row != -1) && "Thư mục".equals(tableModel.getValueAt(row, 2));
    }

    public JTable getFileTable() { return fileTable; }
    public JButton getBtnUp() { return btnUp; }
    public JButton getBtnDownload() { return btnDownload; }
    public JButton getBtnNewFolder() { return btnNewFolder; }
    public JButton getBtnDelete() { return btnDelete; }
    public JButton getBtnRename() { return btnRename; }

}