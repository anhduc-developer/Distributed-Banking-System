package dev.distributed.bank.exception;

/**
 * Exception khi tài khoản không đủ số dư để thực hiện giao dịch.
 */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
