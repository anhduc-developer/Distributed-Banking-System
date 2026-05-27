package dev.distributed.bank.controller;

import dev.distributed.bank.dto.response.ApiResponse;
import dev.distributed.bank.entity.Branch;
import dev.distributed.bank.service.BranchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public ApiResponse<List<Branch>> getAllBranches() {
        return ApiResponse.ok(branchService.getAllBranches());
    }

    @GetMapping("/{branchId}")
    public ApiResponse<Branch> getBranch(@PathVariable("branchId") String branchId) {
        Branch branch = branchService.getBranchById(branchId);
        if (branch == null) {
            return ApiResponse.error("Branch " + branchId + " not found");
        }
        return ApiResponse.ok(branch);
    }
}
