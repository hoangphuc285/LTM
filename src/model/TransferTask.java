package model;

import java.io.File;

public class TransferTask {
    public enum Type { UPLOAD, DOWNLOAD }

    private final String id;
    private final Type type;
    private final File localFile;
    private final String remotePath;

    private long totalBytes;
    private long transferredBytes;
    private double speedBytesPerSec;
    private TransferStatus status;
    private String errorMessage;

    // Interface lắng nghe sự thay đổi tiến độ / trạng thái
    public interface TransferListener {
        void onProgressUpdate(TransferTask task);
        void onStatusChange(TransferTask task);
    }

    private TransferListener listener;

    public TransferTask(String id, Type type, File localFile, String remotePath) {
        this.id = id;
        this.type = type;
        this.localFile = localFile;
        this.remotePath = remotePath;
        this.totalBytes = (type == Type.UPLOAD && localFile.exists()) ? localFile.length() : 0;
        this.transferredBytes = 0;
        this.speedBytesPerSec = 0;
        this.status = TransferStatus.WAITING;
    }

    // --- Getters & Setters ---
    public String getId() { return id; }
    public Type getType() { return type; }
    public File getLocalFile() { return localFile; }
    public String getRemotePath() { return remotePath; }
    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public long getTransferredBytes() { return transferredBytes; }
    public double getSpeedBytesPerSec() { return speedBytesPerSec; }
    public TransferStatus getStatus() { return status; }

    public void setStatus(TransferStatus status) {
        this.status = status;
        if (listener != null) {
            listener.onStatusChange(this);
        }
    }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setListener(TransferListener listener) { this.listener = listener; }

    // Tính phần trăm % hoàn thành
    public double getProgressPercentage() {
        if (totalBytes <= 0) return 0;
        return (double) transferredBytes / totalBytes * 100.0;
    }

    // Cập nhật số bytes đã truyền và tính tốc độ
    public void updateProgress(long transferred, double speed) {
        this.transferredBytes = transferred;
        this.speedBytesPerSec = speed;
        if (listener != null) {
            listener.onProgressUpdate(this);
        }
    }

    @Override
    public String toString() {
        return String.format("[%s] %-8s %-20s -> %-20s [%.1f%%] (%.2f KB/s) Status: %s",
                id, type, localFile.getName(), remotePath,
                getProgressPercentage(), speedBytesPerSec / 1024.0, status);
    }
}