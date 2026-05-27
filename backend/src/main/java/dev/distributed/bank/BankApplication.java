package dev.distributed.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main class — điểm khởi động ứng dụng.
 *
 * Spring Boot app này đóng vai trò COORDINATOR trong hệ thống phân tán:
 * - Kết nối đến 3 MySQL instance (3 chi nhánh)
 * - Điều phối giao dịch phân tán (2PC)
 * - Tổng hợp kết quả distributed query
 *
 * Exclude DataSourceAutoConfiguration vì chúng ta tự cấu hình 3 DataSource
 * (Spring Boot mặc định chỉ tạo 1 DataSource)
 */
@SpringBootApplication(exclude = {
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
})
public class BankApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankApplication.class, args);
    }
}
