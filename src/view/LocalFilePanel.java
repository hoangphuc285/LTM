package view;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;

public class LocalFilePanel extends JPanel {
    private final JLabel pathLabel = new JLabel("Thư mục hiện tại: ");
    private final JTable fileTable;
    private final DefaultTableModel tableModel;
    private final JButton btnUpload = new JButton(" Upload -> ");
    private File currentDir;

    public LocalFilePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Cục bộ (Local) "));

        tableModel = new DefaultTableModel(new String[]{"Tên File", "Kích thước (Bytes)"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        fileTable = new JTable(tableModel);

        add(pathLabel, BorderLayout.NORTH);
        add(new JScrollPane(fileTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        bottomPanel.add(btnUpload);
        add(bottomPanel, BorderLayout.SOUTH);

        // Khởi tạo thư mục chạy project hiện tại
        loadDirectory(new File(System.getProperty("user.dir")));
    }

    public void loadDirectory(File dir) {
        this.currentDir = dir;
        pathLabel.setText("Đường dẫn: " + dir.getAbsolutePath());
        tableModel.setRowCount(0);

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                tableModel.addRow(new Object[]{
                        f.isDirectory() ? "[DIR] " + f.getName() : f.getName(),
                        f.length()
                });
            }
        }
    }

    public File getSelectedFile() {
        int selectedRow = fileTable.getSelectedRow();
        if (selectedRow != -1 && currentDir != null) {
            String fileName = tableModel.getValueAt(selectedRow, 0).toString();
            if (fileName.startsWith("[DIR] ")) {
                fileName = fileName.replace("[DIR] ", "");
            }
            return new File(currentDir, fileName);
        }
        return null;
    }

    public JButton getBtnUpload() { return btnUpload; }
}