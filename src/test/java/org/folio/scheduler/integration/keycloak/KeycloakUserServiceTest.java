package org.folio.scheduler.integration.keycloak;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.scheduler.integration.keycloak.KeycloakUserService.USER_ID_ATTR;
import static org.folio.test.TestConstants.TENANT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.spring.exception.NotFoundException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakUserServiceTest {

  private static final String USER_ID = "00000000-0000-0000-0000-000000000001";
  private static final String KEYCLOAK_USER_ID = "00000000-0000-0000-0000-000000000002";
  private static final String USERNAME = "test-username";

  @InjectMocks private KeycloakUserService service;
  @Mock(answer = RETURNS_DEEP_STUBS) private Keycloak keycloak;

  @BeforeEach
  void beforeEach() {
    verifyNoMoreInteractions(keycloak);
  }

  @Nested
  @DisplayName("findUserIdByKeycloakUsername")
  class FindUserIdByKeycloakUsername {

    @Test
    void positive() {
      var keycloakUser = new UserRepresentation();
      keycloakUser.setId(KEYCLOAK_USER_ID);
      keycloakUser.setUsername(USERNAME);
      keycloakUser.setAttributes(Map.of(USER_ID_ATTR, List.of(USER_ID)));

      when(keycloak.realm(TENANT_ID).users().searchByUsername(USERNAME, true)).thenReturn(List.of(keycloakUser));

      var result = service.findUserIdByKeycloakUsername(TENANT_ID, USERNAME);

      assertThat(result).isEqualTo(USER_ID);
    }

    @Test
    void negative_userIdAttributeIsMissing() {
      var keycloakUser = new UserRepresentation();
      keycloakUser.setId(KEYCLOAK_USER_ID);
      keycloakUser.setUsername(USERNAME);

      when(keycloak.realm(TENANT_ID).users().searchByUsername(USERNAME, true)).thenReturn(List.of(keycloakUser));

      assertThatThrownBy(() -> service.findUserIdByKeycloakUsername(TENANT_ID, USERNAME))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("user_id attribute is not found in user with username: test-username");
    }

    @Test
    void negative_userNotFoundByUsername() {
      when(keycloak.realm(TENANT_ID).users().searchByUsername(USERNAME, true)).thenReturn(emptyList());

      assertThatThrownBy(() -> service.findUserIdByKeycloakUsername(TENANT_ID, USERNAME))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("Keycloak user doesn't exist with the given username: test-username");
    }
  }

  @Nested
  @DisplayName("findKeycloakIdByTenantAndUserId")
  class FindKeycloakIdByTenantAndUserId {

    @Test
    void positive_notRefreshIfTokenNull() {
      var userRepresentation = new UserRepresentation();
      userRepresentation.setId(KEYCLOAK_USER_ID);
      var query = USER_ID_ATTR + ":" + USER_ID;

      when(keycloak.realm(TENANT_ID).users().searchByAttributes(query)).thenReturn(List.of(userRepresentation));
      when(keycloak.tokenManager().getAccessToken()).thenReturn(null);

      var result = service.findKeycloakIdByTenantAndUserId(TENANT_ID, USER_ID);
      assertEquals(KEYCLOAK_USER_ID, result);
    }

    @Test
    void negative_throwExceptions() {
      var userRepresentation = new UserRepresentation();
      userRepresentation.setId(KEYCLOAK_USER_ID);
      var query = USER_ID_ATTR + ":" + USER_ID;

      when(keycloak.realm(TENANT_ID).users().searchByAttributes(query))
        .thenReturn(List.of(userRepresentation, userRepresentation))
        .thenReturn(List.of());

      assertThrows(IllegalStateException.class, () -> service.findKeycloakIdByTenantAndUserId(TENANT_ID, USER_ID),
        "Too many keycloak users with 'user_id' attribute: " + USER_ID);
      assertThrows(NotFoundException.class, () -> service.findKeycloakIdByTenantAndUserId(TENANT_ID, USER_ID),
        "Keycloak user doesn't exist with the given 'user_id' attribute: " + USER_ID);
    }
  }
}
