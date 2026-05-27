-- ================================================================
-- SITE 2: CHI NHÁNH ĐÀ NẴNG (bank_danang)
-- ================================================================
-- Phân mảnh ngang: site này CHỈ chứa dữ liệu branch_id = 'DN'
-- Schema giống hệt Hà Nội — chỉ khác dữ liệu mẫu
-- ================================================================

USE bank_danang;

-- BẢNG 1: branch
CREATE TABLE IF NOT EXISTS branch (
    branch_id   VARCHAR(10)  PRIMARY KEY,
    branch_name VARCHAR(100) NOT NULL,
    city        VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP
);

-- BẢNG 2: customer
CREATE TABLE IF NOT EXISTS customer (
    customer_id BIGINT       AUTO_INCREMENT PRIMARY KEY,
    full_name   VARCHAR(100) NOT NULL,
    phone       VARCHAR(20)  UNIQUE,
    email       VARCHAR(100),
    address     VARCHAR(200),
    branch_id   VARCHAR(10)  NOT NULL,
    created_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (branch_id) REFERENCES branch(branch_id)
);

-- BẢNG 3: account
CREATE TABLE IF NOT EXISTS account (
    account_id  BIGINT        AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT        NOT NULL,
    branch_id   VARCHAR(10)   NOT NULL,
    balance     DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customer(customer_id),
    FOREIGN KEY (branch_id)   REFERENCES branch(branch_id),
    CHECK (balance >= 0)
);

-- BẢNG 4: transaction_history
CREATE TABLE IF NOT EXISTS transaction_history (
    transaction_id     BIGINT        AUTO_INCREMENT PRIMARY KEY,
    transaction_type   VARCHAR(30)   NOT NULL,
    amount             DECIMAL(15,2) NOT NULL,
    account_id         BIGINT        NOT NULL,
    related_account_id BIGINT,
    related_branch_id  VARCHAR(10),
    balance_after      DECIMAL(15,2),
    status             VARCHAR(20)   NOT NULL DEFAULT 'SUCCESS',
    distributed_txn_id VARCHAR(50),
    description        VARCHAR(200),
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

-- BẢNG 5: distributed_transaction_log
CREATE TABLE IF NOT EXISTS distributed_transaction_log (
    txn_id             VARCHAR(50)   PRIMARY KEY,
    txn_type           VARCHAR(30)   NOT NULL,
    status             VARCHAR(20)   NOT NULL DEFAULT 'STARTED',
    source_branch      VARCHAR(10)   NOT NULL,
    dest_branch        VARCHAR(10)   NOT NULL,
    amount             DECIMAL(15,2) NOT NULL,
    source_account_id  BIGINT        NOT NULL,
    dest_account_id    BIGINT        NOT NULL,
    error_message      VARCHAR(500),
    created_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- BẢNG 6: transaction_participant
CREATE TABLE IF NOT EXISTS transaction_participant (
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY,
    txn_id     VARCHAR(50) NOT NULL,
    branch_id  VARCHAR(10) NOT NULL,
    role       VARCHAR(20) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'PREPARING',
    action     VARCHAR(30),
    created_at TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (txn_id) REFERENCES distributed_transaction_log(txn_id)
);

-- ================================================================
-- DỮ LIỆU MẪU — Chi nhánh Đà Nẵng
-- ================================================================

INSERT INTO branch (branch_id, branch_name, city) VALUES
('DN', 'Chi nhánh Đà Nẵng', 'Đà Nẵng');

-- 5 khách hàng mẫu
INSERT INTO customer (full_name, phone, email, address, branch_id) VALUES
('Võ Thanh Phong',    '0905000001', 'phong.vo@email.com',    '15 Bạch Đằng, Hải Châu, Đà Nẵng',        'DN'),
('Nguyễn Thị Quỳnh',  '0905000002', 'quynh.nguyen@email.com','28 Nguyễn Văn Linh, Thanh Khê, Đà Nẵng',  'DN'),
('Trần Đình Rin',     '0905000003', 'rin.tran@email.com',    '42 Trần Phú, Hải Châu, Đà Nẵng',          'DN'),
('Lê Thị Sương',      '0905000004', 'suong.le@email.com',    '67 Lê Duẩn, Hải Châu, Đà Nẵng',           'DN'),
('Phan Văn Tài',      '0905000005', 'tai.phan@email.com',    '89 Điện Biên Phủ, Thanh Khê, Đà Nẵng',    'DN');

-- 5 tài khoản mẫu
INSERT INTO account (customer_id, branch_id, balance, status) VALUES
(1, 'DN', 40000000.00, 'ACTIVE'),     -- Phong: 40 triệu
(2, 'DN', 60000000.00, 'ACTIVE'),     -- Quỳnh: 60 triệu
(3, 'DN', 25000000.00, 'ACTIVE'),     -- Rin: 25 triệu
(4, 'DN', 80000000.00, 'ACTIVE'),     -- Sương: 80 triệu
(5, 'DN', 35000000.00, 'ACTIVE');     -- Tài: 35 triệu

-- Giao dịch mẫu
INSERT INTO transaction_history (transaction_type, amount, account_id, balance_after, status, description) VALUES
('DEPOSIT', 40000000.00, 1, 40000000.00, 'SUCCESS', 'Nạp tiền ban đầu'),
('DEPOSIT', 60000000.00, 2, 60000000.00, 'SUCCESS', 'Nạp tiền ban đầu'),
('DEPOSIT', 25000000.00, 3, 25000000.00, 'SUCCESS', 'Nạp tiền ban đầu'),
('DEPOSIT', 80000000.00, 4, 80000000.00, 'SUCCESS', 'Nạp tiền ban đầu'),
('DEPOSIT', 35000000.00, 5, 35000000.00, 'SUCCESS', 'Nạp tiền ban đầu');
