package client.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class BookmarkService {
    private static final String BOOKMARK_FILE = "config/bookmarks.properties";

    public static List<String> loadBookmarks() {
        List<String> list = new ArrayList<>();
        File file = new File(BOOKMARK_FILE);
        if (!file.exists()) {
            list.add("/");
            saveBookmarks(list);
            return list;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            Properties props = new Properties();
            props.load(fis);
            String paths = props.getProperty("bookmarks", "/");
            String[] arr = paths.split(";");
            for (String p : arr) {
                p = p.trim();
                if (!p.isEmpty() && !list.contains(p)) {
                    list.add(p);
                }
            }
        } catch (Exception ignored) {}

        if (list.isEmpty()) {
            list.add("/");
        }
        return list;
    }

    public static void saveBookmarks(List<String> bookmarks) {
        try {
            File dir = new File("config");
            if (!dir.exists()) dir.mkdirs();

            Properties props = new Properties();
            StringBuilder sb = new StringBuilder();
            for (String b : bookmarks) {
                if (sb.length() > 0) sb.append(";");
                sb.append(b);
            }
            props.setProperty("bookmarks", sb.toString());
            try (FileOutputStream fos = new FileOutputStream(BOOKMARK_FILE)) {
                props.store(fos, "FTP Remote Bookmarks");
            }
        } catch (Exception ignored) {}
    }
}

