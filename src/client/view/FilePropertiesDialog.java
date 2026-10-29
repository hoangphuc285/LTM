package client.view;

import client.model.FTPFile;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.BiConsumer;

public class FilePropertiesDialog extends JDialog {
    private final JCheckBox chkOwnerR = new JCheckBox("Đọc (Read)");
    private final JCheckBox chkOwnerW = new JCheckBox("Ghi (Write)");
    private final JCheckBox chkOwnerX = new JCheckBox("Thực thi (Exec)");

    private final JCheckBox chkGroupR = new JCheckBox("Đọc (Read)");
    private final JCheckBox chkGroupW = new JCheckBox("Ghi (Write)");
    private final JCheckBox chkGroupX = new JCheckBox("Thực thi (Exec)");

    private final JCheckBox chkOtherR = new JCheckBox("Đọc (Read)");
    private final JCheckBox chkOtherW = new JCheckBox("Ghi (Write)");
    private final JCheckBox chkOtherX = new JCheckBox("Thực thi (Exec)");

    private final JTextField octalField = new JTextField("755", 6);
    private final JButton btnApplyChmod = new JButton("Áp dụng quyền (CHMOD)");
    private final JButton btnClose = new JButton("Đóng");

    private BiConsumer<String, String> onChmodCallback;
    private boolean isUpdating = false;

    public FilePropertiesDialog(Frame parent, String fileName, String fullPath, long size, long lastModified, boolean isDir, boolean isRemote) {
        super(parent, "Thuộc tính: " + fileName, true);
        setSize(460, 420);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(8, 8));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        // Thông tin chung
        JPanel infoPanel = new JPanel(new GridLayout(4, 2, 6, 6));
        infoPanel.setBorder(BorderFactory.createTitledBorder(" Thông tin tệp / thư mục "));
        infoPanel.add(new JLabel("Tên:"));
        infoPanel.add(new JLabel(fileName));

        infoPanel.add(new JLabel("Đường dẫn:"));
        JLabel lblPath = new JLabel(fullPath);
        lblPath.setToolTipText(fullPath);
        infoPanel.add(lblPath);

        infoPanel.add(new JLabel("Kích thước:"));
        infoPanel.add(new JLabel(isDir ? "Thư mục" : FTPFile.formatSize(size) + " (" + size + " bytes)"));

        infoPanel.add(new JLabel("Ngày sửa đổi:"));
        String dateStr = lastModified > 0 ? new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(lastModified)) : "-";
        infoPanel.add(new JLabel(dateStr));
        mainPanel.add(infoPanel);

        mainPanel.add(Box.createVerticalStrut(10));

        // Phân quyền (Permissions)
        JPanel permPanel = new JPanel(new GridLayout(3, 4, 6, 6));
        permPanel.setBorder(BorderFactory.createTitledBorder(" Phân quyền tập tin (Unix Permissions) "));

        permPanel.add(new JLabel("Chủ sở hữu (Owner):"));
        permPanel.add(chkOwnerR);
        permPanel.add(chkOwnerW);
        permPanel.add(chkOwnerX);

        permPanel.add(new JLabel("Nhóm (Group):"));
        permPanel.add(chkGroupR);
        permPanel.add(chkGroupW);
        permPanel.add(chkGroupX);

        permPanel.add(new JLabel("Công khai (Other):"));
        permPanel.add(chkOtherR);
        permPanel.add(chkOtherW);
        permPanel.add(chkOtherX);

        mainPanel.add(permPanel);

        mainPanel.add(Box.createVerticalStrut(8));

        // Octal mode panel
        JPanel octalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        octalPanel.add(new JLabel("Mã quyền (Octal mode):"));
        octalPanel.add(octalField);
        mainPanel.add(octalPanel);

        add(mainPanel, BorderLayout.CENTER);

        // Nút dưới cùng
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        if (isRemote) {
            bottomBar.add(btnApplyChmod);
        }
        bottomBar.add(btnClose);
        add(bottomBar, BorderLayout.SOUTH);

        // Mặc định 755 cho thư mục, 644 cho file
        if (isDir) {
            setFromOctal("755");
        } else {
            setFromOctal("644");
        }

        // Sự kiện checkbox -> cập nhật octal
        JCheckBox[] allBoxes = {chkOwnerR, chkOwnerW, chkOwnerX, chkGroupR, chkGroupW, chkGroupX, chkOtherR, chkOtherW, chkOtherX};
        for (JCheckBox cb : allBoxes) {
            cb.addActionListener(e -> updateOctalFromBoxes());
        }

        octalField.addActionListener(e -> setFromOctal(octalField.getText().trim()));

        btnApplyChmod.addActionListener(e -> {
            String mode = octalField.getText().trim();
            if (onChmodCallback != null) {
                onChmodCallback.accept(mode, fileName);
                dispose();
            }
        });

        btnClose.addActionListener(e -> dispose());
    }

    public void setOnChmodCallback(BiConsumer<String, String> callback) {
        this.onChmodCallback = callback;
    }

    private void updateOctalFromBoxes() {
        if (isUpdating) return;
        int owner = (chkOwnerR.isSelected() ? 4 : 0) + (chkOwnerW.isSelected() ? 2 : 0) + (chkOwnerX.isSelected() ? 1 : 0);
        int group = (chkGroupR.isSelected() ? 4 : 0) + (chkGroupW.isSelected() ? 2 : 0) + (chkGroupX.isSelected() ? 1 : 0);
        int other = (chkOtherR.isSelected() ? 4 : 0) + (chkOtherW.isSelected() ? 2 : 0) + (chkOtherX.isSelected() ? 1 : 0);
        octalField.setText("" + owner + group + other);
    }

    private void setFromOctal(String octal) {
        if (octal.length() != 3) return;
        isUpdating = true;
        try {
            int owner = Character.getNumericValue(octal.charAt(0));
            int group = Character.getNumericValue(octal.charAt(1));
            int other = Character.getNumericValue(octal.charAt(2));

            chkOwnerR.setSelected((owner & 4) != 0);
            chkOwnerW.setSelected((owner & 2) != 0);
            chkOwnerX.setSelected((owner & 1) != 0);

            chkGroupR.setSelected((group & 4) != 0);
            chkGroupW.setSelected((group & 2) != 0);
            chkGroupX.setSelected((group & 1) != 0);

            chkOtherR.setSelected((other & 4) != 0);
            chkOtherW.setSelected((other & 2) != 0);
            chkOtherX.setSelected((other & 1) != 0);

            octalField.setText(octal);
        } catch (Exception ignored) {}
        isUpdating = false;
    }
}

