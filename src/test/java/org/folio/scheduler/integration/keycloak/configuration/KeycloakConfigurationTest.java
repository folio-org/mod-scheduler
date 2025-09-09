package org.folio.scheduler.integration.keycloak.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.common.configuration.properties.TlsProperties;
import org.folio.common.utils.exception.SslInitializationException;
import org.folio.scheduler.integration.keycloak.configuration.exception.NotFoundException;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakAdminProperties;
import org.folio.scheduler.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.security.integration.keycloak.service.KeycloakStoreKeyProvider;
import org.folio.test.types.UnitTest;
import org.folio.tools.store.SecureStore;
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
  @Mock private TlsProperties tlsProperties;
  @Mock private KeycloakAdminProperties keycloakAdminProperties;
  @Mock private SecureStore secureStore;
  @Mock private KeycloakStoreKeyProvider keycloakStoreKeyProvider;

  @Test
  void keycloak_negative_secretNotFound_throwsIllegalStateException() {
    when(secureStore.lookup(anyString())).thenReturn(Optional.empty());
    when(keycloakProperties.getAdmin()).thenReturn(keycloakAdminProperties);
    when(keycloakAdminProperties.getClientId()).thenReturn("clientId");
    when(keycloakStoreKeyProvider.globalStoreKey("clientId")).thenReturn("anyKey");

    assertThrows(NotFoundException.class, () -> keycloakConfiguration.keycloak(keycloakStoreKeyProvider));
  }

  @Test
  void keycloak_positive_tlsDisabled() {
    mockProperties();
    when(keycloakProperties.getTls()).thenReturn(tlsProperties);
    when(tlsProperties.isEnabled()).thenReturn(false);
    var keycloakAdminClient = keycloakConfiguration.keycloak(keycloakStoreKeyProvider);

    assertThat(keycloakAdminClient).isNotNull();
  }

  @Test
  void keycloak_positive_tlsEnabled() {
    mockProperties();
    when(keycloakProperties.getTls()).thenReturn(tlsProperties);
    mockKeycloakTlsProperties();
    var keycloakAdminClient = keycloakConfiguration.keycloak(keycloakStoreKeyProvider);

    assertThat(keycloakAdminClient).isNotNull();
  }

  @Test
  void keycloak_positive_tlsPropertiesIsNull() {
    mockProperties();
    when(keycloakProperties.getTls()).thenReturn(null);
    var keycloakAdminClient = keycloakConfiguration.keycloak(keycloakStoreKeyProvider);

    assertThat(keycloakAdminClient).isNotNull();
  }

  @Test
  void keycloak_negative_trustStoreNotFound() {
    mockProperties();
    when(keycloakProperties.getTls()).thenReturn(tlsProperties);
    mockWrongTrustStoreKeycloakTlsProperties();

    assertThrows(SslInitializationException.class, () -> keycloakConfiguration.keycloak(keycloakStoreKeyProvider));
  }

  private void mockKeycloakTlsProperties() {
    when(tlsProperties.getTrustStorePath()).thenReturn("classpath:certificates/test.truststore.jks");
    when(tlsProperties.getTrustStorePassword()).thenReturn("secretpassword");
    when(tlsProperties.isEnabled()).thenReturn(true);
  }

  private void mockWrongTrustStoreKeycloakTlsProperties() {
    when(tlsProperties.getTrustStorePath()).thenReturn("fake.jks");
    when(tlsProperties.isEnabled()).thenReturn(true);
  }

  private void mockProperties() {
    when(secureStore.lookup(anyString())).thenReturn(Optional.of("password"));
    when(keycloakProperties.getAdmin()).thenReturn(keycloakAdminProperties);
    when(keycloakAdminProperties.getClientId()).thenReturn("clientId");
    when(keycloakStoreKeyProvider.globalStoreKey("clientId")).thenReturn("anyKey");
    when(keycloakProperties.getBaseUrl()).thenReturn("http://localhost:8080");
  }
}
