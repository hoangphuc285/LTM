package client;

import client.view.MainFrame;

import javax.swing.*;

/**
 * Điểm khởi chạy chính của FTP Client
 */
public class ClientMain {
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }
}

