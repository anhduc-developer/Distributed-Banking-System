package dev.distributed.bank.controller;

import dev.distributed.bank.dto.request.CreateAccountRequest;
import dev.distributed.bank.dto.response.ApiResponse;
import dev.distributed.bank.dto.response.BalanceResponse;
import dev.distributed.bank.entity.Account;
import dev.distributed.bank.entity.TransactionHistory;
import dev.distributed.bank.service.AccountService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<List<Account>> getAccounts(@RequestParam("branch") String branch) {
        return ApiResponse.ok(accountService.getAccountsByBranch(branch));
    }

    @GetMapping("/{id}")
    public ApiResponse<Account> getAccount(
            @PathVariable("id") Long id,
            @RequestParam("branch") String branch) {
        return ApiResponse.ok(accountService.getAccountById(id, branch));
    }

    @PostMapping
    public ApiResponse<Account> createAccount(@RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(request);
        return ApiResponse.ok("Account created successfully", account);
    }

    @GetMapping("/{id}/balance")
    public ApiResponse<BalanceResponse> getBalance(
            @PathVariable("id") Long id,
            @RequestParam("branch") String branch) {
        return ApiResponse.ok(accountService.getBalance(id, branch));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<Account> updateStatus(
            @PathVariable("id") Long id,
            @RequestParam("branch") String branch,
            @RequestParam("status") String status) {
        Account account = accountService.updateAccountStatus(id, branch, status);
        return ApiResponse.ok("Account status updated", account);
    }

    @GetMapping("/{id}/transactions")
    public ApiResponse<List<TransactionHistory>> getTransactions(
            @PathVariable("id") Long id,
            @RequestParam("branch") String branch) {
        return ApiResponse.ok(accountService.getTransactionHistory(id, branch));
    }
}
