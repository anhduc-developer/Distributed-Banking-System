package dev.distributed.bank.controller;

import dev.distributed.bank.dto.response.ApiResponse;
import dev.distributed.bank.dto.response.TopCustomerResponse;
import dev.distributed.bank.dto.response.TopTransactionCustomerResponse;
import dev.distributed.bank.dto.response.TotalBalanceResponse;
import dev.distributed.bank.dto.response.TransactionStatsResponse;
import dev.distributed.bank.entity.TransactionHistory;
import dev.distributed.bank.service.DistributedQueryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class QueryController {

    private final DistributedQueryService queryService;

    public QueryController(DistributedQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/total-balance")
    public ApiResponse<TotalBalanceResponse> getTotalBalance() {
        return ApiResponse.ok(queryService.getTotalBalance());
    }

    @GetMapping("/top-customers")
    public ApiResponse<List<TopCustomerResponse>> getTopCustomers(
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ApiResponse.ok(queryService.getTopCustomers(limit));
    }

    @GetMapping("/top-depositing-customers")
    public ApiResponse<List<TopTransactionCustomerResponse>> getTopDepositingCustomers(
            @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return ApiResponse.ok(queryService.getTopDepositingCustomers(limit));
    }

    @GetMapping("/inter-branch-transactions")
    public ApiResponse<List<TransactionHistory>> getInterBranchTransactions() {
        return ApiResponse.ok(queryService.getInterBranchTransactions());
    }

    @GetMapping("/history/deposit")
    public ApiResponse<List<TransactionHistory>> getDepositHistory(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ApiResponse.ok(queryService.getDepositHistory(limit));
    }

    @GetMapping("/history/withdraw")
    public ApiResponse<List<TransactionHistory>> getWithdrawHistory(
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return ApiResponse.ok(queryService.getWithdrawHistory(limit));
    }

    @GetMapping("/multi-branch-customers")
    public ApiResponse<List<Map<String, Object>>> getMultiBranchCustomers() {
        return ApiResponse.ok(queryService.getMultiBranchCustomers());
    }

    @GetMapping("/transaction-summary")
    public ApiResponse<List<TransactionStatsResponse>> getTransactionStats() {
        return ApiResponse.ok(queryService.getTransactionStats());
    }
}
