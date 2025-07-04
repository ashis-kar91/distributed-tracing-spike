#!/bin/bash

# Azure Application Insights Setup Script
# This script creates Azure resources and provides connection strings

set -e

echo "=== Azure Application Insights Setup ==="
echo ""

# Check if Azure CLI is installed
if ! command -v az &> /dev/null; then
    echo "❌ Azure CLI is not installed. Please install Azure CLI first."
    echo "   Visit: https://docs.microsoft.com/en-us/cli/azure/install-azure-cli"
    exit 1
fi

# Check if logged in to Azure
if ! az account show &> /dev/null; then
    echo "❌ Not logged in to Azure. Please run 'az login' first."
    exit 1
fi

# Set variables
RESOURCE_GROUP="rg-observability-spike"
LOCATION="eastus"
APP_INSIGHTS_ORDER="appins-order-service"
APP_INSIGHTS_CUSTOMER="appins-customer-service"
LOG_ANALYTICS_WORKSPACE="law-observability-spike"

echo "🔨 Creating Azure resources..."
echo "   Resource Group: $RESOURCE_GROUP"
echo "   Location: $LOCATION"
echo ""

# Create resource group
echo "📁 Creating resource group..."
az group create --name $RESOURCE_GROUP --location $LOCATION > /dev/null

# Create Log Analytics Workspace
echo "📊 Creating Log Analytics workspace..."
az monitor log-analytics workspace create \
  --resource-group $RESOURCE_GROUP \
  --workspace-name $LOG_ANALYTICS_WORKSPACE \
  --location $LOCATION > /dev/null

# Get Log Analytics Workspace ID
echo "🔍 Getting workspace ID..."
WORKSPACE_ID=$(az monitor log-analytics workspace show \
  --resource-group $RESOURCE_GROUP \
  --workspace-name $LOG_ANALYTICS_WORKSPACE \
  --query id \
  --output tsv)

# Create Application Insights for Order Service
echo "📈 Creating Application Insights for Order Service..."
az monitor app-insights component create \
  --resource-group $RESOURCE_GROUP \
  --app $APP_INSIGHTS_ORDER \
  --location $LOCATION \
  --workspace $WORKSPACE_ID > /dev/null

# Create Application Insights for Customer Service
echo "📈 Creating Application Insights for Customer Service..."
az monitor app-insights component create \
  --resource-group $RESOURCE_GROUP \
  --app $APP_INSIGHTS_CUSTOMER \
  --location $LOCATION \
  --workspace $WORKSPACE_ID > /dev/null

echo ""
echo "✅ Azure resources created successfully!"
echo ""

# Get connection strings
echo "🔗 Getting connection strings..."
ORDER_CONNECTION_STRING=$(az monitor app-insights component show \
  --resource-group $RESOURCE_GROUP \
  --app $APP_INSIGHTS_ORDER \
  --query connectionString \
  --output tsv)

CUSTOMER_CONNECTION_STRING=$(az monitor app-insights component show \
  --resource-group $RESOURCE_GROUP \
  --app $APP_INSIGHTS_CUSTOMER \
  --query connectionString \
  --output tsv)

echo ""
echo "🚀 Setup Complete! Use the following connection strings:"
echo ""
echo "=== ORDER SERVICE CONNECTION STRING ==="
echo "export APPLICATIONINSIGHTS_CONNECTION_STRING=\"$ORDER_CONNECTION_STRING\""
echo ""
echo "=== CUSTOMER SERVICE CONNECTION STRING ==="
echo "export APPLICATIONINSIGHTS_CONNECTION_STRING=\"$CUSTOMER_CONNECTION_STRING\""
echo ""
echo "📝 Next steps:"
echo "   1. Copy the connection strings above"
echo "   2. Set the environment variable in each terminal before starting the services"
echo "   3. Restart the services to pick up the new configuration"
echo ""
echo "💡 Quick start commands:"
echo "   # Terminal 1 - Customer Service"
echo "   export APPLICATIONINSIGHTS_CONNECTION_STRING=\"$CUSTOMER_CONNECTION_STRING\""
echo "   cd customer-service-simple && mvn spring-boot:run"
echo ""
echo "   # Terminal 2 - Order Service"
echo "   export APPLICATIONINSIGHTS_CONNECTION_STRING=\"$ORDER_CONNECTION_STRING\""
echo "   cd order-service-simple && mvn spring-boot:run"
echo ""
echo "🌐 Monitor your applications:"
echo "   Azure Portal: https://portal.azure.com"
echo "   Application Insights: $APP_INSIGHTS_ORDER and $APP_INSIGHTS_CUSTOMER"
echo "   Log Analytics: $LOG_ANALYTICS_WORKSPACE"
