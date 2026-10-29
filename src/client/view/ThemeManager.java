package client.view;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.io.*;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Quan ly chu de giao dien (Theme) cho toan bo ung dung FTP Client va Server.
 *
 * Chuc nang:
 * - applyLight()  : Ap dung giao dien sang (FlatLightLaf)
 * - applyDark()   : Ap dung giao dien toi (FlatDarkLaf)
 * - toggle(dark)  : Chuyen doi giao dien va luu vao file config
 * - loadSaved()   : Doc file config va ap dung giao dien da luu
 *
 * Ghi chu: UITheme van ton tai nhu mot lop uy quyen (delegate) de tuong thich
 *          voi code cu. Tat ca logic that su nam trong ThemeManager.
 */
public class ThemeManager {

    // Font chung su dung xuyen suot ung dung
    public static final Font APP_FONT      = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font APP_FONT_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font MONO_FONT     = new Font("Consolas", Font.PLAIN, 12);

    private static final String CONFIG_FILE = "config/app.properties";
    private static final String PROP_THEME  = "theme";

    // --------------------------------------------------------
    // API cong khai
    // --------------------------------------------------------

    /** Ap dung giao dien sang va cap nhat tat ca cua so dang mo. */
    public static void applyLight() {
        try {
            FlatLightLaf.setup();
            AppColors.setDark(false);
            applyFlatLafKeys(false);
            applyFonts();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Ap dung giao dien toi va cap nhat tat ca cua so dang mo. */
    public static void applyDark() {
        try {
            FlatDarkLaf.setup();
            AppColors.setDark(true);
            applyFlatLafKeys(true);
            applyFonts();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Chuyen doi giao dien, cap nhat toan bo UI va luu lua chon vao file config.
     * @param dark true = giao dien toi, false = giao dien sang
     */
    public static void toggle(boolean dark) {
        if (dark) {
            applyDark();
        } else {
            applyLight();
        }
        updateAllWindows();
        save(dark);
    }

    /**
     * Doc file config va ap dung giao dien da luu.
     * Neu chua co config, mac dinh la giao dien sang.
     */
    public static void loadSaved() {
        boolean dark = false;
        File f = new File(CONFIG_FILE);
        if (f.exists()) {
            try (InputStream is = new FileInputStream(f)) {
                Properties props = new Properties();
                props.load(is);
                dark = "dark".equalsIgnoreCase(props.getProperty(PROP_THEME, "light"));
            } catch (IOException ignored) {}
        }
        if (dark) {
            applyDark();
        } else {
            applyLight();
        }
    }

    /** Giu nguyen chuc nang nhu UITheme.setupTheme(boolean) de tuong thich nguoc. */
    public static void setupTheme(boolean dark) {
        toggle(dark);
    }

    // --------------------------------------------------------
    // Noi bo
    // --------------------------------------------------------

    /**
     * Thiet lap tat ca cac khoa UIManager.put() theo cac mau trong AppColors.
     * Duoc goi sau khi FlatLaf.setup() de ghi de cau hinh mac dinh.
     */
    private static void applyFlatLafKeys(boolean dark) {
        Color accent      = AppColors.accent();
        Color selBg       = AppColors.selectionBg();
        Color selFg       = AppColors.selectionText();
        Color altRow      = AppColors.altRow();
        Color headerBg    = AppColors.headerBg();
        Color headerFg    = AppColors.headerText();
        Color bgPanel     = AppColors.bgPanel();
        Color bgWindow    = AppColors.bgWindow();
        Color textPrimary = AppColors.textPrimary();
        Color progressFg  = AppColors.accent();
        Color border      = AppColors.border();

        // --- Bo goc ---
        UIManager.put("Component.arc",       8);
        UIManager.put("Button.arc",          8);
        UIManager.put("TextComponent.arc",   8);
        UIManager.put("CheckBox.arc",        4);
        UIManager.put("ScrollBar.thumbArc",  999);

        // --- Mau nhon chinh ---
        UIManager.put("Component.focusColor",  accent);
        UIManager.put("Component.accentColor", accent);

        // --- Bang (Table) ---
        UIManager.put("Table.selectionBackground",  selBg);
        UIManager.put("Table.selectionForeground",  selFg);
        UIManager.put("Table.alternateRowColor",     altRow);
        UIManager.put("Table.intercellSpacing",      new Dimension(4, 4));
        UIManager.put("Table.rowHeight",             26);
        UIManager.put("Table.focusCellHighlightBorder", BorderFactory.createLineBorder(accent, 1));

        // --- Tieu de bang ---
        UIManager.put("TableHeader.background",  headerBg);
        UIManager.put("TableHeader.foreground",  headerFg);
        UIManager.put("TableHeader.separatorColor", border);

        // --- Tab ---
        UIManager.put("TabbedPane.underlineColor",        accent);
        UIManager.put("TabbedPane.selectedBackground",    bgPanel);
        UIManager.put("TabbedPane.focusColor",            selBg);
        UIManager.put("TabbedPane.tabHeight",             30);

        // --- Thanh tien trinh ---
        UIManager.put("ProgressBar.foreground",       progressFg);
        UIManager.put("ProgressBar.selectionForeground", Color.WHITE);

        // --- Nut ---
        UIManager.put("Button.foreground",    textPrimary);
        UIManager.put("Button.hoverBorderColor", accent);

        // --- Panel, Label ---
        UIManager.put("Panel.background",  bgWindow);
        UIManager.put("Label.foreground",  textPrimary);

        // --- Thanh cuon ---
        UIManager.put("ScrollBar.showButtons", true);

        // --- Splitter ---
        UIManager.put("SplitPane.dividerSize", 5);
    }

    /** Ap dung font chung cho tat ca thanh phan Swing. */
    private static void applyFonts() {
        FontUIResource f = new FontUIResource(APP_FONT);
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof FontUIResource) {
                UIManager.put(key, f);
            }
        }
    }

    /** Cap nhat cay UI cho moi cua so dang mo. */
    public static void updateAllWindows() {
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
    }

    /** Luu lua chon giao dien vao file config/app.properties. */
    private static void save(boolean dark) {
        try {
            File dir = new File("config");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(CONFIG_FILE);
            Properties props = new Properties();
            if (f.exists()) {
                try (InputStream is = new FileInputStream(f)) {
                    props.load(is);
                }
            }
            props.setProperty(PROP_THEME, dark ? "dark" : "light");
            try (OutputStream os = new FileOutputStream(f)) {
                props.store(os, "FTP Client/Server Application Config");
            }
        } catch (IOException ignored) {}
    }
}

