import view.MainFrame;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        // Thiết lập LookAndFeel hệ điều hành cho giao diện đẹp mắt hơn
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Chạy GUI trên Event Dispatch Thread (EDT) theo chuẩn Swing
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }
}