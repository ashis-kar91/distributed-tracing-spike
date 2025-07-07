package com.observability.spike.order.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class MetricsService {
    
    private final MeterRegistry meterRegistry;
    private final Counter orderProcessingSuccessCounter;
    
    public MetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // Initialize counters with tags for better dimensional analysis
        this.orderProcessingSuccessCounter = Counter.builder("order.processing.success")
                .description("Count of successful order processing operations")
                .tag("service", "order-service")
                .register(meterRegistry);
    }
    
    public void recordOrderProcessingSuccess() {
        orderProcessingSuccessCounter.increment();
    }
    
    public void recordOrderProcessingFailure(String failureType) {
        // Create counter with dynamic tags
        Counter.builder("order.processing.failure")
                .tag("service", "order-service")
                .tag("failure_type", failureType)
                .register(meterRegistry)
                .increment();
    }
    
    public void recordCustomerEnrichmentFailure(String orderId, String errorType) {
        // Create counter with dynamic tags for failures
        Counter.builder("customer.enrichment.failure")
                .tag("service", "order-service")
                .tag("operation", "customer_enrichment")
                .tag("error_type", errorType)
                .tag("order_id", orderId)
                .register(meterRegistry)
                .increment();
    }
    
    public Timer.Sample startCustomerEnrichmentTimer() {
        return Timer.start(meterRegistry);
    }
    
    public void recordCustomerEnrichmentDuration(Timer.Sample sample, boolean success) {
        sample.stop(Timer.builder("customer.enrichment.duration")
                .tag("service", "order-service")
                .tag("success", String.valueOf(success))
                .register(meterRegistry));
    }
}
