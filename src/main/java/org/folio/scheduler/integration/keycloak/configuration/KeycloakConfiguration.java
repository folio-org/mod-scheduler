package org.folio.scheduler.integration.keycloak.configuration;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.folio.scheduler.integration.keycloak.utils.KeycloakSecretUtils.globalStoreKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.integration.keycloak.KeycloakUserImpersonationService;
import org.folio.scheduler.integration.keycloak.KeycloakUserService;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.scheduler.integration.securestore.SecureStore;
import org.folio.scheduler.integration.securestore.exception.NotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@ConditionalOnProperty(name = "application.keycloak.enabled", havingValue = "true")
@EnableConfigurationProperties(KeycloakProperties.class)
@RequiredArgsConstructor
public class KeycloakConfiguration {

  private static final String ADMIN_REALM = "master";
  private static final String ADMIN_CLINT_GRANT_TYPE = "client_credentials";

  private final KeycloakProperties properties;
  private final SecureStore secureStore;

  @Bean
  public Keycloak keycloak() {
    var admin = properties.getAdmin();
    var clientId = admin.getClientId();
    var secret = findSecret(clientId);
    return KeycloakBuilder.builder()
      .realm(ADMIN_REALM)
      .serverUrl(properties.getBaseUrl())
      .clientId(clientId)
      .clientSecret(stripToNull(secret))
      .grantType(ADMIN_CLINT_GRANT_TYPE)
      .build();
  }

  @Bean
  public KeycloakUserService keycloakUserService(Keycloak keycloak) {
    return new KeycloakUserService(keycloak);
  }

  @Bean
  public KeycloakUserImpersonationService keycloakUserImpersonationService(Keycloak keycloak,
    KeycloakUserService userService) {
    return new KeycloakUserImpersonationService(keycloak, userService, properties, secureStore);
  }

  private String findSecret(String clientId) {
    return secureStore.lookup(globalStoreKey(clientId)).orElseThrow(() -> {
      log.debug("Secret for 'admin' client is not defined in the secret store: clientId = {}", clientId);
      return new NotFoundException(
        format("Secret for 'admin' client is not defined in the secret store: clientId = %s, secretStore = %s",
          clientId, secureStore.getClass().getSimpleName()));
    });
  }
}
