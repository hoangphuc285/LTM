package client.view;

import java.awt.Font;

/**
 * Lop uy quyen (delegate) giu nguyen ten lop cu de tuong thich voi code hien co.
 * Toan bo logic that su da duoc chuyen sang ThemeManager.
 *
 * Cac lop dang import UITheme (ClientMain, ServerMain, ServerGUI) khong can sua.
 */
public class UITheme {

    // Cac hang so Font duoc tai su dung truc tiep tu ThemeManager
    public static final Font APP_FONT      = ThemeManager.APP_FONT;
    public static final Font APP_FONT_BOLD = ThemeManager.APP_FONT_BOLD;
    public static final Font MONO_FONT     = ThemeManager.MONO_FONT;

    /**
     * Thiet lap giao dien FlatLaf.
     * Giu nguyen chu ky phuong thuc cong khai de tuong thich nguoc.
     *
     * @param isDark true = giao dien toi, false = giao dien sang
     */
    public static void setupTheme(boolean isDark) {
        ThemeManager.toggle(isDark);
    }
}
