package org.folio.scheduler.integration.keycloak;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.MapUtils;
import org.folio.spring.exception.NotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.cache.annotation.Cacheable;

@Log4j2
@RequiredArgsConstructor
public class KeycloakUserService {

  public static final String USER_ID_ATTR = "user_id";

  private final Keycloak keycloak;

  @Cacheable(cacheNames = "keycloak-user-id", key = "#tenant + ':' + #userId")
  public String findKeycloakIdByTenantAndUserId(String tenant, String userId) {
    return findKeycloakUser(tenant, userId).getId();
  }

  /**
   * Retrieves user identifier from keycloak user by username.
   *
   * @param realm - realm identifier
   * @param username - user username
   * @return found user identifier by keycloak username
   * @throws NotFoundException if keycloak user not found by username or having empty user_id attribute
   */
  public String findUserIdByKeycloakUsername(String realm, String username) {
    var foundUsers = keycloak.realm(realm).users().searchByUsername(username, true);
    if (isEmpty(foundUsers)) {
      throw new NotFoundException("Keycloak user doesn't exist with the given username: " + username);
    }

    var keycloakUser = foundUsers.get(0);
    var userIdAttributes = MapUtils.emptyIfNull(keycloakUser.getAttributes()).get(USER_ID_ATTR);
    if (isEmpty(userIdAttributes)) {
      throw new NotFoundException(format(
        "%s attribute is not found in user with username: %s", USER_ID_ATTR, username));
    }

    return userIdAttributes.get(0);
  }

  private UserRepresentation findKeycloakUser(String tenant, String userId) {
    var query = USER_ID_ATTR + ":" + userId;
    refreshTokenIfExists();
    var keycloakUser = keycloak.realm(tenant).users().searchByAttributes(query);
    if (isEmpty(keycloakUser)) {
      throw new NotFoundException("Keycloak user doesn't exist with the given 'user_id' attribute: " + userId);
    }
    if (keycloakUser.size() != 1) {
      throw new IllegalStateException("Too many keycloak users with 'user_id' attribute: " + userId);
    }
    return keycloakUser.get(0);
  }

  private void refreshTokenIfExists() {
    if (isNull(keycloak.tokenManager().getAccessToken())) {
      return;
    }
    keycloak.tokenManager().refreshToken();
  }
}
