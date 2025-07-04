package com.observability.spike.order.service;

import com.observability.spike.order.model.Customer;
import com.observability.spike.order.model.Order;
import com.microsoft.applicationinsights.TelemetryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    private final RestTemplate restTemplate;
    private final TelemetryClient telemetryClient;
    private final String customerServiceBaseUrl;
    private final Map<String, Order> orderDatabase;

    public OrderService(RestTemplate restTemplate, 
                       TelemetryClient telemetryClient,
                       @Value("${customer-service.base-url:http://localhost:8081}") String customerServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.telemetryClient = telemetryClient;
        this.customerServiceBaseUrl = customerServiceBaseUrl;
        this.orderDatabase = initializeOrderData();
    }

    public Optional<Order> getOrderById(String orderId) {
        logger.info("Looking up order with ID: {}", orderId);
        
        // Track custom event
        telemetryClient.trackEvent("OrderLookup", Map.of("orderId", orderId), Map.of("orderLookupCount", 1.0));

        // Simulate order lookup
        Order order = orderDatabase.get(orderId);
        
        if (order == null) {
            logger.warn("Order not found for ID: {}", orderId);
            telemetryClient.trackEvent("OrderNotFound", Map.of("orderId", orderId), null);
            return Optional.empty();
        }

        logger.info("Order found, enriching with customer data for customer: {}", order.getCustomerId());
        
        // Enrich order with customer information
        Order enrichedOrder = enrichOrderWithCustomerData(order);
        
        logger.info("Successfully processed order: {} for customer: {}", orderId, order.getCustomerId());
        
        return Optional.of(enrichedOrder);
    }

    private Order enrichOrderWithCustomerData(Order order) {
        try {
            logger.info("Calling customer service for customer ID: {}", order.getCustomerId());
            
            // Track dependency call
            long startTime = System.currentTimeMillis();
            String url = customerServiceBaseUrl + "/api/customers/" + order.getCustomerId();
            
            Customer customer = restTemplate.getForObject(url, Customer.class);
            
            long duration = System.currentTimeMillis() - startTime;
            
            // Track dependency telemetry
            telemetryClient.trackDependency("HTTP", "customer-service", 
                new com.microsoft.applicationinsights.telemetry.Duration(duration), true);
            
            if (customer != null) {
                order.setCustomer(customer);
                telemetryClient.trackEvent("CustomerEnrichmentSuccess", 
                    Map.of("orderId", order.getOrderId(), "customerId", order.getCustomerId()), 
                    Map.of("enrichmentDuration", (double) duration));
                
                logger.info("Order enriched with customer data: {} {}", 
                           customer.getFirstName(), customer.getLastName());
            } else {
                telemetryClient.trackEvent("CustomerEnrichmentEmpty", 
                    Map.of("orderId", order.getOrderId(), "customerId", order.getCustomerId()), null);
                
                logger.warn("Customer service returned null for order {}, returning order without customer data", 
                           order.getOrderId());
            }

            return order;

        } catch (Exception e) {
            logger.error("Error enriching order with customer data: {}", order.getOrderId(), e);
            
            // Track exception
            telemetryClient.trackException(e);
            telemetryClient.trackEvent("CustomerEnrichmentFailure", 
                Map.of("orderId", order.getOrderId(), "customerId", order.getCustomerId(), "error", e.getMessage()), null);
            
            // Return order without customer data rather than failing completely
            return order;
        }
    }

    private Map<String, Order> initializeOrderData() {
        Map<String, Order> data = new HashMap<>();
        
        data.put("ORD-001", new Order("ORD-001", "123", "Laptop Computer", 1, new BigDecimal("999.99")));
        data.put("ORD-002", new Order("ORD-002", "456", "Wireless Mouse", 2, new BigDecimal("29.99")));
        data.put("ORD-003", new Order("ORD-003", "789", "USB-C Cable", 3, new BigDecimal("19.99")));
        data.put("ORD-004", new Order("ORD-004", "999", "External Monitor", 1, new BigDecimal("299.99")));
        data.put("ORD-005", new Order("ORD-005", "123", "Mechanical Keyboard", 1, new BigDecimal("149.99")));
        
        return data;
    }
}
