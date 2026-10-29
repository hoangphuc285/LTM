package client.view;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class FileTableCellRenderer extends DefaultTableCellRenderer {
    private static final Icon FOLDER_ICON = UIManager.getIcon("FileView.directoryIcon");
    private static final Icon FILE_ICON   = UIManager.getIcon("FileView.fileIcon");

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        int modelRow = table.convertRowIndexToModel(row);
        Object typeVal = table.getModel().getValueAt(modelRow, 2);
        boolean isDir = "Thư mục".equals(typeVal);

        if (isDir) {
            label.setIcon(FOLDER_ICON != null ? FOLDER_ICON : UIManager.getIcon("Tree.closedIcon"));
        } else {
            label.setIcon(FILE_ICON != null ? FILE_ICON : UIManager.getIcon("Tree.leafIcon"));
        }

        label.setIconTextGap(6);
        return label;
    }
}

