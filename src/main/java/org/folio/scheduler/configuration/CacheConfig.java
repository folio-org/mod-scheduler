package org.folio.scheduler.configuration;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

  @Value("${cache.keycloak-user-id.max-size:200}")
  private int keycloakUserIdMaxSize;
  @Value("${cache.keycloak-user-id.ttl:1800s}")
  private Duration keycloakUserIdTtl;

  @Value("${cache.system-user-id.max-size:200}")
  private int systemUserIdMaxSize;
  @Value("${cache.system-user-id.ttl:1800s}")
  private Duration systemUserIdTtl;

  @Value("${cache.client-secret-key.max-size:200}")
  private int clientSecretKeyMaxSize;
  @Value("${cache.client-secret-key.ttl:6000s}")
  private Duration clientSecretKeyTtl;

  @Bean
  public CacheManager cacheManager() {
    var cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(List.of(
      buildCache("keycloak-user-id", keycloakUserIdMaxSize, keycloakUserIdTtl),
      buildCache("system-user-id", systemUserIdMaxSize, systemUserIdTtl),
      buildCache("client-secret-key", clientSecretKeyMaxSize, clientSecretKeyTtl)
    ));
    return cacheManager;
  }

  private CaffeineCache buildCache(String name, int maxSize, Duration ttl) {
    return new CaffeineCache(name,
      Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(ttl).build());
  }
}
