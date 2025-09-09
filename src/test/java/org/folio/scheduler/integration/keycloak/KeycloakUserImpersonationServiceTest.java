package org.folio.scheduler.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.security.integration.keycloak.service.KeycloakStoreKeyProvider;
import org.folio.test.types.UnitTest;
import org.folio.tools.store.SecureStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.token.TokenService;
import org.keycloak.representations.AccessTokenResponse;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakUserImpersonationServiceTest {

  private static final String KEYCLOAK_USER_ID = "00000000-0000-0000-0000-000000000002";
  private static final String BASE_URL = "http://test-url";
  private static final String TOKEN = "token";
  private static final String IMPERSONATION_CLIENT = "impersonation-client";
  private static final String KEY = "folio_" + TENANT_ID + "_" + IMPERSONATION_CLIENT;

  @InjectMocks private KeycloakUserImpersonationService service;
  @Mock private Keycloak keycloak;
  @Mock private KeycloakUserService userService;
  @Mock(answer = Answers.RETURNS_DEEP_STUBS) private KeycloakProperties properties;
  @Mock private SecureStore secureStore;
  @Mock private TokenService tokenService;
  @Mock private KeycloakStoreKeyProvider keycloakStoreKeyProvider;

  @AfterEach
  void afterAll() {
    verifyNoMoreInteractions(keycloak, userService);
  }

  @Nested
  class Impersonate {

    @Test
    void positive() {
      var accessTokenResponse = new AccessTokenResponse();
      accessTokenResponse.setToken(TOKEN);

      when(properties.getBaseUrl()).thenReturn(BASE_URL);
      when(properties.getImpersonationClient()).thenReturn(IMPERSONATION_CLIENT);
      when(keycloakStoreKeyProvider.tenantStoreKey(TENANT_ID, IMPERSONATION_CLIENT)).thenReturn(KEY);
      when(secureStore.get(KEY)).thenReturn("clientSecret");
      when(keycloak.proxy(TokenService.class, URI.create(properties.getBaseUrl()))).thenReturn(tokenService);
      when(tokenService.grantToken(eq(TENANT_ID), any())).thenReturn(accessTokenResponse);
      when(userService.findKeycloakIdByTenantAndUserId(TENANT_ID, USER_ID)).thenReturn(KEYCLOAK_USER_ID);

      var token = service.impersonate(TENANT_ID, USER_ID);
      assertThat(token).isEqualTo(TOKEN);
    }
  }
}
