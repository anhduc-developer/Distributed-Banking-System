package dev.distributed.bank.controller;

import dev.distributed.bank.dto.request.DepositRequest;
import dev.distributed.bank.dto.request.WithdrawRequest;
import dev.distributed.bank.dto.response.ApiResponse;
import dev.distributed.bank.dto.response.ConcurrentWithdrawResponse;
import dev.distributed.bank.entity.Account;
import dev.distributed.bank.service.AccountService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final AccountService accountService;

    public TransactionController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/deposit")
    public ApiResponse<Account> deposit(@RequestBody DepositRequest request) {
        Account account = accountService.deposit(request);
        return ApiResponse.ok("Deposit successful", account);
    }

    @PostMapping("/withdraw")
    public ApiResponse<Account> withdraw(@RequestBody WithdrawRequest request) {
        Account account = accountService.withdraw(request);
        return ApiResponse.ok("Withdrawal successful", account);
    }

    @PostMapping("/concurrent-withdraw")
    public ApiResponse<ConcurrentWithdrawResponse> concurrentWithdraw(@RequestBody WithdrawRequest request) {
        ConcurrentWithdrawResponse result = accountService.concurrentWithdraw(request);
        return ApiResponse.ok("Concurrent withdrawal completed", result);
    }
}
