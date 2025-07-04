#!/bin/bash

# Test Distributed Tracing
echo "=== Testing Distributed Tracing ==="

# Check if services are running
echo "🔍 Checking if services are running..."

# Test Customer Service
if curl -s http://localhost:8081/api/customers/123 > /dev/null; then
    echo "✅ Customer Service is running on port 8081"
else
    echo "❌ Customer Service is not running on port 8081"
    echo "   Start it with: ./start-customer-service.sh"
    exit 1
fi

# Test Order Service
if curl -s http://localhost:8080/api/orders/ORD-001 > /dev/null; then
    echo "✅ Order Service is running on port 8080"
else
    echo "❌ Order Service is not running on port 8080"
    echo "   Start it with: ./start-order-service.sh"
    exit 1
fi

echo ""
echo "🧪 Running distributed tracing tests..."

# Test various scenarios
echo "Test 1: Order with Customer 123"
curl -s http://localhost:8080/api/orders/ORD-001 | head -c 100
echo "..."

echo ""
echo "Test 2: Order with Customer 456"
curl -s http://localhost:8080/api/orders/ORD-002 | head -c 100
echo "..."

echo ""
echo "Test 3: Direct customer lookup"
curl -s http://localhost:8081/api/customers/789 | head -c 100
echo "..."

echo ""
echo "Test 4: Error scenario - Invalid order"
curl -s http://localhost:8080/api/orders/INVALID-ORDER | head -c 100
echo "..."

echo ""
echo "✅ All tests completed!"
echo ""
echo "📊 Check your Application Insights in Azure Portal:"
echo "   - Distributed traces should appear within 1-2 minutes"
echo "   - Look for 'Application Map' to see service topology"
echo "   - Check 'End-to-end transactions' for trace correlation"
echo "   - Verify custom events and dependencies are tracked"
echo ""
echo "💡 If you don't see traces, verify that:"
echo "   1. APPLICATIONINSIGHTS_CONNECTION_STRING is set correctly"
echo "   2. Services were restarted after setting the environment variable"
echo "   3. Your Azure connection string is valid"
