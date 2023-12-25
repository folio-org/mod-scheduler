package org.folio.scheduler.integration.securestore.impl;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.scheduler.integration.securestore.SecureStore;
import org.folio.scheduler.integration.securestore.configuration.properties.EphemeralConfigProperties;
import org.folio.scheduler.integration.securestore.exception.NotFoundException;

@Log4j2
public final class EphemeralStore implements SecureStore {

  private Map<String, String> store;

  private EphemeralStore(EphemeralConfigProperties properties) {
    if (nonNull(properties) && MapUtils.isNotEmpty(properties.getContent())) {
      store = new ConcurrentHashMap<>(properties.getContent());
    } else {
      store = new ConcurrentHashMap<>();
    }
  }

  @Override
  public String get(String key) {
    var value = store.get(key);
    if (isNull(value)) {
      throw new NotFoundException("Nothing associated w/ key: " + key);
    }
    return value;
  }

  @Override
  public Optional<String> lookup(String key) {
    return Optional.ofNullable(store.get(key));
  }

  @Override
  public void set(String key, String value) {
    store.put(key, value);
  }

  public static EphemeralStore create(EphemeralConfigProperties properties) {
    return new EphemeralStore(properties);
  }
}
