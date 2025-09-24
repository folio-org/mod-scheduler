package org.folio.scheduler.integration.keycloak.configuration;

import static jakarta.ws.rs.client.ClientBuilder.newBuilder;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.folio.common.utils.tls.FeignClientTlsUtils.buildSslContext;
import static org.folio.common.utils.tls.Utils.IS_HOSTNAME_VERIFICATION_DISABLED;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.folio.common.configuration.properties.TlsProperties;
import org.folio.scheduler.integration.keycloak.KeycloakUserImpersonationService;
import org.folio.scheduler.integration.keycloak.KeycloakUserService;
import org.folio.scheduler.integration.keycloak.configuration.exception.NotFoundException;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.security.integration.keycloak.service.SecureStoreKeyProvider;
import org.folio.tools.store.SecureStore;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
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

  private static final DefaultHostnameVerifier DEFAULT_HOSTNAME_VERIFIER = new DefaultHostnameVerifier();

  private static final String ADMIN_REALM = "master";
  private static final String ADMIN_CLINT_GRANT_TYPE = "client_credentials";

  private final KeycloakProperties properties;
  private final SecureStore secureStore;

  @Bean
  public Keycloak keycloak(SecureStoreKeyProvider secureStoreKeyProvider) {
    var admin = properties.getAdmin();
    var clientId = admin.getClientId();
    var globalStoreKey = secureStoreKeyProvider.globalStoreKey(clientId);
    var secret = findSecret(globalStoreKey, clientId);
    var builder = KeycloakBuilder.builder()
      .realm(ADMIN_REALM)
      .serverUrl(properties.getBaseUrl())
      .clientId(clientId)
      .clientSecret(stripToNull(secret))
      .grantType(ADMIN_CLINT_GRANT_TYPE);

    if (properties.getTls() != null && properties.getTls().isEnabled()) {
      builder.resteasyClient(buildResteasyClient(properties.getTls()));
    }

    return builder.build();
  }

  @Bean
  public KeycloakUserService keycloakUserService(Keycloak keycloak) {
    return new KeycloakUserService(keycloak);
  }

  @Bean
  public KeycloakUserImpersonationService keycloakUserImpersonationService(Keycloak keycloak,
    KeycloakUserService userService, SecureStoreKeyProvider secureStoreKeyProvider) {
    return new KeycloakUserImpersonationService(keycloak, userService, properties, secureStore, secureStoreKeyProvider);
  }

  private String findSecret(String globalStoreKey, String clientId) {
    return secureStore.lookup(globalStoreKey).orElseThrow(() -> {
      log.debug("Secret for 'admin' client is not defined in the secret store: clientId = {}", clientId);
      return new NotFoundException(
        format("Secret for 'admin' client is not defined in the secret store: clientId = %s, secretStore = %s",
          clientId, secureStore.getClass().getSimpleName()));
    });
  }

  private static ResteasyClient buildResteasyClient(TlsProperties tls) {
    return (ResteasyClient) newBuilder()
      .sslContext(buildSslContext(tls))
      .hostnameVerifier(IS_HOSTNAME_VERIFICATION_DISABLED ? NoopHostnameVerifier.INSTANCE : DEFAULT_HOSTNAME_VERIFIER)
      .build();
  }
}
