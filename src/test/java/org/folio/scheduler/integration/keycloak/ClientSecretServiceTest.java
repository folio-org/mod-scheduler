package org.folio.scheduler.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.security.integration.keycloak.service.SecureStoreKeyProvider;
import org.folio.test.types.UnitTest;
import org.folio.tools.store.SecureStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ClientSecretServiceTest {

  @InjectMocks private ClientSecretService clientSecretService;
  @Mock private SecureStore secureStore;
  @Mock private SecureStoreKeyProvider secureStoreKeyProvider;

  @Nested
  class RetrieveSecretFromSecretStore {

    @Test
    void positive() {
      var tenant = "testTenant";
      var clientId = "testClient";
      var expectedKey = "testKey";
      var expectedSecret = "testSecret";

      when(secureStoreKeyProvider.tenantStoreKey(tenant, clientId)).thenReturn(expectedKey);
      when(secureStore.get(expectedKey)).thenReturn(expectedSecret);

      var actualSecret = clientSecretService.retrieveSecretFromSecretStore(tenant, clientId);

      assertThat(expectedSecret).isEqualTo(actualSecret);
      verify(secureStoreKeyProvider).tenantStoreKey(tenant, clientId);
      verify(secureStore).get(expectedKey);
    }
  }
}
