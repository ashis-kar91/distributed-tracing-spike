#!/bin/bash

# Stop all running services
echo "🛑 Stopping all services..."

# Kill all Java processes running the services
pkill -f "spring-boot:run"
pkill -f "order-service-simple"
pkill -f "customer-service-simple"

echo "✅ All services stopped"

# Clean up log files
if [ -f customer-service.log ]; then
    rm customer-service.log
    echo "🧹 Cleaned up customer-service.log"
fi

if [ -f order-service.log ]; then
    rm order-service.log
    echo "🧹 Cleaned up order-service.log"
fi

echo "✅ Cleanup complete"
