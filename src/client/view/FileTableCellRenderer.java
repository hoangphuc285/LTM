package client.view;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class FileTableCellRenderer extends DefaultTableCellRenderer {
    private static final Icon FOLDER_ICON   = UIManager.getIcon("FileView.directoryIcon");
    private static final Icon FILE_ICON     = UIManager.getIcon("FileView.fileIcon");
    private static final Icon UP_FOLDER_ICON = UIManager.getIcon("FileChooser.upFolderIcon");

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, value, isSelected, hasFocus, row, column);
        label.setIcon(null);

        if (column == 0 && value != null) {
            String fileName = value.toString();

            if ("..".equals(fileName)) {
                label.setIcon(UP_FOLDER_ICON != null ? UP_FOLDER_ICON : FOLDER_ICON);
                label.setText(".. (Thu muc cha)");
                if (!isSelected) label.setForeground(AppColors.textSecondary());
            } else {
                int modelRow = table.convertRowIndexToModel(row);
                Object sizeVal = table.getModel().getValueAt(modelRow, 1);
                boolean isDir = (sizeVal != null && "-".equals(sizeVal.toString()));

                if (isDir) {
                    label.setIcon(FOLDER_ICON != null ? FOLDER_ICON
                            : UIManager.getIcon("Tree.closedIcon"));
                    if (!isSelected) label.setForeground(AppColors.folder());
                } else {
                    label.setIcon(FILE_ICON != null ? FILE_ICON
                            : UIManager.getIcon("Tree.leafIcon"));
                    if (!isSelected) label.setForeground(AppColors.textPrimary());
                }
            }
            label.setIconTextGap(8);
        } else if (!isSelected) {
            label.setForeground(AppColors.textPrimary());
        }

        // Can le
        if (column == 1) {
            label.setHorizontalAlignment(SwingConstants.RIGHT);
        } else if (column >= 2) {
            label.setHorizontalAlignment(SwingConstants.CENTER);
        } else {
            label.setHorizontalAlignment(SwingConstants.LEFT);
        }

        return label;
    }
}
