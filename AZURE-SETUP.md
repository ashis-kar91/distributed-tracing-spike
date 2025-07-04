# Distributed Tracing Spike with Azure Application Insights

This spike demonstrates distributed tracing across two Spring Boot microservices using Azure Application Insights and Log Analytics workspace.

## Architecture

```
┌─────────────────┐     HTTP GET     ┌─────────────────┐
│  Order Service  │ ──────────────→  │ Customer Service│
│   Port 8080     │                  │   Port 8081     │
└─────────────────┘                  └─────────────────┘
         │                                    │
         ▼                                    ▼
┌─────────────────┐                  ┌─────────────────┐
│ App Insights    │                  │ App Insights    │
│ (Order Service) │                  │(Customer Service)│
└─────────────────┘                  └─────────────────┘
         │                                    │
         └────────────────┬───────────────────┘
                          ▼
                ┌─────────────────┐
                │ Log Analytics   │
                │   Workspace     │
                └─────────────────┘
```

## Services

### Order Service (Port 8080)
- **Endpoint**: `GET /api/orders/{orderId}`
- **Function**: Retrieves order details and enriches with customer information
- **Dependencies**: Calls Customer Service to get customer details
- **Sample Orders**: ORD-001, ORD-002, ORD-003, ORD-004, ORD-005

### Customer Service (Port 8081)
- **Endpoint**: `GET /api/customers/{customerId}`
- **Function**: Returns customer information with simulated database delay
- **Sample Customers**: 123, 456, 789, 999

## Azure Setup

### Prerequisites
1. Azure subscription
2. Azure CLI installed and configured
3. Resource group for the spike resources

### Step 1: Create Application Insights Instances

```bash
# Set variables
RESOURCE_GROUP="rg-observability-spike"
LOCATION="eastus"
APP_INSIGHTS_ORDER="appins-order-service"
APP_INSIGHTS_CUSTOMER="appins-customer-service"
LOG_ANALYTICS_WORKSPACE="law-observability-spike"

# Create resource group
az group create --name $RESOURCE_GROUP --location $LOCATION

# Create Log Analytics Workspace
az monitor log-analytics workspace create \
  --resource-group $RESOURCE_GROUP \
  --workspace-name $LOG_ANALYTICS_WORKSPACE \
  --location $LOCATION

# Get Log Analytics Workspace ID
WORKSPACE_ID=$(az monitor log-analytics workspace show \
  --resource-group $RESOURCE_GROUP \
  --workspace-name $LOG_ANALYTICS_WORKSPACE \
  --query id \
  --output tsv)

# Create Application Insights for Order Service
az monitor app-insights component create \
  --resource-group $RESOURCE_GROUP \
  --app $APP_INSIGHTS_ORDER \
  --location $LOCATION \
  --workspace $WORKSPACE_ID

# Create Application Insights for Customer Service
az monitor app-insights component create \
  --resource-group $RESOURCE_GROUP \
  --app $APP_INSIGHTS_CUSTOMER \
  --location $LOCATION \
  --workspace $WORKSPACE_ID
```

### Step 2: Get Connection Strings

```bash
# Get Order Service connection string
ORDER_CONNECTION_STRING=$(az monitor app-insights component show \
  --resource-group $RESOURCE_GROUP \
  --app $APP_INSIGHTS_ORDER \
  --query connectionString \
  --output tsv)

# Get Customer Service connection string
CUSTOMER_CONNECTION_STRING=$(az monitor app-insights component show \
  --resource-group $RESOURCE_GROUP \
  --app $APP_INSIGHTS_CUSTOMER \
  --query connectionString \
  --output tsv)

echo "Order Service Connection String: $ORDER_CONNECTION_STRING"
echo "Customer Service Connection String: $CUSTOMER_CONNECTION_STRING"
```

### Alternative: Get Connection String from Azure Portal

1. Go to [Azure Portal](https://portal.azure.com)
2. Navigate to your Application Insights resource
3. Click on "Overview" in the left menu
4. Find "Connection String" in the Essentials section
5. Copy the connection string (it looks like: `InstrumentationKey=12345678-1234-1234-1234-123456789abc;IngestionEndpoint=https://eastus-8.in.applicationinsights.azure.com/;LiveEndpoint=https://eastus.livediagnostics.monitor.azure.com/`)

### Step 3: Set Environment Variables

**Important**: Each service should use its own Application Insights instance for proper service separation and monitoring.

```bash
# For Order Service
export APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=12345678-1234-1234-1234-123456789abc;IngestionEndpoint=https://eastus-8.in.applicationinsights.azure.com/;LiveEndpoint=https://eastus.livediagnostics.monitor.azure.com/"

# For Customer Service (in separate terminal)
export APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=87654321-4321-4321-4321-cba987654321;IngestionEndpoint=https://eastus-8.in.applicationinsights.azure.com/;LiveEndpoint=https://eastus.livediagnostics.monitor.azure.com/"
```

## Local Development Setup

### Step 1: Build the Project

```bash
cd spike-project
mvn clean compile
```

### Step 2: Start Customer Service

```bash
# Terminal 1 - Customer Service
cd customer-service-simple
export APPLICATIONINSIGHTS_CONNECTION_STRING="<customer-service-connection-string>"
mvn spring-boot:run
```

### Step 3: Start Order Service

```bash
# Terminal 2 - Order Service
cd order-service-simple
export APPLICATIONINSIGHTS_CONNECTION_STRING="<order-service-connection-string>"
mvn spring-boot:run
```

### Step 4: Test the Services

```bash
# Test customer service directly
curl http://localhost:8081/api/customers/123

# Test order service (will call customer service)
curl http://localhost:8080/api/orders/ORD-001

# Test with different customers
curl http://localhost:8080/api/orders/ORD-002
curl http://localhost:8080/api/orders/ORD-003

# Test error scenarios
curl http://localhost:8080/api/orders/INVALID-ORDER
curl http://localhost:8081/api/customers/INVALID-CUSTOMER
```

## Success Criteria Checklist

- [ ] Both services start successfully
- [ ] Services communicate via HTTP
- [ ] Traces appear in both Application Insights instances
- [ ] Single trace ID spans across both services
- [ ] Parent-child span relationships are correct
- [ ] Custom events and dependencies are tracked
- [ ] Exception handling creates proper telemetry
- [ ] Application Map shows service topology
- [ ] End-to-end transactions are visible in Azure Portal
