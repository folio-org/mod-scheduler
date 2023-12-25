package org.folio.scheduler.integration.securestore.configuration;

import org.folio.scheduler.integration.securestore.SecureStore;
import org.folio.scheduler.integration.securestore.configuration.properties.AwsConfigProperties;
import org.folio.scheduler.integration.securestore.configuration.properties.EphemeralConfigProperties;
import org.folio.scheduler.integration.securestore.configuration.properties.VaultConfigProperties;
import org.folio.scheduler.integration.securestore.impl.AwsStore;
import org.folio.scheduler.integration.securestore.impl.EphemeralStore;
import org.folio.scheduler.integration.securestore.impl.VaultStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecureStoreConfiguration {

  @Bean
  @ConditionalOnProperty(name = "application.secret-store.type", havingValue = "EPHEMERAL", matchIfMissing = true)
  public SecureStore ephemeralStore(EphemeralConfigProperties properties) {
    return EphemeralStore.create(properties);
  }

  @Bean
  @ConditionalOnProperty(name = "application.secret-store.type", havingValue = "EPHEMERAL", matchIfMissing = true)
  @ConfigurationProperties(prefix = "application.secret-store.ephemeral")
  public EphemeralConfigProperties ephemeralProperties() {
    return new EphemeralConfigProperties();
  }

  @Bean
  @ConditionalOnProperty(name = "application.secret-store.type", havingValue = "AWS_SSM")
  public SecureStore awsStore(AwsConfigProperties properties) {
    return AwsStore.create(properties);
  }

  @Bean
  @ConditionalOnProperty(name = "application.secret-store.type", havingValue = "AWS_SSM")
  @ConfigurationProperties(prefix = "application.secret-store.aws-ssm")
  public AwsConfigProperties awsProperties() {
    return new AwsConfigProperties();
  }

  @Bean
  @ConditionalOnProperty(name = "application.secret-store.type", havingValue = "VAULT")
  public SecureStore vaultStore(VaultConfigProperties properties) {
    return VaultStore.create(properties);
  }

  @Bean
  @ConditionalOnProperty(name = "application.secret-store.type", havingValue = "VAULT")
  @ConfigurationProperties(prefix = "application.secret-store.vault")
  public VaultConfigProperties vaultProperties() {
    return new VaultConfigProperties();
  }
}
