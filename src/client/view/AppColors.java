package client.view;

import java.awt.Color;

/**
 * He thong mau thong nhat cho toan bo ung dung FTP Client va Server.
 * Ho tro hai che do: Sang (Light) va Toi (Dark).
 * Khong viet ma mau cung (new Color(...)) rai rac trong cac lop giao dien.
 */
public class AppColors {

    private static boolean dark = false;

    public static void setDark(boolean d) { dark = d; }
    public static boolean isDark() { return dark; }

    // =========================================================
    // Che do Sang (Light Mode)
    // =========================================================
    public static final Color L_BG_WINDOW      = new Color(0xF5F7FA);
    public static final Color L_BG_PANEL       = new Color(0xFFFFFF);
    public static final Color L_BORDER         = new Color(0xD9DEE5);
    public static final Color L_TEXT_PRIMARY   = new Color(0x1F2937);
    public static final Color L_TEXT_SECONDARY = new Color(0x6B7280);
    public static final Color L_ACCENT         = new Color(0x2563EB);
    public static final Color L_ACCENT_HOVER   = new Color(0x1D4ED8);
    public static final Color L_ACCENT_PRESSED = new Color(0x1E40AF);
    public static final Color L_SELECTION_BG   = new Color(0xDBEAFE);
    public static final Color L_SELECTION_TEXT = new Color(0x1F2937);
    public static final Color L_ALT_ROW        = new Color(0xF9FAFB);
    public static final Color L_HEADER_BG      = new Color(0xEEF2F7);
    public static final Color L_HEADER_TEXT    = new Color(0x374151);
    public static final Color L_SUCCESS        = new Color(0x16A34A);
    public static final Color L_WARNING        = new Color(0xD97706);
    public static final Color L_ERROR          = new Color(0xDC2626);
    public static final Color L_INFO           = new Color(0x0891B2);
    public static final Color L_WARN_4XX       = new Color(0xEA580C);
    public static final Color L_LOG_BG         = new Color(0xF8F9FB);
    public static final Color L_FOLDER         = new Color(0xD97706);

    // =========================================================
    // Che do Toi (Dark Mode)
    // =========================================================
    public static final Color D_BG_WINDOW      = new Color(0x1E1F22);
    public static final Color D_BG_PANEL       = new Color(0x2B2D30);
    public static final Color D_BORDER         = new Color(0x3C3F41);
    public static final Color D_TEXT_PRIMARY   = new Color(0xE5E7EB);
    public static final Color D_TEXT_SECONDARY = new Color(0x9CA3AF);
    public static final Color D_ACCENT         = new Color(0x3B82F6);
    public static final Color D_ACCENT_HOVER   = new Color(0x60A5FA);
    public static final Color D_ACCENT_PRESSED = new Color(0x2563EB);
    public static final Color D_SELECTION_BG   = new Color(0x264F78);
    public static final Color D_SELECTION_TEXT = new Color(0xFFFFFF);
    public static final Color D_ALT_ROW        = new Color(0x2F3134);
    public static final Color D_HEADER_BG      = new Color(0x35383B);
    public static final Color D_HEADER_TEXT    = new Color(0xD1D5DB);
    public static final Color D_SUCCESS        = new Color(0x22C55E);
    public static final Color D_WARNING        = new Color(0xF59E0B);
    public static final Color D_ERROR          = new Color(0xEF4444);
    public static final Color D_INFO           = new Color(0x22D3EE);
    public static final Color D_WARN_4XX       = new Color(0xFB923C);
    public static final Color D_LOG_BG         = new Color(0x25262A);
    public static final Color D_FOLDER         = new Color(0xF59E0B);

    // =========================================================
    // Cac ham tro cap mau theo che do hien tai
    // =========================================================
    public static Color bgWindow()      { return dark ? D_BG_WINDOW      : L_BG_WINDOW; }
    public static Color bgPanel()       { return dark ? D_BG_PANEL       : L_BG_PANEL; }
    public static Color border()        { return dark ? D_BORDER         : L_BORDER; }
    public static Color textPrimary()   { return dark ? D_TEXT_PRIMARY   : L_TEXT_PRIMARY; }
    public static Color textSecondary() { return dark ? D_TEXT_SECONDARY : L_TEXT_SECONDARY; }
    public static Color accent()        { return dark ? D_ACCENT         : L_ACCENT; }
    public static Color accentHover()   { return dark ? D_ACCENT_HOVER   : L_ACCENT_HOVER; }
    public static Color accentPressed() { return dark ? D_ACCENT_PRESSED : L_ACCENT_PRESSED; }
    public static Color selectionBg()   { return dark ? D_SELECTION_BG   : L_SELECTION_BG; }
    public static Color selectionText() { return dark ? D_SELECTION_TEXT : L_SELECTION_TEXT; }
    public static Color altRow()        { return dark ? D_ALT_ROW        : L_ALT_ROW; }
    public static Color headerBg()      { return dark ? D_HEADER_BG      : L_HEADER_BG; }
    public static Color headerText()    { return dark ? D_HEADER_TEXT    : L_HEADER_TEXT; }
    public static Color success()       { return dark ? D_SUCCESS        : L_SUCCESS; }
    public static Color warning()       { return dark ? D_WARNING        : L_WARNING; }
    public static Color error()         { return dark ? D_ERROR          : L_ERROR; }
    public static Color info()          { return dark ? D_INFO           : L_INFO; }
    public static Color warn4xx()       { return dark ? D_WARN_4XX       : L_WARN_4XX; }
    public static Color logBg()         { return dark ? D_LOG_BG         : L_LOG_BG; }
    public static Color folder()        { return dark ? D_FOLDER         : L_FOLDER; }
}

