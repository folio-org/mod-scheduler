package org.folio.scheduler.support.extension.impl;

import static org.folio.test.TestUtils.parse;
import static org.folio.test.TestUtils.readString;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.PartialImportRepresentation;

@Log4j2
public class KeycloakContainerExtension implements BeforeAllCallback, AfterAllCallback {

  @SuppressWarnings("resource")
  private static final KeycloakContainer CONTAINER = new KeycloakContainer("quay.io/keycloak/keycloak:22.0.1")
    .withFeaturesEnabled("token-exchange", "admin-fine-grained-authz");
  private static final String REALM = "master";
  private static final String REALM_JSON = "json/keycloak/master-realm.json";
  private static final String IMPORTED_CLIENT_ID = "be-admin-client";
  private static final String IMPORTED_CLIENT_SECRET = "be-admin-client-secret";
  private static Keycloak ADMIN_CLIENT;

  @Override
  public void beforeAll(ExtensionContext context) {
    if (!CONTAINER.isRunning()) {
      CONTAINER.start();
    }
    ADMIN_CLIENT = CONTAINER.getKeycloakAdminClient();

    setupMasterRealm();

    System.setProperty("KC_URL", CONTAINER.getAuthServerUrl());
    System.setProperty("KC_ADMIN_CLIENT_ID", IMPORTED_CLIENT_ID);
    System.setProperty("KC_ADMIN_CLIENT_SECRET", IMPORTED_CLIENT_SECRET);
    System.setProperty("KC_ADMIN_USERNAME", CONTAINER.getAdminUsername());
    System.setProperty("KC_ADMIN_PASSWORD", CONTAINER.getAdminPassword());
    System.setProperty("KC_ADMIN_GRANT_TYPE", OAuth2Constants.CLIENT_CREDENTIALS);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    System.clearProperty("KC_URL");
    System.clearProperty("KC_ADMIN_CLIENT_ID");
    System.clearProperty("KC_ADMIN_CLIENT_SECRET");
    System.clearProperty("KC_ADMIN_USERNAME");
    System.clearProperty("KC_ADMIN_PASSWORD");
    System.clearProperty("KC_ADMIN_GRANT_TYPE");
  }

  public static Keycloak getKeycloakAdminClient() {
    if (ADMIN_CLIENT == null) {
      throw new IllegalStateException("Keycloak admin client isn't initialized");
    }

    return ADMIN_CLIENT;
  }

  private static void setupMasterRealm() {
    log.info("Setting up master realm");
    var realmJson = readString(REALM_JSON);
    var realmPartialImport = parse(realmJson, PartialImportRepresentation.class);
    ADMIN_CLIENT.realm(REALM).partialImport(realmPartialImport);
  }
}
