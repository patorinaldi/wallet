# Vault policy for wallet microservices
# This policy grants read access to all secrets needed by the wallet services

# Read access to database credentials
path "secret/data/wallet/database" {
  capabilities = ["read"]
}

# Read access to mail/SMTP credentials
path "secret/data/wallet/mail" {
  capabilities = ["read"]
}

# Read access to API keys (for future use)
path "secret/data/wallet/api-keys" {
  capabilities = ["read"]
}

# Read access to Kafka credentials (for future secured Kafka)
path "secret/data/wallet/kafka" {
  capabilities = ["read"]
}

# Read access to Redis credentials (for future secured Redis)
path "secret/data/wallet/redis" {
  capabilities = ["read"]
}

# Allow services to read their own service-specific secrets
path "secret/data/wallet/services/*" {
  capabilities = ["read"]
}

# Allow token renewal
path "auth/token/renew-self" {
  capabilities = ["update"]
}

# Allow token lookup
path "auth/token/lookup-self" {
  capabilities = ["read"]
}
