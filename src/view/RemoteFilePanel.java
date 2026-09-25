package view;

import model.FTPFile;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class RemoteFilePanel extends JPanel {
    private final JLabel pathLabel = new JLabel("Thư mục Remote: /");
    private final JTable fileTable;
    private final DefaultTableModel tableModel;
    private final JButton btnDownload = new JButton(" <- Download ");

    public RemoteFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Máy chủ (FTP Server) "));

        tableModel = new DefaultTableModel(new String[]{"Tên File / Thư mục", "Kích thước (Bytes)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        fileTable = new JTable(tableModel);

        add(pathLabel, BorderLayout.NORTH);
        add(new JScrollPane(fileTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bottomPanel.add(btnDownload);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    public void updateFileList(List<FTPFile> files) {
        tableModel.setRowCount(0);
        if (files != null) {
            for (FTPFile file : files) {
                tableModel.addRow(new Object[]{
                        file.isDirectory() ? "[DIR] " + file.getName() : file.getName(),
                        file.getSize()
                });
            }
        }
    }

    public String getSelectedFileName() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow != -1) {
            String name = tableModel.getValueAt(selectedRow, 0).toString();
            return name.startsWith("[DIR] ") ? name.replace("[DIR] ", "") : name;
        }
        return null;
    }

    public JButton getBtnDownload() { return btnDownload; }
}