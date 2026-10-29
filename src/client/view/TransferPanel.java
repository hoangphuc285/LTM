package client.view;

import client.model.FTPFile;
import client.model.TransferStatus;
import client.model.TransferTask;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TransferPanel extends JPanel {
    private final JProgressBar overallProgressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Hàng đợi truyền file: Sẵn sàng.");

    private final DefaultTableModel queueTableModel;
    private final JTable queueTable;

    private final Map<String, Integer> taskRowMap = new HashMap<>();

    private final JButton btnCancelSelected = new JButton("⛔ Hủy tác vụ");
    private final JButton btnClearCompleted = new JButton("Dọn tác vụ xong");

    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem menuCancel = new JMenuItem("⛔ Hủy tác vụ đang chọn");
    private final JMenuItem menuRemove = new JMenuItem("🗑 Xóa khỏi danh sách");

    private Consumer<String> onCancelTaskCallback;

    public TransferPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Hàng đợi truyền tập tin (Transfer Queue) "));
        setPreferredSize(new Dimension(0, 160));

        String[] columns = {"Mã", "Loại", "Tập tin", "Máy chủ", "Kích thước", "Tiến độ", "Tốc độ", "Còn lại (ETA)", "Trạng thái"};
        queueTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        queueTable = new JTable(queueTableModel);
        queueTable.setFillsViewportHeight(true);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(JLabel.CENTER);
        queueTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        queueTable.getColumnModel().getColumn(1).setPreferredWidth(75);
        queueTable.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        queueTable.getColumnModel().getColumn(2).setPreferredWidth(150);
        queueTable.getColumnModel().getColumn(3).setPreferredWidth(130);
        queueTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        queueTable.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        queueTable.getColumnModel().getColumn(5).setPreferredWidth(75);
        queueTable.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);
        queueTable.getColumnModel().getColumn(6).setPreferredWidth(85);
        queueTable.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
        queueTable.getColumnModel().getColumn(7).setPreferredWidth(90);
        queueTable.getColumnModel().getColumn(7).setCellRenderer(centerRenderer);
        queueTable.getColumnModel().getColumn(8).setPreferredWidth(95);
        queueTable.getColumnModel().getColumn(8).setCellRenderer(centerRenderer);

        add(new JScrollPane(queueTable), BorderLayout.CENTER);

        // Panel dưới: Thanh tiến độ tổng quát + Các nút thao tác
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        JPanel statusInfoPanel = new JPanel(new BorderLayout(5, 2));
        statusInfoPanel.add(statusLabel, BorderLayout.NORTH);
        overallProgressBar.setStringPainted(true);
        statusInfoPanel.add(overallProgressBar, BorderLayout.CENTER);

        bottomPanel.add(statusInfoPanel, BorderLayout.CENTER);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        actionPanel.add(btnCancelSelected);
        actionPanel.add(btnClearCompleted);
        bottomPanel.add(actionPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // Context menu cho bảng queue
        contextMenu.add(menuCancel);
        contextMenu.add(menuRemove);

        queueTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { checkPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { checkPopup(e); }

            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = queueTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && !queueTable.isRowSelected(row)) {
                        queueTable.setRowSelectionInterval(row, row);
                    }
                    boolean hasSel = queueTable.getSelectedRow() != -1;
                    menuCancel.setEnabled(hasSel);
                    menuRemove.setEnabled(hasSel);
                    contextMenu.show(queueTable, e.getX(), e.getY());
                }
            }
        });

        // Xử lý sự kiện Hủy tác vụ
        ActionListener cancelAction = e -> cancelSelectedTask();
        btnCancelSelected.addActionListener(cancelAction);
        menuCancel.addActionListener(cancelAction);

        // Xử lý xóa dòng khỏi danh sách
        menuRemove.addActionListener(e -> {
            int row = queueTable.getSelectedRow();
            if (row != -1) {
                String taskId = (String) queueTableModel.getValueAt(row, 0);
                taskRowMap.remove(taskId);
                queueTableModel.removeRow(row);
                // Đồng bộ lại chỉ số dòng
                reindexRows();
            }
        });

        btnClearCompleted.addActionListener(e -> clearCompletedTasks());
    }

    public void setOnCancelTaskCallback(Consumer<String> callback) {
        this.onCancelTaskCallback = callback;
    }

    private void cancelSelectedTask() {
        int row = queueTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tác vụ cần hủy trong bảng hàng đợi!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String taskId = (String) queueTableModel.getValueAt(row, 0);
        String currentStatus = (String) queueTableModel.getValueAt(row, 8);

        if (currentStatus.contains("Hoàn tất") || currentStatus.contains("Đã hủy")) {
            JOptionPane.showMessageDialog(this, "Tác vụ này đã kết thúc, không thể hủy!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (onCancelTaskCallback != null) {
            onCancelTaskCallback.accept(taskId);
        }
        queueTableModel.setValueAt("⛔ Đã hủy", row, 8);
    }

    public synchronized void enqueueTask(TransferTask task) {
        SwingUtilities.invokeLater(() -> {
            String typeStr = task.getType() == TransferTask.Type.UPLOAD ? "⬆ Upload" : "⬇ Download";
            String sizeStr = task.getTotalBytes() > 0 ? FTPFile.formatSize(task.getTotalBytes()) : "-";

            int rowIndex = queueTableModel.getRowCount();
            queueTableModel.addRow(new Object[]{
                    task.getId(),
                    typeStr,
                    task.getLocalFile().getName(),
                    task.getRemotePath(),
                    sizeStr,
                    String.format("%.1f%%", task.getProgressPercentage()),
                    "-",
                    task.getETA(),
                    getStatusText(task.getStatus())
            });

            taskRowMap.put(task.getId(), rowIndex);
        });
    }

    public synchronized void updateTask(TransferTask task) {
        SwingUtilities.invokeLater(() -> {
            Integer row = taskRowMap.get(task.getId());
            if (row != null && row >= 0 && row < queueTableModel.getRowCount()) {
                if (task.getTotalBytes() > 0) {
                    queueTableModel.setValueAt(FTPFile.formatSize(task.getTotalBytes()), row, 4);
                }
                queueTableModel.setValueAt(String.format("%.1f%%", task.getProgressPercentage()), row, 5);
                queueTableModel.setValueAt(FTPFile.formatSpeed(task.getSpeedBytesPerSec()), row, 6);
                queueTableModel.setValueAt(task.getETA(), row, 7);
                queueTableModel.setValueAt(getStatusText(task.getStatus()), row, 8);
            }
        });
    }

    private String getStatusText(TransferStatus status) {
        if (status == null) return "Chờ xử lý";
        switch (status) {
            case WAITING:     return "⏳ Đang chờ";
            case IN_PROGRESS: return "🔄 Đang truyền";
            case COMPLETED:   return "✅ Hoàn tất";
            case FAILED:      return "❌ Thất bại";
            case CANCELLED:   return "⛔ Đã hủy";
            case PAUSED:      return "⏸ Tạm dừng";
            default:          return status.toString();
        }
    }

    private synchronized void clearCompletedTasks() {
        for (int i = queueTableModel.getRowCount() - 1; i >= 0; i--) {
            String status = (String) queueTableModel.getValueAt(i, 8);
            if (status != null && (status.contains("Hoàn tất") || status.contains("Thất bại") || status.contains("Đã hủy"))) {
                String taskId = (String) queueTableModel.getValueAt(i, 0);
                taskRowMap.remove(taskId);
                queueTableModel.removeRow(i);
            }
        }
        reindexRows();
    }

    private void reindexRows() {
        taskRowMap.clear();
        for (int i = 0; i < queueTableModel.getRowCount(); i++) {
            String taskId = (String) queueTableModel.getValueAt(i, 0);
            taskRowMap.put(taskId, i);
        }
    }

    public void updateProgress(int percent, String statusText) {
        SwingUtilities.invokeLater(() -> {
            overallProgressBar.setValue(percent);
            statusLabel.setText(statusText);
        });
    }

    public JTable getQueueTable() { return queueTable; }
}
