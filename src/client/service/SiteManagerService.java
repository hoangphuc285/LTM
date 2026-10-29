package client.service;

import client.model.SiteProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SiteManagerService {
    private static final String CONFIG_DIR = "config";
    private static final String SITES_FILE = "config/sites.properties";

    public static List<SiteProfile> loadSites() {
        List<SiteProfile> sites = new ArrayList<>();
        File file = new File(SITES_FILE);
        if (!file.exists()) {
            // Tạo site mẫu mặc định nếu chưa có
            SiteProfile defaultSite = new SiteProfile("default", "Máy chủ cục bộ (Localhost)", "127.0.0.1", 2121, "user01", "123456", false);
            sites.add(defaultSite);
            saveSites(sites);
            return sites;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            String idsStr = props.getProperty("site.ids", "");
            if (!idsStr.trim().isEmpty()) {
                String[] ids = idsStr.split(",");
                for (String id : ids) {
                    id = id.trim();
                    if (id.isEmpty()) continue;
                    String prefix = "site." + id + ".";
                    String name = props.getProperty(prefix + "name", "Site " + id);
                    String host = props.getProperty(prefix + "host", "127.0.0.1");
                    int port = 2121;
                    try {
                        port = Integer.parseInt(props.getProperty(prefix + "port", "2121"));
                    } catch (NumberFormatException ignored) {}
                    String user = props.getProperty(prefix + "username", "");
                    String encPass = props.getProperty(prefix + "password", "");
                    String pass = decodePassword(encPass);
                    boolean anon = Boolean.parseBoolean(props.getProperty(prefix + "anonymous", "false"));

                    sites.add(new SiteProfile(id, name, host, port, user, pass, anon));
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi đọc file sites.properties: " + e.getMessage());
        }

        if (sites.isEmpty()) {
            sites.add(new SiteProfile("default", "Máy chủ cục bộ (Localhost)", "127.0.0.1", 2121, "user01", "123456", false));
        }
        return sites;
    }

    public static void saveSites(List<SiteProfile> sites) {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) dir.mkdirs();

            Properties props = new Properties();
            StringBuilder idsBuilder = new StringBuilder();

            for (SiteProfile site : sites) {
                if (idsBuilder.length() > 0) idsBuilder.append(",");
                idsBuilder.append(site.getId());

                String prefix = "site." + site.getId() + ".";
                props.setProperty(prefix + "name", site.getName() != null ? site.getName() : "");
                props.setProperty(prefix + "host", site.getHost() != null ? site.getHost() : "127.0.0.1");
                props.setProperty(prefix + "port", String.valueOf(site.getPort()));
                props.setProperty(prefix + "username", site.getUsername() != null ? site.getUsername() : "");
                props.setProperty(prefix + "password", encodePassword(site.getPassword()));
                props.setProperty(prefix + "anonymous", String.valueOf(site.isAnonymous()));
            }

            props.setProperty("site.ids", idsBuilder.toString());

            try (FileOutputStream fos = new FileOutputStream(SITES_FILE)) {
                props.store(fos, "FTP Client Site Manager Configurations");
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi lưu file sites.properties: " + e.getMessage());
        }
    }

    private static String encodePassword(String pass) {
        if (pass == null || pass.isEmpty()) return "";
        return Base64.getEncoder().encodeToString(pass.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePassword(String encPass) {
        if (encPass == null || encPass.isEmpty()) return "";
        try {
            return new String(Base64.getDecoder().decode(encPass), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encPass; // Fallback nếu chuỗi không phải base64
        }
    }
}

