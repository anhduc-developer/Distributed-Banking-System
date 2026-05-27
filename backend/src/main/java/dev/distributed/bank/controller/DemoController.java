package dev.distributed.bank.controller;

import dev.distributed.bank.distributed.TransactionLogger;
import dev.distributed.bank.dto.response.ApiResponse;
import dev.distributed.bank.entity.DistributedTransactionLog;
import dev.distributed.bank.service.DemoService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final DemoService demoService;
    private final TransactionLogger txnLogger;

    public DemoController(DemoService demoService, TransactionLogger txnLogger) {
        this.demoService = demoService;
        this.txnLogger = txnLogger;
    }

    @PostMapping("/simulate-site-down")
    public ApiResponse<Map<String, Object>> simulateSiteDown(
            @RequestBody Map<String, Object> request) {
        String branchId = (String) request.get("branchId");
        boolean enabled = (Boolean) request.get("enabled");
        return ApiResponse.ok(demoService.simulateSiteDown(branchId, enabled));
    }

    @PostMapping("/concurrent-withdraw")
    public ApiResponse<Map<String, Object>> concurrentWithdrawWithLock(
            @RequestBody Map<String, Object> request) {
        String branch = (String) request.get("branch");
        Long accountId = Long.valueOf(request.get("accountId").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        return ApiResponse.ok(demoService.concurrentWithdrawWithLock(branch, accountId, amount));
    }

    @PostMapping("/concurrent-withdraw-no-lock")
    public ApiResponse<Map<String, Object>> concurrentWithdrawNoLock(
            @RequestBody Map<String, Object> request) {
        String branch = (String) request.get("branch");
        Long accountId = Long.valueOf(request.get("accountId").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        return ApiResponse.ok(demoService.concurrentWithdrawWithoutLock(branch, accountId, amount));
    }

    @GetMapping("/distributed-txn-logs")
    public ApiResponse<List<DistributedTransactionLog>> getDistributedTxnLogs() {
        return ApiResponse.ok(txnLogger.getAllTransactionLogs());
    }
}
