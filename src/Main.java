import model.TransferTask;
import service.TransferManager;

import java.io.File;
import java.io.RandomAccessFile;

public class Main {
    public static void main(String[] args) {
        String host = "127.0.0.1";
        int port = 2121;
        String username = "user01";
        String password = "123456";

        try {
            System.out.println("=== THỬ NGHIỆM GIAI ĐOẠN 3: MULTI-THREAD & TRANSFER MANAGER ===");

            // 1. Tạo 2 file mẫu khoảng 10MB để thử nghiệm truyền bất đồng bộ
            File bigFile1 = createDummyFile("big_file_1.dat", 10 * 1024 * 1024);
            File bigFile2 = createDummyFile("big_file_2.dat", 10 * 1024 * 1024);

            // 2. Khởi tạo TransferManager với tối đa 2 luồng truyền song song
            TransferManager transferManager = new TransferManager(2, host, port, username, password);

            // 3. Tạo 2 tác vụ Upload
            TransferTask task1 = new TransferTask("TASK-01", TransferTask.Type.UPLOAD, bigFile1, "remote_big_1.dat");
            TransferTask task2 = new TransferTask("TASK-02", TransferTask.Type.UPLOAD, bigFile2, "remote_big_2.dat");

            // Đăng ký Listener theo dõi tiến độ thời gian thực
            TransferTask.TransferListener listener = new TransferTask.TransferListener() {
                @Override
                public void onProgressUpdate(TransferTask task) {
                    System.out.println(task);
                }

                @Override
                public void onStatusChange(TransferTask task) {
                    System.out.println(">>> [TRẠNG THÁI THAY ĐỔI] " + task.getId() + " -> " + task.getStatus());
                }
            };

            task1.setListener(listener);
            task2.setListener(listener);

            // 4. Gửi các tác vụ vào TransferManager
            System.out.println("\n--- GỬI CÁC TÁC VỤ VÀO HÀNG ĐỢI RUNNING BACKGROUND ---");
            transferManager.submitTask(task1);
            transferManager.submitTask(task2);

            // Chờ cho các luồng truyền hoàn tất (hoặc thử nghiệm cho chạy trong vài giây)
            Thread.sleep(8000);

            transferManager.shutdown();
            System.out.println("\n=== HOÀN THÀNH KIỂM THỬ GIAI ĐOẠN 3 ===");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File createDummyFile(String fileName, long sizeInBytes) throws Exception {
        File file = new File(fileName);
        if (!file.exists() || file.length() != sizeInBytes) {
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            raf.setLength(sizeInBytes);
            raf.close();
        }
        return file;
    }
}