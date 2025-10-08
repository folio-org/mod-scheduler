package org.folio.scheduler.integration.keycloak;

import lombok.RequiredArgsConstructor;
import org.folio.security.integration.keycloak.service.SecureStoreKeyProvider;
import org.folio.tools.store.SecureStore;
import org.springframework.cache.annotation.Cacheable;

@RequiredArgsConstructor
public class ClientSecretService {
  private final SecureStore secureStore;
  private final SecureStoreKeyProvider secureStoreKeyProvider;

  @Cacheable(cacheNames = "client-secret-key", key = "#tenant + ':' + #clientId")
  public String retrieveSecretFromSecretStore(String tenant, String clientId) {
    var key = secureStoreKeyProvider.tenantStoreKey(tenant, clientId);
    return secureStore.get(key);
  }
}
