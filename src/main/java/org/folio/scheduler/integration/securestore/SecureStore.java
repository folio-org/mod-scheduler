package org.folio.scheduler.integration.securestore;

import java.util.Optional;

public interface SecureStore {

  String get(String key);

  void set(String key, String value);

  default Optional<String> lookup(String key) {
    try {
      return Optional.ofNullable(get(key));
    } catch (Exception e) {
      return Optional.empty();
    }
  }
}
