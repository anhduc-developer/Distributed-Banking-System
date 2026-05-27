package dev.distributed.bank.exception;

/**
 * Exception khi không tìm thấy tài khoản.
 */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
