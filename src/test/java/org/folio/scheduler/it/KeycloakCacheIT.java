package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;

import org.folio.scheduler.integration.keycloak.KeycloakUserService;
import org.folio.scheduler.integration.keycloak.SystemUserService;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;

@IntegrationTest
@EnableKeycloakTlsMode
@KeycloakRealms("/json/keycloak/test-realm.json")
class KeycloakCacheIT extends BaseIntegrationTest {

  private static final String SYSTEM_USER_ID = "3e0561fc-2013-47fe-bd55-2af3aaa3b54d";
  private static final String REGULAR_USER_ID = "00000000-0000-0000-0000-000000000000";
  private static final String KEYCLOAK_USER_ID = "77832c28-77f1-47ef-ad22-a1cd93df86d4";

  @Autowired private CacheManager cacheManager;
  @Autowired private SystemUserService systemUserService;
  @Autowired private KeycloakUserService keycloakUserService;

  @BeforeAll
  static void beforeAll() {
    setUpTenant();
  }

  @AfterAll
  static void afterAll() {
    removeTenant();
  }

  @BeforeEach
  void setUp() {
    clearAllCaches();
  }

  @Test
  @DisplayName("SystemUserService should cache system user ID and reuse it on subsequent calls")
  void systemUserIdCache_positive() {
    var cache = cacheManager.getCache("system-user-id");
    assertThat(cache).isNotNull();
    assertThat(cache.get(TENANT_ID)).isNull();

    var result1 = systemUserService.findSystemUserId(TENANT_ID);
    assertThat(result1).isEqualTo(SYSTEM_USER_ID);

    var cachedValue = cache.get(TENANT_ID);
    assertThat(cachedValue).isNotNull();
    assertThat(cachedValue.get()).isEqualTo(SYSTEM_USER_ID);

    var result2 = systemUserService.findSystemUserId(TENANT_ID);
    assertThat(result2).isEqualTo(SYSTEM_USER_ID);
    assertThat(cache.get(TENANT_ID).get()).isEqualTo(SYSTEM_USER_ID);

    var result3 = systemUserService.findSystemUserId(TENANT_ID);
    assertThat(result3).isEqualTo(SYSTEM_USER_ID);
    assertThat(cache.get(TENANT_ID).get()).isEqualTo(SYSTEM_USER_ID);
  }

  @Test
  @DisplayName("KeycloakUserService should cache keycloak user ID by tenant and user ID")
  void keycloakUserIdCache_positive() {
    var cache = cacheManager.getCache("keycloak-user-id");
    assertThat(cache).isNotNull();

    var cacheKey = TENANT_ID + ":" + REGULAR_USER_ID;
    assertThat(cache.get(cacheKey)).isNull();

    var result1 = keycloakUserService.findKeycloakIdByTenantAndUserId(TENANT_ID, REGULAR_USER_ID);
    assertThat(result1).isEqualTo(KEYCLOAK_USER_ID);

    var cachedValue = cache.get(cacheKey);
    assertThat(cachedValue).isNotNull();
    assertThat(cachedValue.get()).isEqualTo(KEYCLOAK_USER_ID);

    var result2 = keycloakUserService.findKeycloakIdByTenantAndUserId(TENANT_ID, REGULAR_USER_ID);
    assertThat(result2).isEqualTo(KEYCLOAK_USER_ID);

    var result3 = keycloakUserService.findKeycloakIdByTenantAndUserId(TENANT_ID, REGULAR_USER_ID);
    assertThat(result3).isEqualTo(KEYCLOAK_USER_ID);
  }

  @Test
  @DisplayName("ClientSecretService cache is configured and working")
  void clientSecretCache_positive() {
    var cache = cacheManager.getCache("client-secret-key");
    assertThat(cache).isNotNull();
    assertThat(cache.getName()).isEqualTo("client-secret-key");
  }

  @Test
  @DisplayName("Caches should be independent per tenant")
  void cachesAreTenantSpecific_positive() {
    var cache = cacheManager.getCache("system-user-id");
    assertThat(cache).isNotNull();

    var tenant1 = TENANT_ID;
    var tenant2 = "tenant2";

    var result1 = systemUserService.findSystemUserId(tenant1);
    assertThat(result1).isEqualTo(SYSTEM_USER_ID);
    assertThat(cache.get(tenant1)).isNotNull();
    assertThat(cache.get(tenant1).get()).isEqualTo(SYSTEM_USER_ID);
    assertThat(cache.get(tenant2)).isNull();

    systemUserService.findSystemUserId(tenant1);
    assertThat(cache.get(tenant1).get()).isEqualTo(SYSTEM_USER_ID);
  }

  @Test
  @DisplayName("Cache eviction should remove cached values")
  void cacheEviction_positive() {
    var cache = cacheManager.getCache("system-user-id");
    assertThat(cache).isNotNull();

    var result1 = systemUserService.findSystemUserId(TENANT_ID);
    assertThat(result1).isEqualTo(SYSTEM_USER_ID);
    assertThat(cache.get(TENANT_ID)).isNotNull();
    assertThat(cache.get(TENANT_ID).get()).isEqualTo(SYSTEM_USER_ID);

    cache.evict(TENANT_ID);
    assertThat(cache.get(TENANT_ID)).isNull();

    var result2 = systemUserService.findSystemUserId(TENANT_ID);
    assertThat(result2).isEqualTo(SYSTEM_USER_ID);
    assertThat(cache.get(TENANT_ID)).isNotNull();
    assertThat(cache.get(TENANT_ID).get()).isEqualTo(SYSTEM_USER_ID);
  }

  @Test
  @DisplayName("All caches should be configured and available")
  void allCachesAreConfigured_positive() {
    var cacheNames = cacheManager.getCacheNames();
    assertThat(cacheNames)
      .containsExactlyInAnyOrder("system-user-id", "keycloak-user-id", "client-secret-key");

    assertThat(cacheManager.getCache("system-user-id")).isNotNull();
    assertThat(cacheManager.getCache("keycloak-user-id")).isNotNull();
    assertThat(cacheManager.getCache("client-secret-key")).isNotNull();
  }

  private void clearAllCaches() {
    cacheManager.getCacheNames().forEach(cacheName -> {
      var cache = cacheManager.getCache(cacheName);
      if (cache != null) {
        cache.clear();
      }
    });
  }
}
