package client.model;

import java.text.SimpleDateFormat;
import java.util.Date;

public class FTPFile {
    private final String name;
    private final long size;
    private final boolean isDirectory;
    private final long lastModified; // epoch milliseconds

    public FTPFile(String name, long size, boolean isDirectory) {
        this(name, size, isDirectory, 0L);
    }

    public FTPFile(String name, long size, boolean isDirectory, long lastModified) {
        this.name = name;
        this.size = size;
        this.isDirectory = isDirectory;
        this.lastModified = lastModified;
    }

    public String getName()        { return name; }
    public long getSize()          { return size; }
    public boolean isDirectory()   { return isDirectory; }
    public long getLastModified()  { return lastModified; }

    /**
     * Format kích thước file theo đơn vị dễ đọc (B / KB / MB / GB).
     */
    public static String formatSize(long bytes) {
        if (bytes < 0)            return "-";
        if (bytes < 1_024)        return bytes + " B";
        if (bytes < 1_048_576)    return String.format("%.1f KB", bytes / 1_024.0);
        if (bytes < 1_073_741_824) return String.format("%.1f MB", bytes / 1_048_576.0);
        return String.format("%.2f GB", bytes / 1_073_741_824.0);
    }

    /**
     * Format tốc độ truyền (B/s / KB/s / MB/s).
     */
    public static String formatSpeed(double bytesPerSec) {
        if (bytesPerSec <= 0)         return "-";
        if (bytesPerSec < 1_024)      return String.format("%.0f B/s", bytesPerSec);
        if (bytesPerSec < 1_048_576)  return String.format("%.1f KB/s", bytesPerSec / 1_024.0);
        return String.format("%.1f MB/s", bytesPerSec / 1_048_576.0);
    }

    /**
     * Format ngày sửa đổi (epoch ms → "dd/MM/yyyy HH:mm").
     */
    public static String formatDate(long epochMillis) {
        if (epochMillis <= 0) return "-";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(epochMillis));
    }

    /**
     * Wrapper cho Kích thước file để TableRowSorter sắp xếp đúng thứ tự số (bytes)
     */
    public static class FileSizeItem implements Comparable<FileSizeItem> {
        private final long bytes;
        private final boolean isDirectory;

        public FileSizeItem(long bytes, boolean isDirectory) {
            this.bytes = bytes;
            this.isDirectory = isDirectory;
        }

        public long getBytes() { return bytes; }
        public boolean isDirectory() { return isDirectory; }

        @Override
        public int compareTo(FileSizeItem other) {
            if (this.isDirectory && !other.isDirectory) return -1;
            if (!this.isDirectory && other.isDirectory) return 1;
            return Long.compare(this.bytes, other.bytes);
        }

        @Override
        public String toString() {
            return isDirectory ? "-" : formatSize(bytes);
        }
    }

    /**
     * Wrapper cho Ngày sửa đổi để TableRowSorter sắp xếp đúng theo mốc thời gian.
     */
    public static class FileDateItem implements Comparable<FileDateItem> {
        private final long millis;

        public FileDateItem(long millis) {
            this.millis = millis;
        }

        public long getMillis() { return millis; }

        @Override
        public int compareTo(FileDateItem other) {
            return Long.compare(this.millis, other.millis);
        }

        @Override
        public String toString() {
            return formatDate(millis);
        }
    }

    @Override
    public String toString() {
        return (isDirectory ? "[DIR]  " : "[FILE] ") + name + " (" + formatSize(size) + ")";
    }
}

