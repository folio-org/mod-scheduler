package org.folio.scheduler.integration.keycloak.configuration;

import static jakarta.ws.rs.client.ClientBuilder.newBuilder;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.stripToNull;
import static org.folio.common.utils.tls.FeignClientTlsUtils.buildSslContext;
import static org.folio.common.utils.tls.Utils.IS_HOSTNAME_VERIFICATION_DISABLED;
import static org.folio.scheduler.integration.keycloak.utils.KeycloakSecretUtils.globalStoreKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.folio.common.configuration.properties.TlsProperties;
import org.folio.scheduler.integration.keycloak.KeycloakUserImpersonationService;
import org.folio.scheduler.integration.keycloak.KeycloakUserService;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.scheduler.integration.securestore.SecureStore;
import org.folio.scheduler.integration.securestore.exception.NotFoundException;
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
  public Keycloak keycloak() {
    var admin = properties.getAdmin();
    var clientId = admin.getClientId();
    var secret = findSecret(clientId);
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

  private static ResteasyClient buildResteasyClient(TlsProperties tls) {
    return (ResteasyClient) newBuilder()
      .sslContext(buildSslContext(tls))
      .hostnameVerifier(IS_HOSTNAME_VERIFICATION_DISABLED ? NoopHostnameVerifier.INSTANCE : DEFAULT_HOSTNAME_VERIFIER)
      .build();
  }
}
