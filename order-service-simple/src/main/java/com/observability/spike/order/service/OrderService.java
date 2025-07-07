package com.observability.spike.order.service;

import com.observability.spike.order.model.Customer;
import com.observability.spike.order.model.Order;
import com.microsoft.applicationinsights.TelemetryClient;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
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
    private final MetricsService metricsService;
    private final OpenTelemetryService openTelemetryService;
    private final String customerServiceBaseUrl;
    private final Map<String, Order> orderDatabase;

    public OrderService(RestTemplate restTemplate, 
                       TelemetryClient telemetryClient,
                       MetricsService metricsService,
                       OpenTelemetryService openTelemetryService,
                       @Value("${customer-service.base-url:http://localhost:8081}") String customerServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.telemetryClient = telemetryClient;
        this.metricsService = metricsService;
        this.openTelemetryService = openTelemetryService;
        this.customerServiceBaseUrl = customerServiceBaseUrl;
        this.orderDatabase = initializeOrderData();
    }

    public Optional<Order> getOrderById(String orderId) {
        logger.info("Looking up order with ID: {}", orderId);
        
        // Start OpenTelemetry span for order processing
        Span orderProcessingSpan = openTelemetryService.startOrderProcessingSpan(orderId);
        
        try (Scope scope = orderProcessingSpan.makeCurrent()) {
            // Track custom event (Application Insights)
            telemetryClient.trackEvent("OrderLookup", Map.of("orderId", orderId), Map.of("orderLookupCount", 1.0));

            // Simulate order lookup
            Order order = orderDatabase.get(orderId);
            
            if (order == null) {
                logger.warn("Order not found for ID: {}", orderId);
                telemetryClient.trackEvent("OrderNotFound", Map.of("orderId", orderId), null);
                
                // Record failure in OpenTelemetry
                openTelemetryService.recordOrderProcessingFailure(orderId, "order_not_found");
                openTelemetryService.setSpanError(orderProcessingSpan, new IllegalArgumentException("Order not found"));
                
                return Optional.empty();
            }

            logger.info("Order found, enriching with customer data for customer: {}", order.getCustomerId());
            
            // Add span event for successful order lookup
            openTelemetryService.addSpanEvent(orderProcessingSpan, "order.found", 
                OpenTelemetryService.createAttributes("order.id", orderId, "customer.id", order.getCustomerId()));
            
            // Enrich order with customer information
            Order enrichedOrder = enrichOrderWithCustomerData(order, orderProcessingSpan);
            
            logger.info("Successfully processed order: {} for customer: {}", orderId, order.getCustomerId());
            
            // Record success in OpenTelemetry
            openTelemetryService.recordOrderProcessingSuccess(orderId);
            openTelemetryService.setSpanSuccess(orderProcessingSpan);
            
            return Optional.of(enrichedOrder);
            
        } catch (Exception e) {
            logger.error("Unexpected error processing order: {}", orderId, e);
            
            // Track exception in Application Insights
            telemetryClient.trackException(e);
            
            // Record failure in OpenTelemetry
            openTelemetryService.recordOrderProcessingFailure(orderId, "unexpected_error");
            openTelemetryService.setSpanError(orderProcessingSpan, e);
            
            throw e;
        } finally {
            orderProcessingSpan.end();
        }
    }

    private Order enrichOrderWithCustomerData(Order order, Span parentSpan) {
        // Start OpenTelemetry span for customer enrichment
        Span customerEnrichmentSpan = openTelemetryService.startCustomerEnrichmentSpan(order.getOrderId(), order.getCustomerId());
        
        try (Scope scope = customerEnrichmentSpan.makeCurrent()) {
            logger.info("Calling customer service for customer ID: {}", order.getCustomerId());
            
            // Track dependency call
            long startTime = System.currentTimeMillis();
            String url = customerServiceBaseUrl + "/api/customers/" + order.getCustomerId();
            
            // Add span event for outbound call
            openTelemetryService.addSpanEvent(customerEnrichmentSpan, "http.request.start", 
                OpenTelemetryService.createAttributes("http.url", url, "http.method", "GET"));
            
            Customer customer = restTemplate.getForObject(url, Customer.class);
            
            long duration = System.currentTimeMillis() - startTime;
            
            // Track dependency telemetry (Application Insights)
            telemetryClient.trackDependency("HTTP", "customer-service", 
                new com.microsoft.applicationinsights.telemetry.Duration(duration), true);
            
            if (customer != null) {
                order.setCustomer(customer);
                
                // Application Insights telemetry
                telemetryClient.trackEvent("CustomerEnrichmentSuccess", 
                    Map.of("orderId", order.getOrderId(), "customerId", order.getCustomerId()), 
                    Map.of("enrichmentDuration", (double) duration));
                
                // Track success metric (Application Insights)
                telemetryClient.trackMetric("customer_enrichment_success", 1.0);
                telemetryClient.trackMetric("customer_enrichment_duration_ms", (double) duration);
                
                // Track success using Micrometer
                metricsService.recordOrderProcessingSuccess();
                
                // Track success using OpenTelemetry
                openTelemetryService.recordCustomerEnrichmentDuration(duration, order.getOrderId(), order.getCustomerId(), true);
                openTelemetryService.setSpanSuccess(customerEnrichmentSpan);
                
                // Add span event for successful enrichment
                openTelemetryService.addSpanEvent(customerEnrichmentSpan, "customer.enrichment.success", 
                    OpenTelemetryService.createAttributes("customer.name", customer.getFirstName() + " " + customer.getLastName(), 
                                                        "duration.ms", String.valueOf(duration)));
                
                logger.info("Order enriched with customer data: {} {}", 
                           customer.getFirstName(), customer.getLastName());
            } else {
                // Application Insights telemetry
                telemetryClient.trackEvent("CustomerEnrichmentEmpty", 
                    Map.of("orderId", order.getOrderId(), "customerId", order.getCustomerId()), null);
                
                // OpenTelemetry telemetry
                openTelemetryService.recordCustomerEnrichmentDuration(duration, order.getOrderId(), order.getCustomerId(), false);
                openTelemetryService.addSpanEvent(customerEnrichmentSpan, "customer.enrichment.empty_response", 
                    OpenTelemetryService.createAttributes("duration.ms", String.valueOf(duration)));
                
                logger.warn("Customer service returned null for order {}, returning order without customer data", 
                           order.getOrderId());
            }

            return order;

        } catch (Exception e) {
            logger.error("Error enriching order with customer data: {}", order.getOrderId(), e);
            
            // Track exception (Application Insights)
            telemetryClient.trackException(e);
            telemetryClient.trackEvent("CustomerEnrichmentFailure", 
                Map.of("orderId", order.getOrderId(), "customerId", order.getCustomerId(), "error", e.getMessage()), null);
            
            // Track custom metric for failure count (Application Insights way)
            telemetryClient.trackMetric("customer_enrichment_failures", 1.0);
            
            // Track more detailed failure metrics with dimensions using MetricTelemetry
            com.microsoft.applicationinsights.telemetry.MetricTelemetry failureMetric = 
                new com.microsoft.applicationinsights.telemetry.MetricTelemetry("order_processing_failures", 1.0);
            failureMetric.getProperties().put("failure_type", "customer_enrichment");
            failureMetric.getProperties().put("service", "order-service");
            failureMetric.getProperties().put("order_id", order.getOrderId());
            telemetryClient.track(failureMetric);
            
            // Track failure using Micrometer (alternative approach)
            metricsService.recordCustomerEnrichmentFailure(order.getOrderId(), e.getClass().getSimpleName());
            
            // Track failure using OpenTelemetry
            openTelemetryService.recordCustomerEnrichmentFailure(order.getOrderId(), order.getCustomerId(), e.getClass().getSimpleName());
            openTelemetryService.setSpanError(customerEnrichmentSpan, e);
            
            // Return order without customer data rather than failing completely
            return order;
        } finally {
            customerEnrichmentSpan.end();
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
