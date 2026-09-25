package view;

import javax.swing.*;
import java.awt.*;

public class TransferPanel extends JPanel {
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel statusLabel = new JLabel("Sẵn sàng.");

    public TransferPanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createTitledBorder(" Trạng thái truyền file "));

        progressBar.setStringPainted(true);

        add(statusLabel, BorderLayout.NORTH);
        add(progressBar, BorderLayout.CENTER);
    }

    public void updateProgress(int percent, String statusText) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(percent);
            statusLabel.setText(statusText);
        });
    }
}