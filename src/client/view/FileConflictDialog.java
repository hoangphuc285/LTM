package client.view;

import javax.swing.*;
import java.awt.*;

public class FileConflictDialog extends JDialog {
    public enum ConflictAction { OVERWRITE, SKIP, RENAME, CANCEL }

    private ConflictAction selectedAction = ConflictAction.SKIP;
    private boolean applyToAll = false;

    public FileConflictDialog(Window parent, String fileName, long existingSize, long newSize) {
        super(parent, "Xác nhận xung đột tập tin", ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout(10, 10));
        setSize(500, 220);
        setLocationRelativeTo(parent);
        setResizable(false);

        JPanel contentPanel = new JPanel(new GridLayout(4, 1, 4, 4));
        contentPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 10, 16));

        JLabel lblMsg = new JLabel("<html><b>Tập tin đích đã tồn tại:</b> <font color='#0066cc'>" + fileName + "</font></html>");
        JLabel lblDetails = new JLabel("Kích thước hiện có: " + (existingSize > 0 ? client.model.FTPFile.formatSize(existingSize) : "Không rõ")
                + " | Tập tin mới: " + (newSize > 0 ? client.model.FTPFile.formatSize(newSize) : "Đang kiểm tra"));
        JCheckBox chkAll = new JCheckBox("Áp dụng hành động này cho tất cả các file trùng lặp còn lại");

        contentPanel.add(lblMsg);
        contentPanel.add(lblDetails);
        contentPanel.add(new JLabel("Vui lòng chọn cách xử lý:"));
        contentPanel.add(chkAll);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 10));
        JButton btnOverwrite = new JButton("Ghi đè (Overwrite)");
        JButton btnSkip = new JButton("Bỏ qua (Skip)");
        JButton btnRename = new JButton("Tự đổi tên");
        JButton btnCancel = new JButton("Hủy bỏ");

        btnOverwrite.addActionListener(e -> {
            selectedAction = ConflictAction.OVERWRITE;
            applyToAll = chkAll.isSelected();
            dispose();
        });
        btnSkip.addActionListener(e -> {
            selectedAction = ConflictAction.SKIP;
            applyToAll = chkAll.isSelected();
            dispose();
        });
        btnRename.addActionListener(e -> {
            selectedAction = ConflictAction.RENAME;
            applyToAll = chkAll.isSelected();
            dispose();
        });
        btnCancel.addActionListener(e -> {
            selectedAction = ConflictAction.CANCEL;
            dispose();
        });

        btnPanel.add(btnOverwrite);
        btnPanel.add(btnSkip);
        btnPanel.add(btnRename);
        btnPanel.add(btnCancel);

        add(contentPanel, BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);
    }

    public ConflictAction getSelectedAction() { return selectedAction; }
    public boolean isApplyToAll() { return applyToAll; }
}

