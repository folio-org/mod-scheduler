package org.folio.scheduler.integration.keycloak;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.support.TestConstants.USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import com.github.benmanes.caffeine.cache.Cache;
import java.net.URI;
import java.util.Map;
import org.folio.scheduler.configuration.RetryConfiguration;
import org.folio.scheduler.configuration.properties.RetryConfigurationProperties;
import org.folio.scheduler.configuration.properties.RetryConfigurationProperties.RetryProperties;
import org.folio.scheduler.integration.keycloak.KeycloakUserImpersonationServiceTest.TestContextConfiguration;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.scheduler.service.UserImpersonationService;
import org.folio.spring.exception.NotFoundException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.token.TokenService;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@UnitTest
@SpringBootTest(
  classes = {KeycloakUserImpersonationService.class, TestContextConfiguration.class},
  webEnvironment = NONE)
class KeycloakUserImpersonationServiceTest {

  private static final String KEYCLOAK_USER_ID = "00000000-0000-0000-0000-000000000002";
  private static final String BASE_URL = "http://test-url";
  private static final String TOKEN = "token";
  private static final String IMPERSONATION_CLIENT = "impersonation-client";

  @Autowired private UserImpersonationService service;
  @MockitoBean private Keycloak keycloak;
  @MockitoBean private KeycloakUserService userService;
  @MockitoBean private KeycloakProperties properties;
  @MockitoBean private TokenService tokenService;
  @MockitoBean private ClientSecretService clientSecretService;
  @MockitoBean private Cache<String, AccessTokenResponse> tokenCache;

  private void mockTokenRequest() {
    when(properties.getBaseUrl()).thenReturn(BASE_URL);
    when(properties.getImpersonationClient()).thenReturn(IMPERSONATION_CLIENT);
    when(clientSecretService.retrieveSecretFromSecretStore(TENANT_ID, IMPERSONATION_CLIENT))
      .thenReturn("clientSecret");
    when(keycloak.proxy(TokenService.class, URI.create(BASE_URL))).thenReturn(tokenService);
    when(userService.findKeycloakIdByTenantAndUserId(TENANT_ID, USER_ID)).thenReturn(KEYCLOAK_USER_ID);
  }

  private static AccessTokenResponse tokenResponse(String token) {
    var accessTokenResponse = new AccessTokenResponse();
    accessTokenResponse.setToken(token);
    return accessTokenResponse;
  }

  private static String cacheKey() {
    return TENANT_ID + ":" + USER_ID;
  }

  private static String invalidTokenMessage() {
    return "Failed to obtain user impersonation token: token is blank [tenant: test, userId: " + USER_ID + "]";
  }

  @Nested
  class Impersonate {

    @Test
    void impersonate_positive_tokenIsCached() {
      var accessTokenResponse = tokenResponse(TOKEN);
      mockTokenRequest();
      when(tokenService.grantToken(eq(TENANT_ID), any())).thenReturn(accessTokenResponse);

      var token = service.impersonate(TENANT_ID, USER_ID);

      assertThat(token).isEqualTo(TOKEN);
      verify(tokenCache).getIfPresent(cacheKey());
      verify(tokenCache).put(cacheKey(), accessTokenResponse);
    }

    @Test
    void impersonate_positive_retryIsUsedForBlankTokenResponse() {
      var accessTokenResponse = tokenResponse(TOKEN);
      mockTokenRequest();
      when(tokenService.grantToken(eq(TENANT_ID), any()))
        .thenReturn(tokenResponse(null))
        .thenReturn(accessTokenResponse);

      var token = service.impersonate(TENANT_ID, USER_ID);

      assertThat(token).isEqualTo(TOKEN);
      verify(tokenCache, times(2)).getIfPresent(cacheKey());
      verify(tokenCache).put(cacheKey(), accessTokenResponse);
      verify(tokenService, times(2)).grantToken(eq(TENANT_ID), any());
    }

    @Test
    void impersonate_negative_emptyTokenResponseIsNotCached() {
      mockTokenRequest();
      when(tokenService.grantToken(eq(TENANT_ID), any())).thenReturn(tokenResponse(""));

      assertThatThrownBy(() -> service.impersonate(TENANT_ID, USER_ID))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(invalidTokenMessage());

      verify(tokenCache, times(3)).getIfPresent(cacheKey());
      verify(tokenCache, never()).put(any(), any());
      verify(tokenService, times(3)).grantToken(eq(TENANT_ID), any());
    }

    @Test
    void impersonate_positive_cachedInvalidTokenIsEvictedAndRetried() {
      var accessTokenResponse = tokenResponse(TOKEN);
      when(tokenCache.getIfPresent(cacheKey()))
        .thenReturn(tokenResponse("null"))
        .thenReturn(null);
      mockTokenRequest();
      when(tokenService.grantToken(eq(TENANT_ID), any())).thenReturn(accessTokenResponse);

      var token = service.impersonate(TENANT_ID, USER_ID);

      assertThat(token).isEqualTo(TOKEN);
      verify(tokenCache, times(2)).getIfPresent(cacheKey());
      verify(tokenCache).invalidate(cacheKey());
      verify(tokenCache).put(cacheKey(), accessTokenResponse);
      verify(tokenService).grantToken(eq(TENANT_ID), any());
    }

    @Test
    void impersonate_negative_userNotFoundIsNotRetried() {
      when(properties.getBaseUrl()).thenReturn(BASE_URL);
      when(properties.getImpersonationClient()).thenReturn(IMPERSONATION_CLIENT);
      when(clientSecretService.retrieveSecretFromSecretStore(TENANT_ID, IMPERSONATION_CLIENT))
        .thenReturn("clientSecret");
      when(keycloak.proxy(TokenService.class, URI.create(BASE_URL))).thenReturn(tokenService);
      when(userService.findKeycloakIdByTenantAndUserId(TENANT_ID, USER_ID))
        .thenThrow(new NotFoundException("missing user"));

      assertThatThrownBy(() -> service.impersonate(TENANT_ID, USER_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("missing user");

      verify(tokenCache).getIfPresent(cacheKey());
      verify(userService).findKeycloakIdByTenantAndUserId(TENANT_ID, USER_ID);
      verifyNoInteractions(tokenService);
    }
  }

  @EnableRetry
  @TestConfiguration
  @Import(RetryConfiguration.class)
  static class TestContextConfiguration {

    @Bean
    RetryConfigurationProperties retryConfigurationProperties() {
      var configuration = new RetryConfigurationProperties();
      configuration.setConfig(Map.of("user-impersonation",
        RetryProperties.of(ofMillis(10), ofMillis(100), 3, 1.5)));
      return configuration;
    }
  }
}
