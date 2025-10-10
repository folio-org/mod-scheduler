package org.folio.scheduler.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.utils.TokenUtils.tokenResponseAsString;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.AccessTokenResponse;

class TokenUtilsTest {

  @Test
  void tokenResponseAsString_positive() {
    var tokenResponse = new AccessTokenResponse();
    tokenResponse.setToken("validAccessToken");
    tokenResponse.setRefreshToken("validRefreshToken");
    tokenResponse.setExpiresIn(100L);

    var result = tokenResponseAsString(tokenResponse);

    assertThat(result).contains("accessToken=" + DigestUtils.sha256Hex("validAccessToken"));
    assertThat(result).contains("refreshToken=" + DigestUtils.sha256Hex("validRefreshToken"));
    assertThat(result).contains("expiresIn=100");
  }

  @Test
  void tokenResponseAsString_positive_tokenAndRefreshTokenAreNull() {
    AccessTokenResponse tokenResponse = new AccessTokenResponse();
    tokenResponse.setToken(null);
    tokenResponse.setRefreshToken(null);
    tokenResponse.setExpiresIn(100L);

    var result = tokenResponseAsString(tokenResponse);

    assertThat(result).contains("accessToken=<null>");
    assertThat(result).contains("refreshToken=<null>");
    assertThat(result).contains("expiresIn=100");
  }
}
