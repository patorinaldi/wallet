#!/bin/bash
# Vault Secret Initialization Script for Wallet Microservices
# Run this script after starting Vault in dev mode

set -e

# Configuration
VAULT_ADDR="${VAULT_ADDR:-http://localhost:8200}"
VAULT_TOKEN="${VAULT_TOKEN:-dev-root-token}"

echo "Initializing Vault secrets..."
echo "VAULT_ADDR: $VAULT_ADDR"

# Wait for Vault to be ready
echo "Waiting for Vault to be ready..."
until curl -s "$VAULT_ADDR/v1/sys/health" | grep -q '"initialized":true'; do
    echo "  Vault not ready, waiting..."
    sleep 2
done
echo "Vault is ready!"

# Enable KV secrets engine v2 at secret/ path (if not already enabled)
echo "Enabling KV secrets engine..."
curl -s -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -d '{"type": "kv", "options": {"version": "2"}}' \
    "$VAULT_ADDR/v1/sys/mounts/secret" 2>/dev/null || echo "  KV engine already enabled"

# Store database credentials
echo "Storing database credentials..."
curl -s -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "data": {
            "url": "jdbc:postgresql://postgres:5432/wallet_db",
            "username": "wallet",
            "password": "wallet123",
            "driver-class-name": "org.postgresql.Driver"
        }
    }' \
    "$VAULT_ADDR/v1/secret/data/wallet/database"
echo "  Database credentials stored"

# Store mail/SMTP credentials
echo "Storing mail credentials..."
curl -s -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "data": {
            "host": "mailhog",
            "port": "1025",
            "username": "",
            "password": "",
            "from": "noreply@wallet.local"
        }
    }' \
    "$VAULT_ADDR/v1/secret/data/wallet/mail"
echo "  Mail credentials stored"

# Store API keys (placeholder for future integrations)
echo "Storing API keys..."
curl -s -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "data": {
            "api-key-placeholder": "replace-with-actual-key"
        }
    }' \
    "$VAULT_ADDR/v1/secret/data/wallet/api-keys"
echo "  API keys stored"

# Store Kafka credentials (for future secured Kafka)
echo "Storing Kafka credentials..."
curl -s -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "data": {
            "bootstrap-servers": "kafka:9092",
            "security-protocol": "PLAINTEXT"
        }
    }' \
    "$VAULT_ADDR/v1/secret/data/wallet/kafka"
echo "  Kafka credentials stored"

# Store Redis credentials (for future secured Redis)
echo "Storing Redis credentials..."
curl -s -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "data": {
            "host": "redis",
            "port": "6379",
            "password": ""
        }
    }' \
    "$VAULT_ADDR/v1/secret/data/wallet/redis"
echo "  Redis credentials stored"

# Create policy
echo "Creating wallet-services policy..."
curl -s -X PUT \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "policy": "path \"secret/data/wallet/*\" { capabilities = [\"read\"] } path \"auth/token/renew-self\" { capabilities = [\"update\"] } path \"auth/token/lookup-self\" { capabilities = [\"read\"] }"
    }' \
    "$VAULT_ADDR/v1/sys/policies/acl/wallet-services"
echo "  Policy created"

# Create app role for services (for production use)
echo "Enabling AppRole auth method..."
curl -s -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -d '{"type": "approle"}' \
    "$VAULT_ADDR/v1/sys/auth/approle" 2>/dev/null || echo "  AppRole already enabled"

# Create role for wallet services
echo "Creating wallet-app role..."
curl -s -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
        "token_policies": ["wallet-services"],
        "token_ttl": "1h",
        "token_max_ttl": "4h"
    }' \
    "$VAULT_ADDR/v1/auth/approle/role/wallet-app"
echo "  Role created"

# Get role-id
echo "Getting role-id..."
ROLE_ID=$(curl -s \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    "$VAULT_ADDR/v1/auth/approle/role/wallet-app/role-id" | grep -o '"role_id":"[^"]*"' | cut -d'"' -f4)
echo "  Role ID: $ROLE_ID"

# Generate secret-id
echo "Generating secret-id..."
SECRET_ID=$(curl -s -X POST \
    -H "X-Vault-Token: $VAULT_TOKEN" \
    "$VAULT_ADDR/v1/auth/approle/role/wallet-app/secret-id" | grep -o '"secret_id":"[^"]*"' | cut -d'"' -f4)
echo "  Secret ID: $SECRET_ID"

echo ""
echo "============================================"
echo "Vault initialization complete!"
echo "============================================"
echo ""
echo "For development, use token authentication:"
echo "  VAULT_TOKEN=$VAULT_TOKEN"
echo ""
echo "For production, use AppRole authentication:"
echo "  VAULT_ROLE_ID=$ROLE_ID"
echo "  VAULT_SECRET_ID=$SECRET_ID"
echo ""
echo "Verify secrets with:"
echo "  curl -H \"X-Vault-Token: $VAULT_TOKEN\" $VAULT_ADDR/v1/secret/data/wallet/database"
echo ""
