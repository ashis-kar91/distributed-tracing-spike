# Telemetry Issue Resolution

## Problem
The distributed tracing spike was working functionally (services communicating correctly), but **no traces were appearing in Azure Application Insights**.

## Root Cause
The issue was that the `APPLICATIONINSIGHTS_CONNECTION_STRING` environment variable was not set, which is required for Azure Application Insights to receive telemetry data.

## Evidence of the Problem
Looking at the service logs, we could see:
```
2025-07-04 15:47:04 [ActiveTransmissionNetworkOutput_1-0] DEBUG ... 
HTTP/1.1 400 Bad Request
...
"errors":[{"index":0,"statusCode":400,"message":"Invalid instrumentation key"}]
```

This confirms that the services were trying to send telemetry to Azure but failing due to missing/invalid credentials.

## Solution Steps

### 1. Updated Configuration Files
- **Before**: Used deprecated `APPLICATIONINSIGHTS_INSTRUMENTATION_KEY`
- **After**: Updated to modern `APPLICATIONINSIGHTS_CONNECTION_STRING`

Updated files:
- `/customer-service-simple/src/main/resources/ApplicationInsights.xml`
- `/order-service-simple/src/main/resources/ApplicationInsights.xml`

### 2. Enhanced Azure Setup Documentation
- Added detailed steps in `AZURE-SETUP.md`
- Created automated `setup-azure.sh` script
- Added manual portal instructions as alternative

### 3. Created Testing Tools
- `test-tracing.sh` - Validates that services are running and generates test traffic
- Enhanced `quick-start.sh` with better guidance

## How to Fix Your Setup

### Option A: Use the Automated Script
```bash
# Run the Azure setup script
./setup-azure.sh

# Follow the output instructions to set environment variables
```

### Option B: Manual Setup via Azure Portal
1. Go to [Azure Portal](https://portal.azure.com)
2. Create Application Insights resources for both services
3. Copy the Connection String from each resource
4. Set the environment variable before starting each service

### Option C: Manual Setup via Azure CLI
```bash
# See detailed commands in AZURE-SETUP.md
az monitor app-insights component create ...
az monitor app-insights component show --query connectionString ...
```

## Verification Steps

1. **Set the environment variable** in each terminal before starting the services
2. **Restart the services** after setting the environment variable
3. **Generate test traffic** using the test script or manual curl commands
4. **Check Azure Portal** - traces should appear within 1-2 minutes

## Expected Results After Fix

### In Service Logs
- No more "Invalid instrumentation key" errors
- Successful HTTP 200 responses to Azure Application Insights

### In Azure Portal
- Distributed traces spanning both services
- Application Map showing service topology
- Custom events and dependencies tracked
- End-to-end transaction correlation

## Files Modified
- `customer-service-simple/src/main/resources/ApplicationInsights.xml`
- `order-service-simple/src/main/resources/ApplicationInsights.xml`
- `AZURE-SETUP.md` (enhanced)
- `README.md` (updated quick start)
- `setup-azure.sh` (new)
- `test-tracing.sh` (new)

## Key Takeaway
The distributed tracing code was working correctly from the start. The only missing piece was the Azure Application Insights connection string configuration. Once this is set, telemetry will flow to Azure and traces will be visible in the portal.
