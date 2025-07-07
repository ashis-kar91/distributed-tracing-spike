#!/bin/bash

# Test script for running services without Azure Application Insights
# This uses a mock connection string for local testing of OpenTelemetry integration

echo "🧪 Starting services in TEST MODE (without Azure Application Insights)"
echo "   This mode will use mock connection strings for local testing"
echo ""

# Set mock connection string for testing
export APPLICATIONINSIGHTS_CONNECTION_STRING="InstrumentationKey=00000000-0000-0000-0000-000000000000;IngestionEndpoint=https://test.applicationinsights.azure.com/"

echo "✅ Mock connection string set for testing"
echo "🔧 Starting customer service..."

# Start customer service in background
cd customer-service-simple
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:../ai-agent/applicationinsights-agent-3.4.19.jar -Dapplicationinsights.configuration.file=../ai-agent/applicationinsights-customer.json" \
  > ../customer-service.log 2>&1 &

CUSTOMER_PID=$!
cd ..

echo "✅ Customer service started (PID: $CUSTOMER_PID)"
echo "🔧 Starting order service..."

# Start order service in background
cd order-service-simple
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:../ai-agent/applicationinsights-agent-3.4.19.jar -Dapplicationinsights.configuration.file=../ai-agent/applicationinsights-order.json" \
  > ../order-service.log 2>&1 &

ORDER_PID=$!
cd ..

echo "✅ Order service started (PID: $ORDER_PID)"
echo ""
echo "🎯 Services are starting up..."
echo "   Customer Service: http://localhost:8081 (PID: $CUSTOMER_PID)"
echo "   Order Service: http://localhost:8080 (PID: $ORDER_PID)"
echo ""
echo "📝 Log files:"
echo "   Customer Service: customer-service.log"
echo "   Order Service: order-service.log"
echo ""
echo "⏳ Waiting 30 seconds for services to start..."
sleep 30

echo ""
echo "🧪 Testing the services..."
echo "📞 Testing customer service..."
curl -f http://localhost:8081/api/customers/123 > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Customer service is responding"
else
    echo "❌ Customer service is not responding"
fi

echo "📞 Testing order service..."
curl -f http://localhost:8080/api/orders/ORD-001 > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✅ Order service is responding"
else
    echo "❌ Order service is not responding"
fi

echo ""
echo "🔍 To test the OpenTelemetry integration manually:"
echo "   curl http://localhost:8080/api/orders/ORD-001"
echo "   curl http://localhost:8080/api/orders/ORD-002"
echo "   curl http://localhost:8080/api/orders/ORD-999  # This should fail"
echo ""
echo "🛑 To stop services:"
echo "   kill $CUSTOMER_PID $ORDER_PID"
echo "   Or run: ./stop-services.sh"
