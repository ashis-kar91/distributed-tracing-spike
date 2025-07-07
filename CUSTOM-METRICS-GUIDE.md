# Custom Metrics and Alerting Guide

## Overview

This guide demonstrates how to emit custom metrics and set up alerting for failure scenarios in the distributed tracing spike project.

## Custom Metrics Implementation

### Approach 1: Application Insights TelemetryClient (Recommended)

**Advantages:**
- Direct integration with Application Insights
- Appears in customMetrics table in LAW
- Simple to implement with existing setup
- Supports custom properties and dimensions

**Implementation:**
```java
// Simple counter metric
telemetryClient.trackMetric("customer_enrichment_failures", 1.0);

// Metric with custom properties
MetricTelemetry failureMetric = new MetricTelemetry("order_processing_failures", 1.0);
failureMetric.getProperties().put("failure_type", "customer_enrichment");
failureMetric.getProperties().put("service", "order-service");
failureMetric.getProperties().put("order_id", orderId);
telemetryClient.track(failureMetric);
```

### Approach 2: Micrometer Integration

**Advantages:**
- Industry standard metrics library
- Better dimensional metrics support
- Integration with multiple monitoring systems
- Rich metric types (Counter, Timer, Gauge, etc.)

**Implementation:**
```java
@Service
public class MetricsService {
    private final MeterRegistry meterRegistry;
    
    public void recordCustomerEnrichmentFailure(String orderId, String errorType) {
        Counter.builder("customer.enrichment.failure")
                .tag("service", "order-service")
                .tag("error_type", errorType)
                .tag("order_id", orderId)
                .register(meterRegistry)
                .increment();
    }
}
```

## Verification Queries

### Check Custom Metrics in LAW

**Application Insights Metrics:**
```kusto
customMetrics
| where TimeGenerated > ago(1h)
| where name in ("customer_enrichment_failures", "order_processing_failures")
| project TimeGenerated, name, value, customDimensions
| order by TimeGenerated desc
```

**Micrometer Metrics (via Performance Counters):**
```kusto
performanceCounters
| where TimeGenerated > ago(1h)
| where counterName contains "customer.enrichment.failure"
| project TimeGenerated, counterName, value, instance
| order by TimeGenerated desc
```

**Alternative Query for Micrometer Metrics:**
```kusto
// Micrometer metrics appear in customMetrics table when using Application Insights
customMetrics
| where TimeGenerated > ago(1h)
| where name contains "customer.enrichment"
| project TimeGenerated, name, value, customDimensions
| order by TimeGenerated desc
```

## Setting Up Alerts

### Azure CLI Alert Setup

**1. Create Alert for 3 Failures in 1 Hour:**

```bash
# Create alert rule for Application Insights metrics
az monitor metrics alert create \
  --name "CustomerEnrichmentFailures" \
  --resource-group observability-rg \
  --scopes /subscriptions/{subscription-id}/resourceGroups/observability-rg/providers/Microsoft.Insights/components/order-service-insights \
  --condition "avg customMetrics/customer_enrichment_failures > 3" \
  --window-size 1h \
  --evaluation-frequency 5m \
  --severity 2 \
  --description "Alert when customer enrichment failures exceed 3 in 1 hour" \
  --action-group /subscriptions/{subscription-id}/resourceGroups/observability-rg/providers/Microsoft.Insights/actionGroups/critical-alerts
```

**2. Create Action Group for Notifications:**

```bash
# Create action group for email notifications
az monitor action-group create \
  --name critical-alerts \
  --resource-group observability-rg \
  --short-name crit-alert \
  --email-receivers name=ops-team email=ops@company.com
```

### Log Analytics Alert (Alternative)

**1. KQL Query for Alert:**
```kusto
customMetrics
| where TimeGenerated > ago(1h)
| where name == "customer_enrichment_failures"
| summarize TotalFailures = sum(value) by bin(TimeGenerated, 1h)
| where TotalFailures >= 3
```

**2. Create Log Analytics Alert:**
```bash
az monitor scheduled-query create \
  --name "CustomerEnrichmentFailuresLAW" \
  --resource-group observability-rg \
  --scopes /subscriptions/{subscription-id}/resourceGroups/observability-rg/providers/Microsoft.OperationalInsights/workspaces/central-law \
  --condition-query "customMetrics | where TimeGenerated > ago(1h) | where name == 'customer_enrichment_failures' | summarize TotalFailures = sum(value) | where TotalFailures >= 3" \
  --condition-threshold 1 \
  --condition-operator GreaterThanOrEqual \
  --evaluation-frequency 5m \
  --window-size 1h \
  --severity 2 \
  --action-groups /subscriptions/{subscription-id}/resourceGroups/observability-rg/providers/Microsoft.Insights/actionGroups/critical-alerts
```

## Advanced Alerting Scenarios

### 1. Failure Rate Alert (Percentage-based)

```kusto
// Calculate failure rate over last hour
let timeRange = 1h;
let failures = customMetrics
    | where TimeGenerated > ago(timeRange)
    | where name == "customer_enrichment_failures"
    | summarize FailureCount = sum(value);
let successes = customMetrics
    | where TimeGenerated > ago(timeRange)
    | where name == "customer_enrichment_success"
    | summarize SuccessCount = sum(value);
failures
| extend TotalRequests = toscalar(successes | project SuccessCount) + FailureCount
| extend FailureRate = (FailureCount * 100.0) / TotalRequests
| where FailureRate > 5.0  // Alert if failure rate > 5%
```

### 2. Consecutive Failures Alert

```kusto
// Alert on 3 consecutive failures within 10 minutes
customMetrics
| where TimeGenerated > ago(10m)
| where name == "customer_enrichment_failures"
| order by TimeGenerated asc
| serialize
| extend ConsecutiveFailures = row_cumsum(value)
| where ConsecutiveFailures >= 3
```

### 3. Anomaly Detection Alert

```kusto
// Use built-in anomaly detection
customMetrics
| where TimeGenerated > ago(7d)
| where name == "customer_enrichment_failures"
| make-series FailureCount = sum(value) on TimeGenerated step 1h
| extend Anomalies = series_decompose_anomalies(FailureCount, 1.5, 7, 'linefit')
| mv-expand TimeGenerated, FailureCount, Anomalies
| where Anomalies == 1  // 1 indicates anomaly
```

## Testing the Alerts

### 1. Generate Test Failures

```bash
# Script to generate failures for testing
for i in {1..5}; do
  curl "http://localhost:8080/api/orders/INVALID-ORDER-$i"
  sleep 10
done
```

### 2. Verify Metrics Collection

```kusto
// Check if metrics are being collected
customMetrics
| where TimeGenerated > ago(30m)
| where name contains "customer_enrichment"
| summarize count() by name, bin(TimeGenerated, 5m)
| order by TimeGenerated desc
```

### 3. Test Alert Logic

```kusto
// Test the alert condition
customMetrics
| where TimeGenerated > ago(1h)
| where name == "customer_enrichment_failures"
| summarize TotalFailures = sum(value)
| extend ShouldAlert = TotalFailures >= 3
```

## Dashboard Integration

### Custom Dashboard Widget

```kusto
// Widget for real-time failure monitoring
customMetrics
| where TimeGenerated > ago(24h)
| where name in ("customer_enrichment_failures", "customer_enrichment_success")
| summarize Value = sum(value) by name, bin(TimeGenerated, 1h)
| render timechart
```

## Best Practices

1. **Use Descriptive Metric Names**: Follow naming conventions like `service.component.metric_type`
2. **Add Relevant Dimensions**: Include service name, operation type, error categories
3. **Set Appropriate Alert Thresholds**: Balance between noise and missing critical issues
4. **Test Alert Logic**: Validate alerts fire correctly with synthetic data
5. **Document Alert Runbooks**: Include investigation and resolution steps
6. **Use Multiple Alert Channels**: Email, Teams, SMS for different severity levels
7. **Monitor Alert Performance**: Track false positives and alert resolution times

## Troubleshooting

### Metrics Not Appearing

1. **Check Agent Configuration**: Ensure Micrometer is enabled in Application Insights agent
2. **Verify Connection String**: Confirm telemetry is flowing to correct App Insights instance
3. **Check Sampling**: High sampling rates might filter out metrics
4. **Wait for Ingestion**: Metrics can take 2-5 minutes to appear in LAW

### Alerts Not Firing

1. **Validate Query Logic**: Test alert queries manually in LAW
2. **Check Time Windows**: Ensure evaluation frequency and window size are appropriate
3. **Verify Action Groups**: Confirm notification channels are configured correctly
4. **Check Alert State**: Alerts might be in suppressed or disabled state
