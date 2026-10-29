package server;

import client.view.UITheme;

import javax.swing.*;

/**
 * Điểm khởi chạy chính của FTP Server Dashboard
 */
public class ServerMain {
    public static void main(String[] args) {
        client.view.ThemeManager.loadSaved();
        SwingUtilities.invokeLater(() -> {
            ServerGUI gui = new ServerGUI();
            gui.setVisible(true);
        });
    }
}
