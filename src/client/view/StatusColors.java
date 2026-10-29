package client.view;

import client.model.TransferStatus;
import java.awt.Color;

/**
 * Cung cap mau sac theo ma phan hoi FTP va trang thai tac vu truyen tai.
 * Tat ca mau duoc lay tu AppColors de dam bao nhat quan giua Light/Dark.
 */
public class StatusColors {

    /**
     * Tra ve mau tuong ung voi ma phan hoi FTP (1xx, 2xx, 3xx, 4xx, 5xx).
     */
    public static Color forFtpCode(int code) {
        if (code >= 100 && code < 200) return AppColors.info();
        if (code >= 200 && code < 300) return AppColors.success();
        if (code >= 300 && code < 400) return AppColors.warning();
        if (code >= 400 && code < 500) return AppColors.warn4xx();
        if (code >= 500)               return AppColors.error();
        return AppColors.textPrimary();
    }

    /**
     * Phan tich mot dong nhat ky va tra ve mau phu hop.
     * Thu tu phan tich: prefix lenh -> ma so FTP -> tu khoa.
     */
    public static Color forLogLine(String line) {
        if (line == null || line.isBlank()) return AppColors.textPrimary();

        // Dong lenh gui di
        String trimmed = line.trim();
        if (trimmed.startsWith("->") || trimmed.startsWith("Lenh:")
                || trimmed.startsWith("Lnh:") || trimmed.contains("COMMAND")) {
            return AppColors.accent();
        }

        // Thu phan tich ma so FTP 3 chu so
        // Dinh dang thuong gap: "[HH:mm:ss] 220 ..." hoac "220 Welcome..."
        int codeStart = -1;
        // Tim vi tri dau tien co cum 3 chu so sau khoang trang hoac [ ]
        for (int i = 0; i <= trimmed.length() - 3; i++) {
            char c0 = trimmed.charAt(i);
            if (Character.isDigit(c0)) {
                if (i == 0 || !Character.isDigit(trimmed.charAt(i - 1))) {
                    String sub = trimmed.substring(i, i + 3);
                    if (sub.matches("[1-5][0-9]{2}")) {
                        try {
                            int code = Integer.parseInt(sub);
                            // Kiem tra sau 3 chu so la khoang trang hoac cuoi chuoi
                            if (i + 3 >= trimmed.length() || !Character.isDigit(trimmed.charAt(i + 3))) {
                                return forFtpCode(code);
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }

        // Tu khoa ERROR / WARN / thanh cong / loi
        String upper = line.toUpperCase();
        if (upper.contains("ERROR") || upper.contains("LOI") || upper.contains("THAT BAI")
                || upper.contains("FAILED") || upper.contains("LI")) {
            return AppColors.error();
        }
        if (upper.contains("WARN") || upper.contains("CANH BAO")) {
            return AppColors.warning();
        }
        if (upper.contains("THANH CONG") || upper.contains("HOAN TAT") || upper.contains("SUCCESS")) {
            return AppColors.success();
        }

        return AppColors.textPrimary();
    }

    /**
     * Tra ve mau tuong ung voi trang thai tac vu truyen tai.
     */
    public static Color forTransferStatus(TransferStatus s) {
        if (s == null) return AppColors.textSecondary();
        return switch (s) {
            case WAITING    -> AppColors.textSecondary();
            case IN_PROGRESS -> AppColors.accent();
            case PAUSED     -> AppColors.warning();
            case COMPLETED  -> AppColors.success();
            case FAILED     -> AppColors.error();
            case CANCELLED  -> AppColors.warning();
        };
    }

    /**
     * Tra ve mau thanh tien trinh dua tren phan tram hoan thanh va co loi khong.
     */
    public static Color forProgressBar(int percent, boolean hasError) {
        if (hasError)      return AppColors.error();
        if (percent >= 100) return AppColors.success();
        return AppColors.accent();
    }
}

