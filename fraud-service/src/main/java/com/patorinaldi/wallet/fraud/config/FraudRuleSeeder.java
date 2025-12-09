package com.patorinaldi.wallet.fraud.config;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.patorinaldi.wallet.fraud.entity.FraudRule;
import com.patorinaldi.wallet.fraud.entity.RuleType;
import com.patorinaldi.wallet.fraud.repository.FraudRuleRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FraudRuleSeeder implements CommandLineRunner {

    private final FraudRuleRepository fraudRuleRepository;
    
    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Checking fraud rules...");
        if (fraudRuleRepository.count() == 0) {
            // Seed default fraud rules

            seedRule("LARGE_AMOUNT", RuleType.AMOUNT_THRESHOLD,
                    "Flag transactions over $10,000", 
                    BigDecimal.valueOf(10000), 30, null);

            seedRule("VERY_LARGE_AMOUNT", RuleType.AMOUNT_THRESHOLD,
                    "Flag transactions over $50,000", 
                    BigDecimal.valueOf(50000), 50, null);
                
            seedRule("HIGH_VELOCITY", RuleType.VELOCITY,
                    "Flag more than 10 transactions in 60 minutes", 
                    BigDecimal.valueOf(10), 25, 60);

            seedRule("EXTREME_VELOCITY", RuleType.VELOCITY,
                    "Flag more than 20 transactions in 60 minutes",
                    BigDecimal.valueOf(20), 40, 60);

            seedRule("NEW_WALLET", RuleType.NEW_ACCOUNT,
                    "Flag transactions from wallets created within the last 24 hours",
                    null, 15, 1440);

            seedRule("UNUSUAL_AMOUNT", RuleType.UNUSUAL_PATTERN,
                    "Amount > 3x wallet average", 
                    BigDecimal.valueOf(3), 20, null);

            log.info("Default fraud rules created");
            
        } else {
            log.debug("Fraud rules already exist");
        }
        log.info("Fraud rules verified successfully");
    }

    private void seedRule(String code, RuleType type, String desc,
                        BigDecimal threshold, int score, Integer timeWindow) {
      if (fraudRuleRepository.existsByRuleCode(code)) {
          log.debug("Rule {} already exists, skipping", code);
          return;
      }

      FraudRule rule = FraudRule.builder()
          .ruleCode(code)
          .ruleType(type)
          .description(desc)
          .threshold(threshold)
          .scoreImpact(score)
          .timeWindowMinutes(timeWindow)
          .active(true)
          .createdAt(Instant.now())
          .build();

      fraudRuleRepository.save(rule);
      log.info("Created fraud rule: {}", code);
  }
    
}
