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
    private final JLabel statusLabel = new JLabel("Hang doi truyen tap tin: San sang.");

    private final DefaultTableModel queueTableModel;
    private final JTable queueTable;
    private final Map<String, Integer> taskRowMap = new HashMap<>();

    private final JButton btnPauseResume    = new JButton("Tam dung / Tiep tuc");
    private final JButton btnRetry          = new JButton("Thu lai");
    private final JButton btnCancelSelected = new JButton("Huy tac vu");
    private final JButton btnClearCompleted = new JButton("Don tac vu xong");

    private final JPopupMenu contextMenu    = new JPopupMenu();
    private final JMenuItem menuCancel      = new JMenuItem("Huy tac vu dang chon");
    private final JMenuItem menuRemove      = new JMenuItem("Xoa khoi danh sach");

    private Consumer<String> onCancelTaskCallback;

    public TransferPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Hang doi truyen tap tin (Transfer Queue) "));
        setPreferredSize(new Dimension(0, 175));

        String[] columns = {"Ma", "Loai", "Tap tin", "Duong dan tu xa",
                "Kich thuoc", "Tien do", "Toc do", "ETA", "Trang thai"};
        queueTableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        queueTable = new JTable(queueTableModel);
        queueTable.setFillsViewportHeight(true);

        queueTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        queueTable.getColumnModel().getColumn(1).setPreferredWidth(75);
        queueTable.getColumnModel().getColumn(2).setPreferredWidth(160);
        queueTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        queueTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        queueTable.getColumnModel().getColumn(5).setPreferredWidth(90);
        queueTable.getColumnModel().getColumn(6).setPreferredWidth(85);
        queueTable.getColumnModel().getColumn(7).setPreferredWidth(70);
        queueTable.getColumnModel().getColumn(8).setPreferredWidth(100);

        // Dang ky Progress Bar Renderer cho cot 5 (Tien do)
        queueTable.getColumnModel().getColumn(5).setCellRenderer(new ProgressBarRenderer());

        // Dang ky Status Renderer to mau cho cot 8 (Trang thai)
        queueTable.getColumnModel().getColumn(8).setCellRenderer(new StatusColorRenderer());

        add(new JScrollPane(queueTable), BorderLayout.CENTER);

        // Panel duoi: Thanh tien do tong quat + Nut dieu khien
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 4));
        JPanel statusInfo = new JPanel(new BorderLayout(4, 2));
        statusInfo.add(statusLabel, BorderLayout.NORTH);
        overallProgressBar.setStringPainted(true);
        overallProgressBar.setForeground(AppColors.accent());
        statusInfo.add(overallProgressBar, BorderLayout.CENTER);

        JPanel btnActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
        btnActions.add(btnPauseResume);
        btnActions.add(btnRetry);
        btnActions.add(btnCancelSelected);
        btnActions.add(btnClearCompleted);

        bottomPanel.add(statusInfo, BorderLayout.CENTER);
        bottomPanel.add(btnActions, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);

        // Context menu
        contextMenu.add(menuCancel);
        contextMenu.add(menuRemove);

        queueTable.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e)  { checkPopup(e); }
            @Override public void mouseReleased(MouseEvent e) { checkPopup(e); }

            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int r = queueTable.rowAtPoint(e.getPoint());
                    if (r >= 0 && !queueTable.isRowSelected(r)) queueTable.setRowSelectionInterval(r, r);
                    boolean hasSel = queueTable.getSelectedRow() != -1;
                    menuCancel.setEnabled(hasSel);
                    menuRemove.setEnabled(hasSel);
                    contextMenu.show(queueTable, e.getX(), e.getY());
                }
            }
        });

        ActionListener cancelAction = e -> cancelSelectedTask();
        btnCancelSelected.addActionListener(cancelAction);
        menuCancel.addActionListener(cancelAction);

        menuRemove.addActionListener(e -> {
            int r = queueTable.getSelectedRow();
            if (r != -1) {
                String id = (String) queueTableModel.getValueAt(r, 0);
                taskRowMap.remove(id);
                queueTableModel.removeRow(r);
                reindexRows();
                updateOverallProgress();
            }
        });

        btnClearCompleted.addActionListener(e -> clearCompletedTasks());
    }

    // ------------------------------------------------------------------
    // Renderer: Thanh tien trinh theo tung dong
    // ------------------------------------------------------------------
    private static class ProgressBarRenderer extends JProgressBar
            implements javax.swing.table.TableCellRenderer {

        public ProgressBarRenderer() {
            super(0, 100);
            setStringPainted(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int r, int c) {
            int progress = 0;
            if (value instanceof Number n) {
                progress = n.intValue();
            } else if (value instanceof String s) {
                try { progress = (int) Double.parseDouble(s.replace("%", "").trim()); }
                catch (Exception ignored) {}
            }
            setValue(progress);
            setString(progress + "%");

            // Lay trang thai tu cot 8 cung hang de to mau phu hop
            String statusStr = "";
            try {
                Object sv = table.getModel().getValueAt(r, 8);
                if (sv != null) statusStr = sv.toString();
            } catch (Exception ignored) {}

            boolean hasError = statusStr.contains("Loi") || statusStr.contains("loi")
                    || statusStr.contains("that bai");
            setForeground(StatusColors.forProgressBar(progress, hasError));
            return this;
        }
    }

    // ------------------------------------------------------------------
    // Renderer: To mau o Trang thai
    // ------------------------------------------------------------------
    private static class StatusColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int r, int c) {
            JLabel lbl = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, r, c);
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            if (!isSelected && value != null) {
                String s = value.toString();
                // Anh xa tu text hien thi sang TransferStatus
                if (s.contains("Hoan tat") || s.contains("hoan tat"))
                    lbl.setForeground(AppColors.success());
                else if (s.contains("Dang truyen") || s.contains("Dang"))
                    lbl.setForeground(AppColors.accent());
                else if (s.contains("Loi") || s.contains("That bai") || s.contains("loi"))
                    lbl.setForeground(AppColors.error());
                else if (s.contains("Da huy") || s.contains("Tam dung") || s.contains("Canh bao"))
                    lbl.setForeground(AppColors.warning());
                else
                    lbl.setForeground(AppColors.textSecondary());
            }
            return lbl;
        }
    }

    // ------------------------------------------------------------------
    // API cong khai
    // ------------------------------------------------------------------

    public synchronized void enqueueTask(TransferTask task) { addTask(task); }

    public synchronized void addTask(TransferTask task) {
        SwingUtilities.invokeLater(() -> {
            int row = queueTableModel.getRowCount();
            taskRowMap.put(task.getId(), row);

            queueTableModel.addRow(new Object[]{
                    task.getId(),
                    task.getType() == TransferTask.Type.UPLOAD ? "Upload" : "Download",
                    task.getLocalFile().getName(),
                    task.getRemotePath(),
                    task.getTotalBytes() > 0 ? FTPFile.formatSize(task.getTotalBytes()) : "-",
                    (int) task.getProgressPercentage(),
                    "-",
                    task.getETA(),
                    formatStatus(task.getStatus())
            });
            updateOverallProgress();
        });
    }

    public synchronized void updateTask(TransferTask task) { updateTaskProgress(task); }

    public synchronized void updateTaskProgress(TransferTask task) {
        SwingUtilities.invokeLater(() -> {
            Integer row = taskRowMap.get(task.getId());
            if (row != null && row >= 0 && row < queueTableModel.getRowCount()) {
                if (task.getTotalBytes() > 0)
                    queueTableModel.setValueAt(FTPFile.formatSize(task.getTotalBytes()), row, 4);
                queueTableModel.setValueAt((int) task.getProgressPercentage(), row, 5);
                queueTableModel.setValueAt(FTPFile.formatSpeed(task.getSpeedBytesPerSec()), row, 6);
                queueTableModel.setValueAt(task.getETA(), row, 7);
                queueTableModel.setValueAt(formatStatus(task.getStatus()), row, 8);
            }
            updateOverallProgress();
        });
    }

    private String formatStatus(TransferStatus s) {
        if (s == null) return "Dang cho";
        return switch (s) {
            case IN_PROGRESS -> "Dang truyen...";
            case COMPLETED   -> "Hoan tat";
            case FAILED      -> "Loi";
            case CANCELLED   -> "Da huy";
            case PAUSED      -> "Tam dung";
            default          -> "Dang cho";
        };
    }

    public void updateProgress(int percent, String statusText) {
        SwingUtilities.invokeLater(() -> {
            overallProgressBar.setValue(percent);
            overallProgressBar.setForeground(StatusColors.forProgressBar(percent, false));
            statusLabel.setText(statusText);
        });
    }

    private void updateOverallProgress() {
        int total = queueTableModel.getRowCount();
        if (total == 0) {
            overallProgressBar.setValue(0);
            statusLabel.setText("Hang doi truyen tap tin: San sang.");
            return;
        }
        int sum = 0, completed = 0;
        for (int i = 0; i < total; i++) {
            Object pVal = queueTableModel.getValueAt(i, 5);
            if (pVal instanceof Number n) sum += n.intValue();
            String st = (String) queueTableModel.getValueAt(i, 8);
            if (st != null && st.contains("Hoan tat")) completed++;
        }
        int avg = sum / total;
        overallProgressBar.setValue(avg);
        overallProgressBar.setForeground(StatusColors.forProgressBar(avg, false));
        statusLabel.setText(String.format("Tong tien do: %d%% | Hoan thanh %d/%d tac vu", avg, completed, total));
    }

    public synchronized void clearCompletedTasks() {
        for (int i = queueTableModel.getRowCount() - 1; i >= 0; i--) {
            String st = (String) queueTableModel.getValueAt(i, 8);
            if (st != null && (st.contains("Hoan tat") || st.contains("Da huy") || st.contains("Loi"))) {
                String id = (String) queueTableModel.getValueAt(i, 0);
                taskRowMap.remove(id);
                queueTableModel.removeRow(i);
            }
        }
        reindexRows();
        updateOverallProgress();
    }

    private void reindexRows() {
        taskRowMap.clear();
        for (int i = 0; i < queueTableModel.getRowCount(); i++) {
            taskRowMap.put((String) queueTableModel.getValueAt(i, 0), i);
        }
    }

    private void cancelSelectedTask() {
        int r = queueTable.getSelectedRow();
        if (r == -1) {
            JOptionPane.showMessageDialog(this, "Vui long chon tac vu can huy!", "Thong bao", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String id = (String) queueTableModel.getValueAt(r, 0);
        String st = (String) queueTableModel.getValueAt(r, 8);
        if (st.contains("Hoan tat") || st.contains("Da huy")) {
            JOptionPane.showMessageDialog(this, "Tac vu nay da ket thuc, khong the huy!", "Thong bao", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (onCancelTaskCallback != null) onCancelTaskCallback.accept(id);
    }

    public void setOnCancelTaskCallback(Consumer<String> cb) { this.onCancelTaskCallback = cb; }
    public JTable getQueueTable() { return queueTable; }
}
