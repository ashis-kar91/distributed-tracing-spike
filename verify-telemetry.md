# Distributed Tracing Verification Guide

## Overview

This guide explains how to verify distributed tracing is working correctly in Azure Log Analytics Workspace (LAW) using KQL queries. The project uses Application Insights Java Agent for automatic instrumentation.

## Prerequisites

1. Both services are running with Application Insights Java Agent
2. Connection string is properly configured
3. You've generated some test traffic

## Quick Test Setup

### Start Services
```bash
# Terminal 1 - Customer Service
./start-customer-service.sh

# Terminal 2 - Order Service  
./start-order-service.sh
```

### Generate Test Traffic
```bash
# Test the distributed tracing flow
curl http://localhost:8080/api/orders/ORD-001
curl http://localhost:8080/api/orders/ORD-002
curl http://localhost:8081/api/customers/123
```

Wait 2-3 minutes for telemetry to appear in LAW, then run verification queries.

## Verification Queries for LAW

### 1. Check All Available Telemetry Tables
```kusto
// Verify all telemetry types are being captured
union App*
| where TimeGenerated > ago(1h)
| summarize count() by $table, AppRoleName
| order by $table, AppRoleName
```

**Expected Results:**
- `AppRequests` - HTTP requests to both services
- `AppDependencies` - HTTP calls between services  
- `AppTraces` - Application logs with correlation
- `AppEvents` - Custom business events
- `AppPerformanceCounters` - JVM metrics

### 2. Distributed Tracing Validation (Key Query)
```kusto
// Find correlated transactions across services
AppRequests
| where TimeGenerated > ago(1h)
| where AppRoleName in ("order-service", "customer-service")
| extend RequestPath = replace_string(Url, strcat("http://", extract(@"//([^/]+)", 1, Url)), "")
| project TimeGenerated, AppRoleName, RequestPath, OperationId, Id, ParentId, DurationMs, Success
| join kind=leftouter (
    AppDependencies
    | where TimeGenerated > ago(1h) 
    | where Type == "Http"
    | project DepOperationId=OperationId, DepName=Name, DepDuration=DurationMs, DepSuccess=Success, DepTarget=Target
) on $left.OperationId == $right.DepOperationId
| order by TimeGenerated desc
```

**Expected Results:**
- Same `OperationId` for order service request and customer service dependency
- Order service shows HTTP dependency to customer service
- Customer service shows corresponding HTTP request
- Proper parent-child relationship via `ParentId`

### 3. Custom Events Correlation
```kusto
// Verify custom business events are correlated
AppEvents
| where TimeGenerated > ago(1h)
| where AppRoleName in ("order-service", "customer-service")
| project TimeGenerated, AppRoleName, Name, OperationId, Properties
| order by OperationId, TimeGenerated
```

**Expected Results:**
- `OrderRequest`, `OrderFound` events from order-service
- `CustomerRequest`, `CustomerFound` events from customer-service  
- Same `OperationId` for events in the same transaction

### 4. End-to-End Transaction View
```kusto
// Complete transaction flow for a specific operation
let targetOperationId = toscalar(
    AppRequests 
    | where TimeGenerated > ago(1h) and AppRoleName == "order-service"
    | top 1 by TimeGenerated desc
    | project OperationId
);
union App*
| where TimeGenerated > ago(1h)
| where OperationId == targetOperationId
| project TimeGenerated, $table, AppRoleName, Name, OperationId, ParentId
| order by TimeGenerated
```

**Expected Results:**
1. `AppRequests` - Initial order service request
2. `AppEvents` - OrderRequest event
3. `AppDependencies` - HTTP call to customer service
4. `AppRequests` - Customer service receives request
5. `AppEvents` - CustomerRequest, CustomerFound events
6. `AppEvents` - OrderFound event

### 5. Performance Analysis
```kusto
// Analyze response times and dependencies
AppRequests
| where TimeGenerated > ago(1h)
| where AppRoleName in ("order-service", "customer-service")
| extend RequestPath = replace_string(Url, strcat("http://", extract(@"//([^/]+)", 1, Url)), "")
| summarize 
    RequestCount = count(),
    AvgDurationMs = avg(DurationMs),
    P95DurationMs = percentile(DurationMs, 95),
    SuccessRate = avg(todouble(Success)) * 100
by AppRoleName, RequestPath
| order by AppRoleName, RequestPath
```

### 6. Error Detection
```kusto
// Check for failed requests or exceptions
union AppRequests, AppDependencies, AppExceptions
| where TimeGenerated > ago(1h)
| where AppRoleName in ("order-service", "customer-service")
| where Success == false or isnotempty(ProblemId)
| project TimeGenerated, $table, AppRoleName, Name, OperationId, Success, ResultCode
| order by TimeGenerated desc
```

## Troubleshooting

### Missing Tables
If you don't see certain tables:

- **No AppRequests**: Java agent not attached or connection string missing
- **No AppDependencies**: HTTP client instrumentation not working  
- **No AppTraces**: Application logs not flowing or logging level too high
- **No AppEvents**: Custom events not being sent (check TelemetryClient usage)

### Verification Commands
```kusto
// Quick health check
search in (App*) TimeGenerated > ago(1h) and ("order-service" or "customer-service")
| summarize count() by $table
| order by count_ desc
```

### No Data at All
If no data appears, check:
1. Connection string is set: `echo $APPLICATIONINSIGHTS_CONNECTION_STRING`
2. Services are running with agent: check startup logs for "ApplicationInsights Java Agent"
3. Network connectivity to Azure

## Expected Distributed Tracing Behavior

### ✅ Successful Scenario:
1. **Single Operation ID**: All events for one user request share the same `OperationId`
2. **Cross-Service Correlation**: Order service dependency call correlates with customer service request
3. **Proper Timing**: Events appear in chronological order within the transaction
4. **Business Context**: Custom events capture business operations (OrderRequest, CustomerFound, etc.)
5. **Performance Visibility**: Clear view of service-to-service call durations

### ❌ Issues to Watch For:
- **Different Operation IDs**: Correlation headers not propagating
- **Missing Dependencies**: HTTP client calls not instrumented
- **No Custom Events**: TelemetryClient not configured properly
- **Time Gaps**: Large delays indicate configuration or network issues

## Application Insights Portal

You can also verify in the Azure portal:
1. **Application Map**: Visual topology showing service dependencies
2. **End-to-end transactions**: Click on a request to see the full trace
3. **Performance blade**: Analyze response times and failure rates
4. **Live Metrics**: Real-time telemetry stream

