package dev.distributed.bank.controller;

import dev.distributed.bank.dto.request.CreateCustomerRequest;
import dev.distributed.bank.dto.response.ApiResponse;
import dev.distributed.bank.entity.Customer;
import dev.distributed.bank.service.CustomerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ApiResponse<List<Customer>> getCustomers(
            @RequestParam(value = "branch", required = false) String branch) {
        if (branch != null && !branch.isEmpty()) {
            return ApiResponse.ok(customerService.getCustomersByBranch(branch));
        }
        return ApiResponse.ok(customerService.getAllCustomers());
    }

    @GetMapping("/{id}")
    public ApiResponse<Customer> getCustomer(
            @PathVariable("id") Long id,
            @RequestParam("branch") String branch) {
        Customer customer = customerService.getCustomerById(id, branch);
        if (customer == null) {
            return ApiResponse.error("Customer " + id + " not found at branch " + branch);
        }
        return ApiResponse.ok(customer);
    }

    @PostMapping
    public ApiResponse<Customer> createCustomer(@RequestBody CreateCustomerRequest request) {
        Customer customer = customerService.createCustomer(request);
        return ApiResponse.ok("Customer created successfully", customer);
    }

    @PutMapping("/{id}")
    public ApiResponse<Customer> updateCustomer(
            @PathVariable("id") Long id,
            @RequestBody CreateCustomerRequest request) {
        Customer customer = customerService.updateCustomer(id, request);
        return ApiResponse.ok("Customer updated successfully", customer);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCustomer(
            @PathVariable("id") Long id,
            @RequestParam("branch") String branch) {
        customerService.deleteCustomer(id, branch);
        return ApiResponse.ok("Customer deleted successfully", null);
    }
}
