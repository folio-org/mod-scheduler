package org.folio.scheduler.integration.keycloak;

import static java.net.URI.create;
import static java.util.Collections.singletonList;
import static org.keycloak.OAuth2Constants.TOKEN_EXCHANGE_GRANT_TYPE;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import lombok.RequiredArgsConstructor;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.scheduler.service.UserImpersonationService;
import org.folio.security.integration.keycloak.service.SecureStoreKeyProvider;
import org.folio.tools.store.SecureStore;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.token.TokenService;

@RequiredArgsConstructor
public class KeycloakUserImpersonationService implements UserImpersonationService {

  private final Keycloak keycloak;
  private final KeycloakUserService userService;
  private final KeycloakProperties properties;
  private final SecureStore secureStore;
  private final SecureStoreKeyProvider secureStoreKeyProvider;

  @Override
  public String impersonate(String tenant, String userId) {
    var keycloakProxy = keycloak.proxy(TokenService.class, create(properties.getBaseUrl()));
    var data = prepareRequestData(tenant, userId);
    var accessTokenResponse = keycloakProxy.grantToken(tenant, data);
    return accessTokenResponse.getToken();
  }

  private MultivaluedMap<String, String> prepareRequestData(String tenant, String userId) {
    MultivaluedMap<String, String> data = new MultivaluedHashMap<>();
    var impersonationClient = properties.getImpersonationClient();
    data.put("client_id", singletonList(impersonationClient));
    data.put("client_secret", singletonList(retrieveSecretFromSecretStore(tenant, impersonationClient)));
    data.put("requested_subject", singletonList(userService.findKeycloakIdByTenantAndUserId(tenant, userId)));
    data.put("grant_type", singletonList(TOKEN_EXCHANGE_GRANT_TYPE));
    return data;
  }

  private String retrieveSecretFromSecretStore(String tenant, String clientId) {
    var key = secureStoreKeyProvider.tenantStoreKey(tenant, clientId);
    return secureStore.get(key);
  }
}
