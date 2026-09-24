package model;

public enum TransferStatus {
    WAITING,       // Đang chờ trong hàng đợi
    IN_PROGRESS,   // Đang trong quá trình truyền
    PAUSED,        // Đang tạm dừng
    COMPLETED,     // Đã hoàn thành thành công
    FAILED,        // Thất bại do lỗi
    CANCELLED      // Đã bị người dùng hủy
}