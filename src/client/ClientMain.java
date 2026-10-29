package client;

import client.view.MainFrame;
import client.view.UITheme;

import javax.swing.*;

public class ClientMain {
    public static void main(String[] args) {
        // Thiet lap giao dien FlatLaf tu cau hinh da luu (hoac mac dinh la Light)
        client.view.ThemeManager.loadSaved();

        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }
}
