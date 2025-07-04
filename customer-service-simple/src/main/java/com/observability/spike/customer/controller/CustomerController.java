package com.observability.spike.customer.controller;

import com.observability.spike.customer.model.Customer;
import com.microsoft.applicationinsights.TelemetryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger logger = LoggerFactory.getLogger(CustomerController.class);
    private final Map<String, Customer> customerDatabase;
    private final TelemetryClient telemetryClient;

    public CustomerController(TelemetryClient telemetryClient) {
        this.telemetryClient = telemetryClient;
        this.customerDatabase = initializeCustomerData();
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<Customer> getCustomer(@PathVariable("customerId") String customerId) {
        logger.info("Received request to get customer: {}", customerId);
        
        // Track custom event
        telemetryClient.trackEvent("CustomerRequest", Map.of("customerId", customerId), null);
        
        // Add request validation
        if (customerId == null || customerId.trim().isEmpty()) {
            logger.warn("Invalid customer ID received: {}", customerId);
            telemetryClient.trackEvent("InvalidCustomerRequest", Map.of("customerId", String.valueOf(customerId)), null);
            return ResponseEntity.badRequest().build();
        }

        // Simulate database lookup delay
        try {
            long startTime = System.currentTimeMillis();
            Thread.sleep(200);
            long duration = System.currentTimeMillis() - startTime;
            
            // Track simulated database dependency
            telemetryClient.trackDependency("Database", "customer-db", 
                new com.microsoft.applicationinsights.telemetry.Duration(duration), true);
                
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            telemetryClient.trackException(e);
        }

        Customer customer = customerDatabase.get(customerId);
        
        if (customer != null) {
            logger.info("Successfully found customer: {} {}", customer.getFirstName(), customer.getLastName());
            
            // Track successful customer lookup
            telemetryClient.trackEvent("CustomerFound", 
                Map.of("customerId", customerId, "customerStatus", customer.getStatus()), null);
                
            return ResponseEntity.ok(customer);
        } else {
            logger.warn("Customer not found: {}", customerId);
            telemetryClient.trackEvent("CustomerNotFound", Map.of("customerId", customerId), null);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        telemetryClient.trackEvent("HealthCheck", Map.of("service", "customer-service"), null);
        return ResponseEntity.ok("Customer Service is UP");
    }

    private Map<String, Customer> initializeCustomerData() {
        Map<String, Customer> data = new HashMap<>();
        
        data.put("123", new Customer("123", "John", "Doe", "john.doe@example.com", "ACTIVE"));
        data.put("456", new Customer("456", "Jane", "Smith", "jane.smith@example.com", "ACTIVE"));
        data.put("789", new Customer("789", "Bob", "Johnson", "bob.johnson@example.com", "INACTIVE"));
        data.put("999", new Customer("999", "Alice", "Williams", "alice.williams@example.com", "SUSPENDED"));
        
        return data;
    }
}
