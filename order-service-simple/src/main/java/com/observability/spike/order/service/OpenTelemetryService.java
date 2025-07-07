package com.observability.spike.order.service;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.stereotype.Service;

@Service
public class OpenTelemetryService {

    private final Tracer tracer;
    private final Meter meter;
    private final LongCounter orderProcessingCounter;
    private final LongCounter customerEnrichmentFailureCounter;
    private final LongHistogram customerEnrichmentDuration;

    // Attribute keys for consistent metadata
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");
    private static final AttributeKey<String> ORDER_ID = AttributeKey.stringKey("order.id");
    private static final AttributeKey<String> CUSTOMER_ID = AttributeKey.stringKey("customer.id");
    private static final AttributeKey<String> OPERATION = AttributeKey.stringKey("operation");
    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
    private static final AttributeKey<String> OUTCOME = AttributeKey.stringKey("outcome");

    public OpenTelemetryService(Tracer otelTracer, Meter otelMeter) {
        this.tracer = otelTracer;
        this.meter = otelMeter;

        // Initialize metrics
        this.orderProcessingCounter = meter
                .counterBuilder("order.processing.operations")
                .setDescription("Count of order processing operations")
                .setUnit("1")
                .build();

        this.customerEnrichmentFailureCounter = meter
                .counterBuilder("customer.enrichment.failures")
                .setDescription("Count of customer enrichment failures")
                .setUnit("1")
                .build();

        this.customerEnrichmentDuration = meter
                .histogramBuilder("customer.enrichment.duration")
                .setDescription("Duration of customer enrichment operations")
                .setUnit("ms")
                .ofLongs()
                .build();
    }

    public Span startOrderProcessingSpan(String orderId) {
        return tracer.spanBuilder("order.processing")
                .setSpanKind(io.opentelemetry.api.trace.SpanKind.INTERNAL)
                .setAttribute(SERVICE_NAME, "order-service")
                .setAttribute(ORDER_ID, orderId)
                .setAttribute(OPERATION, "order_lookup")
                .startSpan();
    }

    public Span startCustomerEnrichmentSpan(String orderId, String customerId) {
        return tracer.spanBuilder("customer.enrichment")
                .setSpanKind(io.opentelemetry.api.trace.SpanKind.CLIENT)
                .setAttribute(SERVICE_NAME, "order-service")
                .setAttribute(ORDER_ID, orderId)
                .setAttribute(CUSTOMER_ID, customerId)
                .setAttribute(OPERATION, "customer_enrichment")
                .startSpan();
    }

    public void recordOrderProcessingSuccess(String orderId) {
        orderProcessingCounter.add(1, Attributes.of(
                SERVICE_NAME, "order-service",
                ORDER_ID, orderId,
                OUTCOME, "success"
        ));
    }

    public void recordOrderProcessingFailure(String orderId, String failureType) {
        orderProcessingCounter.add(1, Attributes.of(
                SERVICE_NAME, "order-service",
                ORDER_ID, orderId,
                OUTCOME, "failure",
                ERROR_TYPE, failureType
        ));
    }

    public void recordCustomerEnrichmentFailure(String orderId, String customerId, String errorType) {
        customerEnrichmentFailureCounter.add(1, Attributes.of(
                SERVICE_NAME, "order-service",
                ORDER_ID, orderId,
                CUSTOMER_ID, customerId,
                ERROR_TYPE, errorType
        ));
    }

    public void recordCustomerEnrichmentDuration(long durationMs, String orderId, String customerId, boolean success) {
        customerEnrichmentDuration.record(durationMs, Attributes.of(
                SERVICE_NAME, "order-service",
                ORDER_ID, orderId,
                CUSTOMER_ID, customerId,
                OUTCOME, success ? "success" : "failure"
        ));
    }

    public void addSpanEvent(Span span, String eventName, Attributes attributes) {
        span.addEvent(eventName, attributes);
    }

    public void setSpanError(Span span, Throwable throwable) {
        span.setStatus(StatusCode.ERROR, throwable.getMessage());
        span.recordException(throwable);
    }

    public void setSpanSuccess(Span span) {
        span.setStatus(StatusCode.OK);
    }

    // Utility method to create attributes
    public static Attributes createAttributes(String key1, String value1) {
        return Attributes.of(AttributeKey.stringKey(key1), value1);
    }

    public static Attributes createAttributes(String key1, String value1, String key2, String value2) {
        return Attributes.of(
                AttributeKey.stringKey(key1), value1,
                AttributeKey.stringKey(key2), value2
        );
    }

    public static Attributes createAttributes(String key1, String value1, String key2, String value2, String key3, String value3) {
        return Attributes.of(
                AttributeKey.stringKey(key1), value1,
                AttributeKey.stringKey(key2), value2,
                AttributeKey.stringKey(key3), value3
        );
    }
}
