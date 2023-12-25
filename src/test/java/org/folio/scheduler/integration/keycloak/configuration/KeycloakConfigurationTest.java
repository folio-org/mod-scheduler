package org.folio.scheduler.integration.keycloak.configuration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakAdminProperties;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.scheduler.integration.securestore.SecureStore;
import org.folio.scheduler.integration.securestore.exception.NotFoundException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakConfigurationTest {

  @InjectMocks private KeycloakConfiguration keycloakConfiguration;

  @Mock private KeycloakProperties keycloakProperties;
  @Mock private KeycloakAdminProperties keycloakAdminProperties;
  @Mock private SecureStore secureStore;

  @Test
  void keycloak_negative_secretNotFound_throwsIllegalStateException() {
    when(secureStore.lookup(anyString())).thenReturn(Optional.empty());
    when(keycloakProperties.getAdmin()).thenReturn(keycloakAdminProperties);
    when(keycloakAdminProperties.getClientId()).thenReturn("clientId");

    assertThrows(NotFoundException.class, () -> keycloakConfiguration.keycloak());
  }
}
