package org.folio.scheduler.integration.keycloak.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class KeycloakSecretUtilsTest {

  @Test
  void globalStoreKey_positive_returnsGlobalStoreKey() {
    var globalStoreKey = KeycloakSecretUtils.globalStoreKey("clientId");

    assertEquals("folio_master_clientId", globalStoreKey);
  }

  @Test
  void tenantStoreKey_positive_returnsTenantStoreKey() {
    var tenantStoreKey = KeycloakSecretUtils.tenantStoreKey("tenant", "clientId");

    assertEquals("folio_tenant_clientId", tenantStoreKey);
  }

  @Test
  void tenantStoreKey_tenantEmpty_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> KeycloakSecretUtils.tenantStoreKey("", "clientId"));
  }

  @Test
  void tenantStoreKey_clientIdEmpty_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class, () -> KeycloakSecretUtils.tenantStoreKey("tenant", null));
  }
}
