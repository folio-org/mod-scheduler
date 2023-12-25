package org.folio.scheduler.integration.keycloak;

import static java.time.Duration.ofMillis;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.scheduler.utils.TestUtils.cleanUpCaches;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.folio.scheduler.configuration.RetryConfiguration;
import org.folio.scheduler.configuration.properties.RetryConfigurationProperties;
import org.folio.scheduler.configuration.properties.RetryConfigurationProperties.RetryProperties;
import org.folio.scheduler.configuration.properties.SystemUserConfigurationProperties;
import org.folio.scheduler.integration.keycloak.SystemUserServiceTest.TestContextConfiguration;
import org.folio.spring.exception.NotFoundException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;

@UnitTest
@SpringBootTest(classes = {SystemUserService.class, TestContextConfiguration.class}, webEnvironment = NONE)
class SystemUserServiceTest {

  private static final String SYSTEM_USER_ID_CACHE = "system-user-id";
  private static final String SYSTEM_USER_ID = UUID.randomUUID().toString();

  @Autowired private CacheManager cacheManager;
  @Autowired private SystemUserService systemUserService;
  @MockBean private KeycloakUserService keycloakUserService;

  @BeforeEach
  void setUp() {
    cleanUpCaches(cacheManager);
  }

  private Optional<Object> getCachedValue() {
    return ofNullable(cacheManager.getCache(SYSTEM_USER_ID_CACHE))
      .map(cache -> cache.get(TENANT_ID))
      .map(ValueWrapper::get);
  }

  @Nested
  @DisplayName("findSystemUserId")
  class FindSystemUserId {

    @Test
    void positive() {
      var username = TENANT_ID + "-system-user";
      when(keycloakUserService.findUserIdByKeycloakUsername(TENANT_ID, username)).thenReturn(SYSTEM_USER_ID);

      var result = systemUserService.findSystemUserId(TENANT_ID);

      assertThat(result).isEqualTo(SYSTEM_USER_ID);
      assertThat(getCachedValue()).isPresent().get().isEqualTo(SYSTEM_USER_ID);
    }

    @Test
    void positive_retryIsUsed() {
      var username = TENANT_ID + "-system-user";
      when(keycloakUserService.findUserIdByKeycloakUsername(TENANT_ID, username))
        .thenThrow(new NotFoundException("Keycloak user doesn't exist with the given username: " + username))
        .thenReturn(SYSTEM_USER_ID);

      var result = systemUserService.findSystemUserId(TENANT_ID);

      assertThat(result).isEqualTo(SYSTEM_USER_ID);
      assertThat(getCachedValue()).isPresent().get().isEqualTo(SYSTEM_USER_ID);
      verify(keycloakUserService, times(2)).findUserIdByKeycloakUsername(TENANT_ID, username);
    }

    @Test
    void negative_retryIsFailed() {
      var username = TENANT_ID + "-system-user";
      when(keycloakUserService.findUserIdByKeycloakUsername(TENANT_ID, username))
        .thenThrow(new NotFoundException("Keycloak user doesn't exist with the given username: " + username));

      assertThatThrownBy(() -> systemUserService.findSystemUserId(TENANT_ID))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Keycloak user doesn't exist with the given username: " + username);

      assertThat(getCachedValue()).isEmpty();
      verify(keycloakUserService, times(3)).findUserIdByKeycloakUsername(TENANT_ID, username);
    }
  }

  @EnableRetry
  @EnableCaching
  @TestConfiguration
  @Import(RetryConfiguration.class)
  static class TestContextConfiguration {

    @Bean
    SystemUserConfigurationProperties systemUserConfigurationProperties() {
      var systemUserConfigurationProperties = new SystemUserConfigurationProperties();
      systemUserConfigurationProperties.setUsernameTemplate("{tenantId}-system-user");
      return systemUserConfigurationProperties;
    }

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(SYSTEM_USER_ID_CACHE);
    }

    @Bean
    public RetryConfigurationProperties retryConfigurationProperties() {
      var configuration = new RetryConfigurationProperties();
      configuration.setConfig(Map.of("system-user", RetryProperties.of(ofMillis(10), ofMillis(100), 3, 1.5)));
      return configuration;
    }
  }
}
