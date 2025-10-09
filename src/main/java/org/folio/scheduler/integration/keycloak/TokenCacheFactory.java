package org.folio.scheduler.integration.keycloak;

import static java.time.Duration.ofSeconds;
import static org.folio.scheduler.utils.TokenUtils.tokenResponseAsString;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Scheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.integration.keycloak.configuration.properties.TokenCacheProperties;
import org.keycloak.representations.AccessTokenResponse;

@Log4j2
@RequiredArgsConstructor
public class TokenCacheFactory {

  private static final int MIN_EARLY_EXPIRATION_SEC = 30;
  private final TokenCacheProperties tokenCacheProperties;

  public Cache<String, AccessTokenResponse> createCache() {
    var builder = Caffeine.newBuilder()
      .expireAfter(new TokenCacheExpiry(this::calculateTtl))
      .scheduler(Scheduler.systemScheduler())
      .initialCapacity(tokenCacheProperties.getCapacity().getInitial())
      .maximumSize(tokenCacheProperties.getCapacity().getMax());

    builder.removalListener((k, jwt, cause) -> log.debug("Cached access token removed: key={}, cause={}", k, cause));
    return builder.build();
  }

  private long calculateTtl(AccessTokenResponse token) {
    var expiresIn = token.getExpiresIn();
    var refreshBeforeExpiry = tokenCacheProperties.getRefreshBeforeExpirySec();

    log.debug("Calculating token TTL: tokenExpiresIn = {} secs, refreshBeforeExpiry = {} secs",
      expiresIn, refreshBeforeExpiry);

    // invalidating a cache entry prior to the token expiration.
    var earlyExpiresIn = expiresIn - refreshBeforeExpiry;
    var duration = earlyExpiresIn > MIN_EARLY_EXPIRATION_SEC ? ofSeconds(earlyExpiresIn) : ofSeconds(expiresIn);
    log.debug("Token TTL calculated: duration = {} secs, token = {}",
      duration::toSeconds, () -> tokenResponseAsString(token));

    return duration.toNanos();
  }
}


