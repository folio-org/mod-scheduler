package org.folio.scheduler.integration.securestore.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.folio.scheduler.integration.securestore.configuration.properties.EphemeralConfigProperties;
import org.folio.scheduler.integration.securestore.exception.NotFoundException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EphemeralStoreTest {

  public static final EphemeralConfigProperties CONFIG =
    EphemeralConfigProperties.builder().content(Map.of("foo", "bar")).build();

  @InjectMocks
  private EphemeralStore ephemeralStore = EphemeralStore.create(CONFIG);

  @Test
  void get_positive() {
    var key = "foo";
    var value = "bar";

    var result = ephemeralStore.get(key);

    assertEquals(value, result);
  }

  @Test
  void get_negative_notFound() {
    var key = "baz";

    assertThrows(NotFoundException.class, () -> ephemeralStore.get(key));
  }

  @Test
  void lookup_positive() {
    var key = "foo";
    var value = "bar";

    var result = ephemeralStore.lookup(key);

    assertTrue(result.isPresent());
    assertEquals(value, result.get());
  }

  @Test
  void lookup_negative_notFound() {
    var key = "baz";

    var result = ephemeralStore.lookup(key);

    assertTrue(result.isEmpty());
  }

  @Test
  void set_positive() {
    var key = "foo";
    var value = "bar";

    ephemeralStore.set(key, value);

    assertEquals(value, ephemeralStore.get(key));
  }

  @Test
  void set_negative() {
    var key = "foo";
    var value = "bar";

    ephemeralStore.set(key, value);

    assertThrows(NotFoundException.class, () -> ephemeralStore.get("baz"));
  }

  @Test
  void create_positive_propertiesIsNull() {
    var ephemeralStore = EphemeralStore.create(null);

    assertThrows(NotFoundException.class, () -> ephemeralStore.get("foo"));
  }

  @Test
  void create_positive_contentIsEmpty() {
    var ephemeralStore = EphemeralStore.create(EphemeralConfigProperties.builder().build());

    assertThrows(NotFoundException.class, () -> ephemeralStore.get("foo"));
  }
}
