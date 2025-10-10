package org.folio.scheduler.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.utils.TokenUtils.tokenResponseAsString;

import org.apache.commons.codec.digest.DigestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.AccessTokenResponse;

@UnitTest
class TokenUtilsTest {

  @Test
  void tokenResponseAsString_positive() {
    var tokenResponse = new AccessTokenResponse();
    tokenResponse.setToken("validAccessToken");
    tokenResponse.setRefreshToken("validRefreshToken");
    tokenResponse.setExpiresIn(100L);

    var result = tokenResponseAsString(tokenResponse);

    assertThat(result)
      .contains("accessToken=" + DigestUtils.sha256Hex("validAccessToken"))
      .contains("refreshToken=" + DigestUtils.sha256Hex("validRefreshToken"))
      .contains("expiresIn=100");
  }

  @Test
  void tokenResponseAsString_positive_tokenAndRefreshTokenAreNull() {
    var tokenResponse = new AccessTokenResponse();
    tokenResponse.setToken(null);
    tokenResponse.setRefreshToken(null);
    tokenResponse.setExpiresIn(100L);

    var result = tokenResponseAsString(tokenResponse);

    assertThat(result).contains("accessToken=<null>")
      .contains("refreshToken=<null>")
      .contains("expiresIn=100");
  }
}
