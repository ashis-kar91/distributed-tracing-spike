#!/bin/bash

# Start Order Service with Application Insights Java Agent
echo "Starting Order Service with Application Insights Java Agent..."

# Check if connection string is set
if [ -z "$APPLICATIONINSIGHTS_CONNECTION_STRING" ]; then
    echo "❌ APPLICATIONINSIGHTS_CONNECTION_STRING environment variable is not set!"
    echo "   Please set it first: export APPLICATIONINSIGHTS_CONNECTION_STRING=\"your-connection-string\""
    exit 1
fi

echo "✅ Connection string is set"
echo "🚀 Starting order service on port 8080..."

cd order-service-simple

# Start with Application Insights Java Agent
mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-javaagent:../ai-agent/applicationinsights-agent-3.4.19.jar -Dapplicationinsights.configuration.file=../ai-agent/applicationinsights-order.json"
