package org.folio.scheduler.integration.keycloak;

import static java.net.URI.create;
import static java.util.Collections.singletonList;
import static org.keycloak.OAuth2Constants.TOKEN_EXCHANGE_GRANT_TYPE;

import com.github.benmanes.caffeine.cache.Cache;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.scheduler.service.UserImpersonationService;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.token.TokenService;
import org.keycloak.representations.AccessTokenResponse;

@Log4j2
@RequiredArgsConstructor
public class KeycloakUserImpersonationService implements UserImpersonationService {

  private final Keycloak keycloak;
  private final KeycloakUserService userService;
  private final KeycloakProperties properties;
  private final ClientSecretService clientSecretService;
  private final Cache<String, AccessTokenResponse> tokenCache;

  @Override
  public String impersonate(String tenant, String userId) {
    var key = buildCacheKey(tenant, userId);
    var accessTokenResponse = tokenCache.getIfPresent(key);
    if (accessTokenResponse == null) {
      accessTokenResponse = getUserToken(tenant, userId);
      tokenCache.put(key, accessTokenResponse);
      log.debug("Update cache with user token : tenant = {}, userId = {}", tenant, userId);
    }
    return accessTokenResponse.getToken();
  }

  private AccessTokenResponse getUserToken(String tenant, String userId) {
    log.debug("Get user token from keycloak: tenant = {}, userId = {}", tenant, userId);
    var keycloakProxy = keycloak.proxy(TokenService.class, create(properties.getBaseUrl()));
    var data = prepareRequestData(tenant, userId);
    return keycloakProxy.grantToken(tenant, data);
  }

  private MultivaluedMap<String, String> prepareRequestData(String tenant, String userId) {
    MultivaluedMap<String, String> data = new MultivaluedHashMap<>();
    var impersonationClient = properties.getImpersonationClient();
    data.put("client_id", singletonList(impersonationClient));
    data.put("client_secret", singletonList(clientSecretService
      .retrieveSecretFromSecretStore(tenant, impersonationClient)));
    data.put("requested_subject", singletonList(userService.findKeycloakIdByTenantAndUserId(tenant, userId)));
    data.put("grant_type", singletonList(TOKEN_EXCHANGE_GRANT_TYPE));
    return data;
  }

  private static String buildCacheKey(String tenant, String userId) {
    return tenant + ":" + userId;
  }
}
