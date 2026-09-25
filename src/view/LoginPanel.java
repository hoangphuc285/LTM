package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class LoginPanel extends JPanel {
    private final JTextField hostField = new JTextField("172.26.27.121", 10);
    private final JTextField portField = new JTextField("2121", 4);
    private final JTextField userField = new JTextField("user01", 8);
    private final JPasswordField passField = new JPasswordField("123456", 8);
    private final JButton btnConnect = new JButton("Kết nối");

    public LoginPanel() {
        setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));

        add(new JLabel("Host:"));
        add(hostField);
        add(new JLabel("Port:"));
        add(portField);
        add(new JLabel("User:"));
        add(userField);
        add(new JLabel("Pass:"));
        add(passField);
        add(btnConnect);
    }

    public String getHost() { return hostField.getText().trim(); }
    public int getPort() { return Integer.parseInt(portField.getText().trim()); }
    public String getUsername() { return userField.getText().trim(); }
    public String getPassword() { return new String(passField.getPassword()); }

    public void setConnectAction(ActionListener action) {
        btnConnect.addActionListener(action);
    }

    public void setConnectedState(boolean connected) {
        btnConnect.setText(connected ? "Ngắt kết nối" : "Kết nối");
        hostField.setEnabled(!connected);
        portField.setEnabled(!connected);
        userField.setEnabled(!connected);
        passField.setEnabled(!connected);
    }
}