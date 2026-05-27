-- ================================================================
-- SITE 1: CHI NHÁNH HÀ NỘI (bank_hanoi)
-- ================================================================
-- File này tự động chạy khi MySQL container khởi động lần đầu.
-- Tạo schema (6 bảng) + dữ liệu mẫu cho chi nhánh Hà Nội.
--
-- Phân mảnh ngang: site này CHỈ chứa dữ liệu branch_id = 'HN'
-- ================================================================

USE bank_hanoi;

-- ================================================================
-- BẢNG 1: branch — Thông tin chi nhánh
-- Mỗi site chỉ có 1 record (chi nhánh mình)
-- ================================================================
CREATE TABLE IF NOT EXISTS branch (
    branch_id   VARCHAR(10)  PRIMARY KEY COMMENT 'Mã chi nhánh: HN, DN, HCM',
    branch_name VARCHAR(100) NOT NULL    COMMENT 'Tên đầy đủ chi nhánh',
    city        VARCHAR(50)  NOT NULL    COMMENT 'Thành phố',
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================
-- BẢNG 2: customer — Khách hàng
-- Horizontal fragment: chỉ chứa KH có branch_id = 'HN'
-- ================================================================
CREATE TABLE IF NOT EXISTS customer (
    customer_id BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT 'ID tự tăng',
    full_name   VARCHAR(100) NOT NULL    COMMENT 'Họ và tên',
    phone       VARCHAR(20)  UNIQUE      COMMENT 'SĐT - unique toàn site',
    email       VARCHAR(100)             COMMENT 'Email (tuỳ chọn)',
    address     VARCHAR(200)             COMMENT 'Địa chỉ',
    branch_id   VARCHAR(10)  NOT NULL    COMMENT 'FK → branch',
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id)
);

-- ================================================================
-- BẢNG 3: account — Tài khoản ngân hàng
-- Chứa balance - trường quan trọng nhất cho giao dịch
-- ================================================================
CREATE TABLE IF NOT EXISTS account (
    account_id  BIGINT        AUTO_INCREMENT PRIMARY KEY COMMENT 'ID tài khoản',
    customer_id BIGINT        NOT NULL    COMMENT 'FK → customer (chủ TK)',
    branch_id   VARCHAR(10)   NOT NULL    COMMENT 'FK → branch',
    balance     DECIMAL(15,2) NOT NULL DEFAULT 0.00 COMMENT 'Số dư (VND)',
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/INACTIVE/FROZEN',
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (branch_id)   REFERENCES branch(branch_id),
    CHECK (balance >= 0)
);

-- ================================================================
-- BẢNG 4: transaction_history — Lịch sử giao dịch
-- Ghi lại mọi thao tác: gửi, rút, chuyển tiền
-- ================================================================
CREATE TABLE IF NOT EXISTS transaction_history (
    transaction_id     BIGINT        AUTO_INCREMENT PRIMARY KEY,
    transaction_type   VARCHAR(30)   NOT NULL COMMENT 'DEPOSIT/WITHDRAW/TRANSFER_IN/TRANSFER_OUT/INTER_BRANCH_IN/INTER_BRANCH_OUT',
    amount             DECIMAL(15,2) NOT NULL COMMENT 'Số tiền giao dịch',
    account_id         BIGINT        NOT NULL COMMENT 'Tài khoản thực hiện',
    related_account_id BIGINT                 COMMENT 'TK đối ứng (nếu chuyển tiền)',
    related_branch_id  VARCHAR(10)            COMMENT 'Chi nhánh đối ứng (nếu liên CN)',
    balance_after      DECIMAL(15,2)          COMMENT 'Số dư sau giao dịch',
    status             VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED/ROLLED_BACK',
    distributed_txn_id VARCHAR(50)            COMMENT 'Link đến dist_txn_log',
    description        VARCHAR(200)           COMMENT 'Ghi chú',
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- ================================================================
-- BẢNG 5: distributed_transaction_log — Log giao dịch phân tán (2PC)
-- Coordinator dùng bảng này để theo dõi trạng thái 2PC
-- ================================================================
CREATE TABLE IF NOT EXISTS distributed_transaction_log (
    txn_id             VARCHAR(50)   PRIMARY KEY COMMENT 'UUID giao dịch phân tán',
    txn_type           VARCHAR(30)   NOT NULL COMMENT 'INTER_BRANCH_TRANSFER',
    status             VARCHAR(20)   NOT NULL DEFAULT 'STARTED' COMMENT 'STARTED/PREPARING/PREPARED/COMMITTING/COMMITTED/ABORTING/ABORTED',
    source_branch      VARCHAR(10)   NOT NULL COMMENT 'Chi nhánh nguồn',
    dest_branch        VARCHAR(10)   NOT NULL COMMENT 'Chi nhánh đích',
    amount             DECIMAL(15,2) NOT NULL COMMENT 'Số tiền',
    source_account_id  BIGINT        NOT NULL COMMENT 'TK nguồn',
    dest_account_id    BIGINT        NOT NULL COMMENT 'TK đích',
    error_message      VARCHAR(500)           COMMENT 'Lý do lỗi (nếu có)',
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ================================================================
-- BẢNG 6: transaction_participant — Participant trong 2PC
-- Mỗi giao dịch phân tán có 2 participants (source + destination)
-- ================================================================
CREATE TABLE IF NOT EXISTS transaction_participant (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    txn_id     VARCHAR(50) NOT NULL COMMENT 'FK → distributed_transaction_log',
    branch_id  VARCHAR(10) NOT NULL COMMENT 'Site tham gia',
    role       VARCHAR(20) NOT NULL COMMENT 'SOURCE/DESTINATION',
    status     VARCHAR(20) NOT NULL DEFAULT 'PREPARING' COMMENT 'PREPARING/PREPARED/COMMITTED/ABORTED/FAILED',
    action     VARCHAR(30)          COMMENT 'DEBIT/CREDIT',
    created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (txn_id) REFERENCES distributed_transaction_log(txn_id)
);

-- ================================================================
-- DỮ LIỆU MẪU — Chi nhánh Hà Nội
-- ================================================================

-- Branch: chỉ 1 record cho chi nhánh này
INSERT INTO branch (branch_id, branch_name, city) VALUES
('HN', 'Chi nhánh Hà Nội', 'Hà Nội');

-- 5 khách hàng mẫu
INSERT INTO customer (full_name, phone, email, address, branch_id) VALUES
('Nguyễn Văn An',    '0901000001', 'an.nguyen@email.com',   '12 Phố Huế, Hai Bà Trưng, Hà Nội',       'HN'),
('Trần Thị Bình',    '0901000002', 'binh.tran@email.com',   '45 Láng Hạ, Đống Đa, Hà Nội',             'HN'),
('Lê Hoàng Cường',   '0901000003', 'cuong.le@email.com',    '78 Nguyễn Trãi, Thanh Xuân, Hà Nội',      'HN'),
('Phạm Minh Dũng',   '0901000004', 'dung.pham@email.com',   '23 Kim Mã, Ba Đình, Hà Nội',              'HN'),
('Hoàng Thị Em',     '0901000005', 'em.hoang@email.com',    '56 Trần Duy Hưng, Cầu Giấy, Hà Nội',     'HN');

-- 6 tài khoản mẫu (1 KH có thể có nhiều TK)
INSERT INTO account (customer_id, branch_id, balance, status) VALUES
(1, 'HN', 50000000.00, 'ACTIVE'),     -- An: 50 triệu
(1, 'HN', 20000000.00, 'ACTIVE'),     -- An: TK thứ 2 - 20 triệu
(2, 'HN', 75000000.00, 'ACTIVE'),     -- Bình: 75 triệu
(3, 'HN', 30000000.00, 'ACTIVE'),     -- Cường: 30 triệu
(4, 'HN', 100000000.00, 'ACTIVE'),    -- Dũng: 100 triệu
(5, 'HN', 15000000.00, 'ACTIVE');     -- Em: 15 triệu

-- Vài giao dịch mẫu
INSERT INTO transaction_history (transaction_type, amount, account_id, balance_after, status, description) VALUES
('DEPOSIT',  50000000.00, 1, 50000000.00, 'SUCCESS', 'Nạp tiền ban đầu'),
('DEPOSIT',  20000000.00, 2, 20000000.00, 'SUCCESS', 'Nạp tiền ban đầu'),
('DEPOSIT',  75000000.00, 3, 75000000.00, 'SUCCESS', 'Nạp tiền ban đầu'),
('DEPOSIT',  30000000.00, 4, 30000000.00, 'SUCCESS', 'Nạp tiền ban đầu'),
('DEPOSIT', 100000000.00, 5, 100000000.00, 'SUCCESS', 'Nạp tiền ban đầu'),
('DEPOSIT',  15000000.00, 6, 15000000.00, 'SUCCESS', 'Nạp tiền ban đầu');
