package org.folio.scheduler.it;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.folio.scheduler.service.TenantModuleVersionService;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class TenantModuleVersionIT extends BaseIntegrationTest {

  private static final String DROPPED_TENANT_ID = "dropped";

  @Autowired private TenantModuleVersionService tenantModuleVersionService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private FolioModuleMetadata folioModuleMetadata;

  @BeforeAll
  static void beforeAll() {
    setUpTenant();
  }

  @AfterAll
  static void afterAll(@Autowired Scheduler scheduler) throws Exception {
    removeTenant();
    deleteAllQuartzJobs(scheduler);
  }

  @Test
  void tenantInit_positive_currentVersionOwnsTenant() {
    try (var ignored = tenantContext()) {
      assertThat(tenantModuleVersionService.isOwnedByAnotherVersion()).isFalse();
    }
  }

  @Test
  void isOwnedByAnotherVersion_positive_anotherVersionOwnsTenant() {
    try (var ignored = tenantContext()) {
      jdbcTemplate.update("UPDATE tenant_module_version SET module_id = 'mod-scheduler-0.0.1-OTHER'");

      assertThat(tenantModuleVersionService.isOwnedByAnotherVersion()).isTrue();

      tenantModuleVersionService.markCurrentVersion();
      assertThat(tenantModuleVersionService.isOwnedByAnotherVersion()).isFalse();
    }
  }

  @Test
  void isOwnedByAnotherVersion_positive_markerRemoved() {
    try (var ignored = tenantContext()) {
      tenantModuleVersionService.clear();

      assertThat(tenantModuleVersionService.isOwnedByAnotherVersion()).isFalse();

      tenantModuleVersionService.markCurrentVersion();
    }
  }

  @Test
  void isOwnedByAnotherVersion_positive_schemaDroppedAfterBeingChecked() {
    enableTenant(DROPPED_TENANT_ID);
    try (var ignored = tenantContext(DROPPED_TENANT_ID)) {
      assertThat(tenantModuleVersionService.isOwnedByAnotherVersion()).isFalse();

      // simulates another instance deleting the tenant; this instance still has it cached
      jdbcTemplate.execute("DROP SCHEMA " + folioModuleMetadata.getDBSchemaName(DROPPED_TENANT_ID) + " CASCADE");

      assertThat(tenantModuleVersionService.isOwnedByAnotherVersion()).isFalse();
    }
  }

  private FolioExecutionContextSetter tenantContext() {
    return tenantContext(TENANT_ID);
  }

  private FolioExecutionContextSetter tenantContext(String tenant) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, singletonList(tenant));
    return new FolioExecutionContextSetter(folioModuleMetadata, Map.copyOf(headers));
  }
}
