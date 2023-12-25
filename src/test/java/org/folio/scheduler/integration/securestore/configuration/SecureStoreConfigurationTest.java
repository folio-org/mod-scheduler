package org.folio.scheduler.integration.securestore.configuration;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.folio.scheduler.integration.securestore.configuration.properties.AwsConfigProperties;
import org.folio.scheduler.integration.securestore.configuration.properties.EphemeralConfigProperties;
import org.folio.scheduler.integration.securestore.configuration.properties.VaultConfigProperties;
import org.folio.scheduler.integration.securestore.impl.AwsStore;
import org.folio.scheduler.integration.securestore.impl.EphemeralStore;
import org.folio.scheduler.integration.securestore.impl.VaultStore;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SecureStoreConfigurationTest {

  @InjectMocks private SecureStoreConfiguration secureStoreConfiguration;

  @Test
  void ephemeralStore_positive_returnsEphemeralStore() {
    var secureStore = secureStoreConfiguration.ephemeralStore(ephemeralConfigProperties());

    assertInstanceOf(EphemeralStore.class, secureStore);
  }

  @Test
  void ephemeralProperties_positive_returnsEphemeralConfigProperties() {
    var ephemeralConfigProperties = secureStoreConfiguration.ephemeralProperties();

    assertNotNull(ephemeralConfigProperties);
  }

  @Test
  void awsStore_positive_returnsAwsStore() {
    var secureStore = secureStoreConfiguration.awsStore(awsConfigProperties());

    assertInstanceOf(AwsStore.class, secureStore);
  }

  @Test
  void awsStore_negative_sdkClientException() {
    var awsConfigProperties = awsConfigProperties();
    awsConfigProperties.setUseIam(FALSE);

    assertThrows(SdkClientException.class, () -> secureStoreConfiguration.awsStore(awsConfigProperties));
  }

  @Test
  void awsProperties_positive_returnsAwsConfigProperties() {
    var awsConfigProperties = secureStoreConfiguration.awsProperties();

    assertNotNull(awsConfigProperties);
  }

  @Test
  void vaultStore_positive_returnsVaultStore() {
    var secureStore = secureStoreConfiguration.vaultStore(vaultConfigProperties());

    assertInstanceOf(VaultStore.class, secureStore);
  }

  @Test
  void vaultProperties_positive_returnsVaultConfigProperties() {
    var vaultConfigProperties = secureStoreConfiguration.vaultProperties();

    assertNotNull(vaultConfigProperties);
  }

  private static VaultConfigProperties vaultConfigProperties() {
    var vaultConfigProperties = new VaultConfigProperties();
    vaultConfigProperties.setAddress("http://localhost:8200");
    vaultConfigProperties.setToken("token");
    return vaultConfigProperties;
  }

  private static AwsConfigProperties awsConfigProperties() {
    var awsConfigProperties = new AwsConfigProperties();
    awsConfigProperties.setRegion("us-east-1");
    awsConfigProperties.setUseIam(TRUE);
    return awsConfigProperties;
  }

  private static EphemeralConfigProperties ephemeralConfigProperties() {
    var ephemeralConfigProperties = new EphemeralConfigProperties();
    ephemeralConfigProperties.setContent(Map.of("key", "value"));
    return ephemeralConfigProperties;
  }
}
