package client.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class FileEditorDialog extends JDialog {
    private final JTextArea textArea = new JTextArea();
    private final JLabel lblStatus = new JLabel();
    private final JButton btnSave = new JButton("Luu va Cap nhat");
    private final JButton btnOpenExternal = new JButton("Mo bang phan mem ngoai");
    private final JButton btnClose = new JButton("Đóng");

    private final File targetFile;
    private final boolean isRemote;
    private Consumer<File> onSaveRemoteCallback;

    public FileEditorDialog(Frame parent, File file, boolean isRemote, String titleName) {
        super(parent, "Xem & Sửa tập tin: " + titleName + (isRemote ? " (Từ Máy chủ)" : " (Cục bộ)"), true);
        this.targetFile = file;
        this.isRemote = isRemote;

        setSize(750, 520);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(5, 5));

        textArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        textArea.setTabSize(4);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder(" Nội dung văn bản "));
        add(scrollPane, BorderLayout.CENTER);

        // Status bar
        JPanel bottomBar = new JPanel(new BorderLayout(5, 5));
        bottomBar.setBorder(new EmptyBorder(5, 10, 5, 10));

        lblStatus.setText("Đang đọc nội dung...");
        bottomBar.add(lblStatus, BorderLayout.WEST);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        btnPanel.add(btnSave);
        btnPanel.add(btnOpenExternal);
        btnPanel.add(btnClose);
        bottomBar.add(btnPanel, BorderLayout.EAST);

        add(bottomBar, BorderLayout.SOUTH);

        btnSave.addActionListener(e -> saveFileContent());

        btnOpenExternal.addActionListener(e -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(targetFile);
                } else {
                    JOptionPane.showMessageDialog(this, "Hệ thống không hỗ trợ mở ứng dụng ngoài!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể mở tệp: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        });

        btnClose.addActionListener(e -> dispose());

        loadFileContent();
    }

    public void setOnSaveRemoteCallback(Consumer<File> callback) {
        this.onSaveRemoteCallback = callback;
    }

    private void loadFileContent() {
        if (!targetFile.exists() || !targetFile.isFile()) {
            lblStatus.setText("Tập tin không tồn tại!");
            return;
        }

        if (targetFile.length() > 5 * 1024 * 1024) { // Lớn hơn 5MB
            textArea.setText("Tập tin quá lớn (" + (targetFile.length() / 1024 / 1024) + " MB) để hiển thị trong trình soạn thảo nhanh.\nVui lòng bấm 'Mở bằng phần mềm ngoài' để xem.");
            textArea.setEditable(false);
            btnSave.setEnabled(false);
            lblStatus.setText("Kích thước tệp lớn (> 5MB)");
            return;
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(targetFile), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                lineCount++;
            }
            textArea.setText(sb.toString());
            textArea.setCaretPosition(0);
            lblStatus.setText(String.format("Dung lượng: %d bytes | Số dòng: %d | UTF-8", targetFile.length(), lineCount));
        } catch (Exception ex) {
            textArea.setText("Không thể đọc tệp văn bản. Đây có thể là tệp nhị phân (binary/hình ảnh/nén).\nBấm 'Mở bằng phần mềm ngoài' để xem.");
            btnSave.setEnabled(false);
            lblStatus.setText("Lỗi đọc định dạng text.");
        }
    }

    private void saveFileContent() {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8))) {
            writer.write(textArea.getText());
            writer.flush();
            lblStatus.setText("Đã lưu tệp lúc: " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()));

            if (isRemote && onSaveRemoteCallback != null) {
                onSaveRemoteCallback.accept(targetFile);
            } else {
                JOptionPane.showMessageDialog(this, "Đã lưu tệp thành công!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khi lưu tệp: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }
}

