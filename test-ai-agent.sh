#!/bin/bash

echo "=== Application Insights Java Agent Test ==="
echo ""

# Check if connection string is set
if [ -z "$APPLICATIONINSIGHTS_CONNECTION_STRING" ]; then
    echo "❌ APPLICATIONINSIGHTS_CONNECTION_STRING is not set!"
    echo ""
    echo "To fix this:"
    echo "1. Get your connection string from Azure Portal > Application Insights > Overview"
    echo "2. Set it as environment variable:"
    echo "   export APPLICATIONINSIGHTS_CONNECTION_STRING=\"InstrumentationKey=...\""
    echo ""
    echo "Then restart this test."
    exit 1
fi

echo "✅ APPLICATIONINSIGHTS_CONNECTION_STRING is set"
echo ""

# Check if agent file exists
if [ ! -f "ai-agent/applicationinsights-agent-3.4.19.jar" ]; then
    echo "❌ Application Insights agent not found!"
    echo "   Expected: ai-agent/applicationinsights-agent-3.4.19.jar"
    exit 1
fi

echo "✅ Application Insights agent found"
echo ""

# Check if services are running
echo "🔍 Checking if services are running..."

if curl -s http://localhost:8081/api/customers/123 > /dev/null 2>&1; then
    echo "✅ Customer Service is running on port 8081"
else
    echo "❌ Customer Service is not running"
    echo "   Start it with: ./start-customer-service.sh"
fi

if curl -s http://localhost:8080/api/orders/ORD-001 > /dev/null 2>&1; then
    echo "✅ Order Service is running on port 8080"
else
    echo "❌ Order Service is not running"
    echo "   Start it with: ./start-order-service.sh"
fi

echo ""
echo "📝 Instructions:"
echo "1. Start Customer Service: ./start-customer-service.sh"
echo "2. Start Order Service (new terminal): ./start-order-service.sh"
echo "3. Test requests: curl http://localhost:8080/api/orders/ORD-001"
echo "4. Check Application Insights in Azure Portal for AppRequests table"
echo ""
echo "⏰ HTTP requests should appear in Application Insights within 1-2 minutes"
