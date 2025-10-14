package org.folio.scheduler.utils;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import lombok.experimental.UtilityClass;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.keycloak.representations.AccessTokenResponse;

@UtilityClass
public class TokenUtils {

  public static String tokenResponseAsString(AccessTokenResponse tokenResponse) {
    return new ToStringBuilder(tokenResponse)
      .append("accessToken", tokenHash(tokenResponse.getToken()))
      .append("refreshToken", tokenHash(tokenResponse.getRefreshToken()))
      .append("expiresIn", tokenResponse.getExpiresIn())
      .toString();
  }

  private static String tokenHash(String token) {
    return isNotEmpty(token) ? DigestUtils.sha256Hex(token) : null;
  }
}
