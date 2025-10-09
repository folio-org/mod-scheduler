package org.folio.scheduler.integration.keycloak;

import com.github.benmanes.caffeine.cache.Expiry;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.keycloak.representations.AccessTokenResponse;

@Log4j2
@RequiredArgsConstructor
public class TokenCacheExpiry implements Expiry<String, AccessTokenResponse> {

  private final Function<AccessTokenResponse, Long> expireAfterCreateFunc;

  @Override
  public long expireAfterCreate(String tenant, AccessTokenResponse token, long currentTime) {
    log.debug("expireAfterCreate called: tenant = {}, tokenExpiresIn = {}", tenant, token.getExpiresIn());
    Long expiresAfter = expireAfterCreateFunc.apply(token);
    log.debug("expireAfterCreate result: expiresAfter = {} nanos", expiresAfter);
    return expiresAfter;
  }

  @Override
  public long expireAfterUpdate(String tenant, AccessTokenResponse token, long currentTime, long currentDuration) {
    log.debug("expireAfterUpdate called: tenant = {}, tokenExpiresIn = {}", tenant, token.getExpiresIn());
    log.debug("expireAfterUpdate result: expiresAfter = {} nanos", currentDuration);
    return currentDuration;
  }

  @Override
  public long expireAfterRead(String tenant, AccessTokenResponse token, long currentTime, long currentDuration) {
    log.debug("expireAfterRead called: tenant = {}, tokenExpiresIn = {}", tenant, token.getExpiresIn());
    log.debug("expireAfterRead result: expiresAfter = {} nanos", currentDuration);
    return currentDuration;
  }
}
