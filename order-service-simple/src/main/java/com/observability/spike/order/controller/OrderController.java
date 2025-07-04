package com.observability.spike.order.controller;

import com.observability.spike.order.model.Order;
import com.observability.spike.order.service.OrderService;
import com.microsoft.applicationinsights.TelemetryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    
    private final OrderService orderService;
    private final TelemetryClient telemetryClient;

    public OrderController(OrderService orderService, TelemetryClient telemetryClient) {
        this.orderService = orderService;
        this.telemetryClient = telemetryClient;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable("orderId") String orderId) {
        logger.info("Received request to get order: {}", orderId);
        
        // Track request event
        telemetryClient.trackEvent("OrderRequest", Map.of("orderId", orderId), null);
        
        // Add request validation
        if (orderId == null || orderId.trim().isEmpty()) {
            logger.warn("Invalid order ID received: {}", orderId);
            telemetryClient.trackEvent("InvalidOrderRequest", Map.of("orderId", String.valueOf(orderId)), null);
            return ResponseEntity.badRequest().build();
        }

        Optional<Order> order = orderService.getOrderById(orderId);
        
        if (order.isPresent()) {
            Order foundOrder = order.get();
            
            // Track successful response
            telemetryClient.trackEvent("OrderFound", 
                Map.of("orderId", orderId, "customerId", foundOrder.getCustomerId()), 
                Map.of("totalAmount", foundOrder.getTotalAmount().doubleValue()));
                
            logger.info("Successfully returned order: {} with total: {}", 
                       orderId, foundOrder.getTotalAmount());
            return ResponseEntity.ok(foundOrder);
        } else {
            logger.warn("Order not found: {}", orderId);
            telemetryClient.trackEvent("OrderNotFound", Map.of("orderId", orderId), null);
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        telemetryClient.trackEvent("HealthCheck", Map.of("service", "order-service"), null);
        return ResponseEntity.ok("Order Service is UP");
    }
}
