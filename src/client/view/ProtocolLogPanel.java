package client.view;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ProtocolLogPanel extends JPanel {
    private final JTextPane textPane = new JTextPane();
    private final StyledDocument doc = textPane.getStyledDocument();
    private final JTextField txtFilter = new JTextField(12);

    public ProtocolLogPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Nhat ky trao doi giao thuc (Protocol Log) "));
        setPreferredSize(new Dimension(0, 140));

        textPane.setEditable(false);
        textPane.setFont(UITheme.MONO_FONT);
        textPane.setBackground(AppColors.logBg());
        JScrollPane scrollPane = new JScrollPane(textPane);

        // Toolbar nho ben tren cua so nhat ky
        JPanel toolBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        toolBar.add(new JLabel("Loc:"));
        toolBar.add(txtFilter);

        JButton btnClear = new JButton("Xoa log");
        btnClear.setToolTipText("Xoa toan bo noi dung nhat ky");
        btnClear.addActionListener(e -> textPane.setText(""));

        JButton btnSave = new JButton("Luu ra file...");
        btnSave.setToolTipText("Luu toan bo nhat ky ra tep van ban .txt");
        btnSave.addActionListener(e -> saveLogToFile());

        toolBar.add(btnClear);
        toolBar.add(btnSave);

        add(toolBar, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Them mot dong vao nhat ky voi mau sac phu hop.
     * Mat khau trong lenh PASS luon duoc che dau.
     */
    public void appendLog(String message) {
        if (message == null) return;

        // Bao mat: che dau mat khau trong lenh PASS
        if (message.toUpperCase().contains("PASS ")) {
            message = message.replaceAll("(?i)PASS\\s+.*", "PASS ******");
        }

        final String finalMsg = message;
        SwingUtilities.invokeLater(() -> {
            try {
                String filter = txtFilter.getText().trim();
                if (!filter.isEmpty() && !finalMsg.toLowerCase().contains(filter.toLowerCase())) {
                    return;
                }

                // Cap nhat nen cua textPane theo theme hien tai
                textPane.setBackground(AppColors.logBg());

                String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
                String line = "[" + timestamp + "] " + finalMsg + "\n";

                // Xac dinh mau bang StatusColors
                Color color = StatusColors.forLogLine(finalMsg);

                SimpleAttributeSet style = new SimpleAttributeSet();
                StyleConstants.setForeground(style, color);

                // In dam lenh va loi
                if (finalMsg.startsWith("->") || finalMsg.startsWith("Lenh:") || finalMsg.contains("COMMAND")) {
                    StyleConstants.setBold(style, true);
                } else if (finalMsg.matches(".*\\b(4[0-9]{2}|5[0-9]{2})\\b.*")
                        || finalMsg.contains("Loi") || finalMsg.contains("Error")) {
                    StyleConstants.setBold(style, true);
                }

                StyleConstants.setFontFamily(style, UITheme.MONO_FONT.getFamily());
                StyleConstants.setFontSize(style, UITheme.MONO_FONT.getSize());

                doc.insertString(doc.getLength(), line, style);
                textPane.setCaretPosition(doc.getLength());
            } catch (BadLocationException ignored) {}
        });
    }

    /** Ap dung lai mau nen khi doi theme. */
    public void applyTheme() {
        SwingUtilities.invokeLater(() -> textPane.setBackground(AppColors.logBg()));
    }

    private void saveLogToFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("ftp_protocol_log.txt"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (FileWriter writer = new FileWriter(chooser.getSelectedFile())) {
                writer.write(textPane.getText());
                JOptionPane.showMessageDialog(this, "Da luu nhat ky thanh cong!", "Thong bao", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Loi khi luu tep: " + ex.getMessage(), "Loi", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
