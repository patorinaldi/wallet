package com.patorinaldi.wallet.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for HashiCorp Vault integration.
 *
 * <p>This configuration is enabled when {@code vault.enabled=true} is set.
 * Spring Cloud Vault will automatically:
 * <ul>
 *   <li>Connect to Vault using the configured authentication method</li>
 *   <li>Fetch secrets from configured paths</li>
 *   <li>Inject secrets as Spring properties</li>
 *   <li>Refresh secrets on token renewal</li>
 * </ul>
 *
 * <p>Configuration properties (in bootstrap.yml):
 * <pre>
 * spring:
 *   cloud:
 *     vault:
 *       enabled: true
 *       host: localhost
 *       port: 8200
 *       scheme: http
 *       authentication: TOKEN  # or APPROLE for production
 *       token: dev-root-token  # for development only
 *       kv:
 *         enabled: true
 *         backend: secret
 *         application-name: wallet
 * </pre>
 *
 * <p>Secret paths:
 * <ul>
 *   <li>{@code secret/data/wallet/database} - Database credentials</li>
 *   <li>{@code secret/data/wallet/mail} - SMTP credentials</li>
 *   <li>{@code secret/data/wallet/api-keys} - External API keys</li>
 * </ul>
 *
 * @see <a href="https://docs.spring.io/spring-cloud-vault/docs/current/reference/html/">Spring Cloud Vault Reference</a>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "vault.enabled", havingValue = "true", matchIfMissing = false)
public class VaultConfig {

    public VaultConfig() {
        log.info("Vault integration enabled - secrets will be fetched from Vault");
    }
}
