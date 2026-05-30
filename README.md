# Hệ thống Ngân hàng Phân tán (Distributed Banking System)

## Giới thiệu

Hệ thống Ngân hàng Phân tán là dự án mô phỏng mô hình ngân hàng đa chi nhánh dựa trên kiến trúc cơ sở dữ liệu phân tán. Dữ liệu được phân mảnh ngang (Horizontal Fragmentation) theo từng chi nhánh nhằm đảm bảo tính độc lập trong quản lý dữ liệu và hỗ trợ các giao dịch liên chi nhánh.

Hệ thống gồm 3 Site độc lập:

* Hà Nội (HN)
* Đà Nẵng (DN)
* Thành phố Hồ Chí Minh (HCM)

Các chức năng chính:

* Quản lý khách hàng
* Quản lý tài khoản
* Nạp tiền
* Rút tiền
* Chuyển tiền nội bộ chi nhánh
* Chuyển tiền liên chi nhánh bằng giao thức Two-Phase Commit (2PC)
* Truy vấn phân tán (Distributed Queries)
* Mô phỏng Deadlock và Crash Recovery

---

# 1. Cơ sở dữ liệu mẫu

Dự án đã chuẩn bị sẵn cấu trúc cơ sở dữ liệu và dữ liệu mẫu cho cả ba chi nhánh.

Các tệp khởi tạo dữ liệu:

```text
docker/init-hanoi.sql
docker/init-danang.sql
docker/init-hcm.sql
```

Các tệp này sẽ được Docker tự động thực thi trong lần khởi động đầu tiên.

Người dùng không cần chạy thủ công các tập lệnh SQL.

---

# 2. Yêu cầu hệ thống

Trước khi chạy dự án, cần cài đặt:

1. Docker Desktop
2. Java JDK 17 trở lên
3. Node.js 18 trở lên

---

# 3. Khởi động hệ thống

Hệ thống bao gồm:

* 03 máy chủ MySQL
* 01 Backend Spring Boot
* 01 Frontend ReactJS

Khuyến nghị mở 3 cửa sổ Terminal riêng biệt.

---

## 3.1 Khởi động các Site MySQL

Tại thư mục gốc của dự án:

```bash
docker-compose up -d
```

Lệnh trên sẽ khởi động:

| Site    | Container    | Port |
| ------- | ------------ | ---- |
| Hà Nội  | mysql_hanoi  | 3306 |
| Đà Nẵng | mysql_danang | 3307 |
| TP.HCM  | mysql_hcm    | 3308 |

Kiểm tra trạng thái:

```bash
docker ps
```

---

## 3.2 Khởi động Backend

Di chuyển tới thư mục backend.

### macOS / Linux

```bash
cd backend
./gradlew bootRun
```

### Windows

```cmd
cd backend
gradlew.bat bootRun
```

Sau khi khởi động thành công:

```text
http://localhost:8080
```

---

## 3.3 Khởi động Frontend

Di chuyển tới thư mục frontend.

```bash
cd frontend
npm install
npm run dev
```

Sau khi khởi động thành công:

```text
http://localhost:3000
```

Truy cập địa chỉ trên trình duyệt để sử dụng hệ thống.

---

# 4. Kiểm thử API bằng Postman

Dự án cung cấp sẵn bộ sưu tập API phục vụ kiểm thử.

Các bước thực hiện:

1. Mở Postman.
2. Chọn Import.
3. Chọn tệp:

```text
Distributed_Banking.postman_collection.json
```

4. Thực hiện các kịch bản kiểm thử có sẵn.

Các nhóm API chính:

* Customer Management
* Account Management
* Deposit
* Withdraw
* Local Transfer
* Inter-Branch Transfer (2PC)
* Distributed Queries
* Deadlock Simulation

---

# 5. Kiến trúc hệ thống

```text
                    Transaction Manager
                             |
        ------------------------------------------------
        |                      |                      |
        |                      |                      |
      HN Site               DN Site               HCM Site
      MySQL                 MySQL                 MySQL
```

Mỗi chi nhánh quản lý dữ liệu cục bộ của mình và tham gia vào các giao dịch phân tán thông qua Transaction Manager.

---

# 6. Công nghệ sử dụng

## Backend

* Java 17
* Spring Boot
* Spring Data JPA
* MySQL
* Docker
* Gradle

## Frontend

* ReactJS
* Vite
* Axios
* Ant Design

## Database

* MySQL InnoDB
* Horizontal Fragmentation
* Two-Phase Commit (2PC)

---

# 7. Thành viên thực hiện

* Mai Anh Đức
* Trần Đăng Dương
* Trịnh Anh Tú
