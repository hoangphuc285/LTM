package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * P10: LoginPanel với tính năng lưu và nạp lịch sử kết nối gần nhất (tương tự Quickconnect của FileZilla)
 */
public class LoginPanel extends JPanel {
    private final JTextField hostField = new JTextField("127.0.0.1", 10);
    private final JTextField portField = new JTextField("2121", 4);
    private final JTextField userField = new JTextField("user01", 8);
    private final JPasswordField passField = new JPasswordField("123456", 8);
    private final JButton btnConnect = new JButton("Kết nối nhanh");

    private static final String CONFIG_DIR = "config";
    private static final String RECENT_FILE = "config/recent_connection.properties";

    public LoginPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 8));
        setBorder(BorderFactory.createTitledBorder(" Kết nối nhanh (Quickconnect) "));

        add(new JLabel("Máy chủ:"));
        add(hostField);
        add(new JLabel("Cổng:"));
        add(portField);
        add(new JLabel("Tài khoản:"));
        add(userField);
        add(new JLabel("Mật khẩu:"));
        add(passField);
        add(btnConnect);

        // Nhấn Enter ở bất kỳ ô nào cũng kích hoạt nút Kết nối
        ActionListener submitAction = e -> btnConnect.doClick();
        hostField.addActionListener(submitAction);
        portField.addActionListener(submitAction);
        userField.addActionListener(submitAction);
        passField.addActionListener(submitAction);

        // P10: Nạp cấu hình kết nối gần đây
        loadRecentConnection();
    }

    public String getHost() { return hostField.getText().trim(); }
    public int getPort() {
        try {
            return Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            return 2121;
        }
    }
    public String getUsername() { return userField.getText().trim(); }
    public String getPassword() { return new String(passField.getPassword()); }

    public void setConnectAction(ActionListener action) {
        btnConnect.addActionListener(action);
    }

    public void setConnectedState(boolean connected) {
        btnConnect.setText(connected ? "Ngắt kết nối" : "Kết nối nhanh");
        btnConnect.setEnabled(true);
        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        userField.setEnabled(!connected);
        passField.setEnabled(!connected);

        if (connected) {
            saveRecentConnection();
        }
    }

    public void setConnectingState(boolean connecting) {
        btnConnect.setEnabled(!connecting);
        btnConnect.setText(connecting ? "Đang kết nối..." : "Kết nối nhanh");
        hostField.setEnabled(!connecting);
        portField.setEnabled(!connecting);
        userField.setEnabled(!connecting);
        passField.setEnabled(!connecting);
    }

    /**
     * P10: Lưu thông tin máy chủ và tài khoản vừa kết nối thành công
     */
    public void saveRecentConnection() {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) dir.mkdirs();

            Properties props = new Properties();
            props.setProperty("host", getHost());
            props.setProperty("port", String.valueOf(getPort()));
            props.setProperty("username", getUsername());

            try (FileOutputStream fos = new FileOutputStream(RECENT_FILE)) {
                props.store(fos, "Recent FTP Connection Settings");
            }
        } catch (Exception ignored) {}
    }

    /**
     * P10: Nạp lại thông tin kết nối gần nhất
     */
    private void loadRecentConnection() {
        File file = new File(RECENT_FILE);
        if (!file.exists()) return;

        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);

            String h = props.getProperty("host");
            String p = props.getProperty("port");
            String u = props.getProperty("username");

            if (h != null && !h.trim().isEmpty()) hostField.setText(h.trim());
            if (p != null && !p.trim().isEmpty()) portField.setText(p.trim());
            if (u != null && !u.trim().isEmpty()) userField.setText(u.trim());
        } catch (Exception ignored) {}
    }
}
