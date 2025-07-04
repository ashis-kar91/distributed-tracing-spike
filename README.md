# Distributed Tracing Spike Project

This spike project demonstrates distributed tracing across multiple Spring Boot services using **Application Insights Java Agent**, with telemetry flowing to Azure Application Insights and Log Analytics Workspace (LAW).

## Architecture

The spike consists of two Spring Boot services:

1. **Order Service** (Port 8080)
   - Provides REST API: `GET /api/orders/{orderId}`
   - Calls Customer Service to enrich order data
   - Demonstrates HTTP client tracing and custom events

2. **Customer Service** (Port 8081)
   - Provides REST API: `GET /api/customers/{customerId}`
   - Simulates database operations with artificial delays
   - Demonstrates HTTP server tracing and custom events

## Telemetry Features

### Automatic Instrumentation (via Java Agent)
- ✅ HTTP requests and responses (`AppRequests`)
- ✅ HTTP client calls between services (`AppDependencies`) 
- ✅ Application logs with correlation (`AppTraces`)
- ✅ JVM performance metrics (`AppPerformanceCounters`)
- ✅ Exception tracking (`AppExceptions`)

### Custom Business Telemetry
- ✅ Custom events for business operations (`AppEvents`)
- ✅ Operation correlation across services (`OperationId`)
- ✅ Custom properties and metrics
- ✅ Structured logging with trace/span context

### Trace Flow Example
```
Order Service: GET /api/orders/ORD-001
├── AppRequests: HTTP request received
├── AppEvents: OrderRequest (custom event)
├── AppDependencies: HTTP call to customer-service
│   └── Customer Service: GET /api/customers/123
│       ├── AppRequests: HTTP request received  
│       ├── AppEvents: CustomerRequest, CustomerFound
│       └── AppDependencies: Simulated database call
└── AppEvents: OrderFound (custom event)
```

## Prerequisites

- Java 17+ 
- Maven 3.6+
- Azure subscription with Application Insights resource
- Azure Log Analytics Workspace

## Quick Start

### Step 1: Set Up Environment
```bash
# Set your Application Insights connection string
export APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=xxx;IngestionEndpoint=https://xxx.in.applicationinsights.azure.com/;LiveEndpoint=https://xxx.livediagnostics.monitor.azure.com/"
```

### Step 2: Build Project
```bash
mvn clean compile
```

### Step 3: Start Services with Java Agent
```bash
# Terminal 1 - Customer Service
./start-customer-service.sh

# Terminal 2 - Order Service  
./start-order-service.sh
```

### Step 4: Test Distributed Tracing
```bash
# Generate test traffic
curl http://localhost:8080/api/orders/ORD-001
curl http://localhost:8080/api/orders/ORD-002
curl http://localhost:8081/api/customers/123
```

### Step 5: Verify in Azure LAW
Wait 2-3 minutes, then run queries in `verify-telemetry.md` to confirm distributed tracing is working.

## Project Structure

```
spike-project/
├── ai-agent/                          # Application Insights Java Agent
│   ├── applicationinsights-agent-3.4.19.jar
│   ├── applicationinsights-customer.json
│   └── applicationinsights-order.json
├── customer-service-simple/           # Customer microservice
│   ├── src/main/java/.../customer/
│   │   ├── CustomerServiceApplication.java
│   │   ├── controller/CustomerController.java
│   │   ├── model/Customer.java
│   │   └── config/ApplicationInsightsConfig.java
│   ├── src/main/resources/application.yml
│   └── pom.xml
├── order-service-simple/              # Order microservice  
│   ├── src/main/java/.../order/
│   │   ├── OrderServiceApplication.java
│   │   ├── controller/OrderController.java
│   │   ├── service/OrderService.java
│   │   ├── model/{Order,Customer}.java
│   │   └── config/ApplicationConfig.java
│   ├── src/main/resources/application.yml
│   └── pom.xml
├── start-customer-service.sh          # Launch customer service with agent
├── start-order-service.sh             # Launch order service with agent
├── verify-telemetry.md                # LAW verification queries
├── AZURE-SETUP.md                     # Azure resource setup
└── README.md                          # This file
```

## Configuration

### Application Insights Java Agent

The project uses Application Insights Java Agent 3.4.19 for automatic instrumentation:

- **Customer Service Config**: `ai-agent/applicationinsights-customer.json`
- **Order Service Config**: `ai-agent/applicationinsights-order.json`

Both services use the same connection string via environment variable.

### Custom Events

Services send custom business events:
- `OrderRequest`, `OrderFound`, `OrderNotFound` (Order Service)
- `CustomerRequest`, `CustomerFound`, `CustomerNotFound` (Customer Service)
- `HealthCheck` events from both services

## Verification

See `verify-telemetry.md` for comprehensive LAW queries to verify:
- ✅ Distributed tracing with correlated `OperationId`
- ✅ Service-to-service dependencies  
- ✅ Custom business events with correlation
- ✅ Performance metrics and error tracking

## Troubleshooting

### No Telemetry Data
1. Check connection string: `echo $APPLICATIONINSIGHTS_CONNECTION_STRING`
2. Verify agent is loaded: look for "ApplicationInsights Java Agent" in startup logs
3. Check network connectivity to Azure

### Partial Data
- **Missing AppRequests**: Agent not attached properly
- **Missing AppDependencies**: HTTP client instrumentation disabled
- **Missing custom events**: Check TelemetryClient configuration

### LAW Query Issues
- Data appears 2-5 minutes after requests
- Use `TimeGenerated > ago(1h)` for recent data
- Check service names match: `order-service`, `customer-service`

## Additional Resources

- `AZURE-SETUP.md` - Setting up Azure resources
- `verify-telemetry.md` - Complete verification guide with KQL queries
- Application Insights Java Agent docs: https://docs.microsoft.com/en-us/azure/azure-monitor/app/java-in-process-agent


### 1. Correlation ID Propagation
- Uses W3C TraceContext standard
- Automatic HTTP header propagation
- Manual MDC setup for structured logging

### 2. Custom Instrumentation
- Business operation spans (e.g., `order.lookup`, `customer.lookup`)
- Database simulation spans
- Custom attributes for business context

### 3. Error Handling
- Exception recording in spans
- Graceful degradation (orders returned without customer data if customer service fails)
- Proper span status codes

### 4. Performance Simulation
- Variable database lookup delays (100-400ms)
- Realistic HTTP communication patterns

## Next Steps for Azure Integration

To integrate with Azure Monitor:

1. **Add Azure Monitor OpenTelemetry Exporter**:
   ```xml
   <dependency>
       <groupId>com.azure</groupId>
       <artifactId>azure-monitor-opentelemetry-exporter</artifactId>
   </dependency>
   ```

2. **Configure Connection String**:
   ```yaml
   azure:
     monitor:
       opentelemetry:
         connection-string: ${APPLICATIONINSIGHTS_CONNECTION_STRING}
   ```

3. **Deploy to Azure Container Apps** with OpenTelemetry agent configuration

## Troubleshooting

### Common Issues

1. **Port conflicts**: Ensure ports 8080 and 8081 are available
2. **Service startup order**: Start customer-service first, then order-service
3. **Java version**: Ensure Java 21 is being used

### Validation Checklist
- ✅ Both services start without errors
- ✅ Health endpoints return 200 OK
- ✅ Order requests return customer data
- ✅ Same trace ID appears in both service logs
- ✅ Parent-child span relationships visible in logs
- ✅ Custom attributes appear in span data
- ✅ Exceptions are properly recorded and propagated

## Performance Notes

This spike uses 100% sampling and detailed console logging, which is not suitable for production. For production use:
- Reduce sampling rate (e.g., 10%)
- Remove console span exporter
- Configure appropriate log levels
- Use actual Azure Monitor endpoints
