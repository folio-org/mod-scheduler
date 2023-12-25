package org.folio.scheduler.integration.securestore.impl;

import static org.folio.scheduler.integration.securestore.configuration.properties.VaultConfigProperties.DEFAULT_VAULT_SECRET_ROOT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.api.Logical;
import com.bettercloud.vault.response.LogicalResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.folio.scheduler.integration.securestore.configuration.properties.VaultConfigProperties;
import org.folio.scheduler.integration.securestore.exception.NotFoundException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class VaultStoreTest {

  public static final VaultConfigProperties CONFIG = VaultConfigProperties.builder()
    .enableSsl(false)
    .token("token")
    .address("address")
    .secretRoot(DEFAULT_VAULT_SECRET_ROOT)
    .build();

  @Mock
  private Vault vault;

  @InjectMocks
  private VaultStore secureStore = VaultStore.create(CONFIG);

  @ParameterizedTest
  @CsvSource({
    "folio_secretKey,folio,secretKey",
    "folio_diku_secretKey,folio/diku,secretKey",
    "folio_diku_dikuLib_secretKey,folio/diku/dikuLib,secretKey",
  })
  void getKey_positive(String key, String secretPath, String secretKey) throws Exception {
    var password = "Pa$$w0rd";

    var logical = mock(Logical.class);
    var logicalResponse = mock(LogicalResponse.class);
    when(vault.logical()).thenReturn(logical);
    when(logical.read(addRootTo(secretPath))).thenReturn(logicalResponse);
    when(logicalResponse.getData()).thenReturn(Map.of(secretKey, password));

    var actual = secureStore.get(key);

    assertEquals(password, actual);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void getKey_negative_emptyKey(String key) {
    var exc = assertThrows(IllegalArgumentException.class, () -> secureStore.get(key));
    assertEquals("Key is empty", exc.getMessage());
  }

  @Test
  void getKey_negative_shortKey() {
    var exc = assertThrows(IllegalArgumentException.class, () -> secureStore.get("folio"));
    assertEquals("Key should consist of at least two parts separated by '_'", exc.getMessage());
  }

  @Test
  void getKey_negative_notFoundValueByKey() throws VaultException {
    var key = "folio_secretKey";
    var path = "folio";

    var logical = mock(Logical.class);
    var logicalResponse = mock(LogicalResponse.class);
    when(vault.logical()).thenReturn(logical);
    when(logical.read(addRootTo(path))).thenReturn(logicalResponse);
    when(logicalResponse.getData()).thenReturn(Map.of());

    var ex = assertThrows(NotFoundException.class, () -> secureStore.get(key));
    assertEquals("Attribute: secretKey not set for folio", ex.getMessage());
  }

  @ParameterizedTest
  @CsvSource({
    "folio_secretKey,folio,secretKey",
    "folio_diku_secretKey,folio/diku,secretKey",
    "folio_diku_dikuLib_secretKey,folio/diku/dikuLib,secretKey",
  })
  void lookup_positive(String key, String secretPath, String secretKey) throws Exception {
    var password = "Pa$$w0rd";

    var logical = mock(Logical.class);
    var logicalResponse = mock(LogicalResponse.class);
    when(vault.logical()).thenReturn(logical);
    when(logical.read(addRootTo(secretPath))).thenReturn(logicalResponse);
    when(logicalResponse.getData()).thenReturn(Map.of(secretKey, password));

    var actual = secureStore.lookup(key);

    assertTrue(actual.isPresent());
    assertEquals(password, actual.get());
  }

  @Test
  void lookup_negative_notFoundValueByKey() throws VaultException {
    var key = "folio_secretKey";
    var path = "folio";

    var logical = mock(Logical.class);
    var logicalResponse = mock(LogicalResponse.class);
    when(vault.logical()).thenReturn(logical);
    when(logical.read(addRootTo(path))).thenReturn(logicalResponse);
    when(logicalResponse.getData()).thenReturn(Map.of());

    var actual = secureStore.lookup(key);

    assertTrue(actual.isEmpty());
  }

  @ParameterizedTest
  @CsvSource({
    "folio_secretKey,folio,secretKey",
    "folio_diku_secretKey,folio/diku,secretKey",
    "folio_diku_dikuLib_secretKey,folio/diku/dikuLib,secretKey",
  })
  void set_positive(String key, String secretPath, String secretName) throws Exception {
    var password = "Pa$$w0rd";

    var existingSecrets = Map.of("anotherSecretKey", password);
    var logical = mock(Logical.class);
    var logicalResponse = mock(LogicalResponse.class);
    when(vault.logical()).thenReturn(logical);
    when(logical.read(addRootTo(secretPath))).thenReturn(logicalResponse);
    when(logicalResponse.getData()).thenReturn(existingSecrets);
    when(vault.logical()).thenReturn(logical);

    var expectedData = new HashMap<String, Object>(existingSecrets);
    expectedData.put(secretName, password);

    secureStore.set(key, password);

    verify(logical).write(eq(addRootTo(secretPath)),
      argThat((Map<String, Object> data) -> data.entrySet().containsAll(expectedData.entrySet())));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void set_negative_emptyKey(String key) {
    var exc = assertThrows(IllegalArgumentException.class, () -> secureStore.set(key, "Pa$$w0rd"));
    assertEquals("Key is empty", exc.getMessage());
  }

  @Test
  void set_negative_shortKey() {
    var exc = assertThrows(IllegalArgumentException.class, () -> secureStore.set("folio", "Pa$$w0rd"));
    assertEquals("Key should consist of at least two parts separated by '_'", exc.getMessage());
  }

  @Test
  void set_negative_vaultException() throws VaultException {
    var path = "folio";
    var key = "folio_secretKey";

    var logical = mock(Logical.class);
    var logicalResponse = mock(LogicalResponse.class);
    when(vault.logical()).thenReturn(logical);
    when(logical.read(addRootTo(path))).thenReturn(logicalResponse);
    when(logicalResponse.getData()).thenReturn(Collections.emptyMap());
    when(vault.logical()).thenReturn(logical);
    when(logical.write(any(), any())).thenThrow(new VaultException("Unexpected error"));

    var ex = assertThrows(RuntimeException.class, () -> secureStore.set(key, "Pa$$w0rd"));
    assertEquals("Failed to save secret for secretKey", ex.getMessage());
  }

  private static String addRootTo(String path) {
    return DEFAULT_VAULT_SECRET_ROOT + '/' + path;
  }
}
