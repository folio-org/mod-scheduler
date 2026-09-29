package org.folio.scheduler.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.folio.scheduler.repository.TenantModuleVersionRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessResourceUsageException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantModuleVersionServiceTest {

  private static final String OWN_MODULE_ID = "mod-scheduler-4.2.0";
  private static final String SCHEMA = TENANT_ID + "_mod_scheduler";
  private static final String TIMER_TABLE = "timer";
  private static final String MARKER_TABLE = "tenant_module_version";

  @Mock private TenantModuleVersionRepository repository;
  @Mock private FolioExecutionContext context;
  @Mock private FolioModuleMetadata moduleMetadata;

  private TenantModuleVersionService service;

  @BeforeEach
  void setUp() {
    when(moduleMetadata.getModuleId()).thenReturn(OWN_MODULE_ID);
    when(context.getTenantId()).thenReturn(TENANT_ID);
    service = new TenantModuleVersionService(repository, context, moduleMetadata);
  }

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(repository);
  }

  @Test
  void markCurrentVersion_positive() {
    service.markCurrentVersion();

    verify(repository).upsert(OWN_MODULE_ID);
  }

  @Test
  void clear_positive() {
    mockTable(MARKER_TABLE, true);

    service.clear();

    verify(repository).tableExists(SCHEMA, MARKER_TABLE);
    verify(repository).deleteOwner();
  }

  @Test
  void clear_positive_markerTableMissing() {
    mockTable(MARKER_TABLE, false);

    service.clear();

    verify(repository).tableExists(SCHEMA, MARKER_TABLE);
    verify(repository, never()).deleteOwner();
  }

  @Test
  void isOwnedByAnotherVersion_positive_ownedByThisVersion() {
    mockTablesExist();
    mockMarker(Optional.of(OWN_MODULE_ID));

    assertThat(service.isOwnedByAnotherVersion()).isFalse();
  }

  @Test
  void isOwnedByAnotherVersion_positive_ownedByAnotherVersion() {
    mockTablesExist();
    mockMarker(Optional.of("mod-scheduler-4.1.0"));

    assertThat(service.isOwnedByAnotherVersion()).isTrue();
  }

  @Test
  void isOwnedByAnotherVersion_positive_schemaPredatesMarker() {
    mockTable(TIMER_TABLE, true);
    mockTable(MARKER_TABLE, false);

    assertThat(service.isOwnedByAnotherVersion()).isTrue();
  }

  @Test
  void isOwnedByAnotherVersion_positive_tenantIsNotInitializedYet() {
    mockTable(TIMER_TABLE, false);

    assertThat(service.isOwnedByAnotherVersion()).isFalse();
  }

  @Test
  void isOwnedByAnotherVersion_positive_markerNotWrittenYet() {
    mockTablesExist();
    mockMarker(Optional.empty());

    assertThat(service.isOwnedByAnotherVersion()).isFalse();
  }

  @Test
  void isOwnedByAnotherVersion_positive_remembersThatMarkerTableExists() {
    mockTablesExist();
    mockMarker(Optional.of(OWN_MODULE_ID));

    assertThat(service.isOwnedByAnotherVersion()).isFalse();
    assertThat(service.isOwnedByAnotherVersion()).isFalse();

    verify(repository, times(1)).tableExists(SCHEMA, TIMER_TABLE);
    verify(repository, times(1)).tableExists(SCHEMA, MARKER_TABLE);
    verify(repository, times(2)).findModuleId();
  }

  @Test
  void isOwnedByAnotherVersion_positive_readsOwnerOnEveryCheck() {
    mockTablesExist();
    when(repository.findModuleId())
      .thenReturn(Optional.of(OWN_MODULE_ID))
      .thenReturn(Optional.of("mod-scheduler-4.3.0"));

    assertThat(service.isOwnedByAnotherVersion()).isFalse();
    assertThat(service.isOwnedByAnotherVersion()).isTrue();
  }

  @Test
  void clear_positive_forgetsMarkerTable() {
    mockTablesExist();
    mockMarker(Optional.of(OWN_MODULE_ID));
    service.isOwnedByAnotherVersion();

    service.clear();
    service.isOwnedByAnotherVersion();

    verify(repository, times(2)).tableExists(SCHEMA, TIMER_TABLE);
    verify(repository, times(3)).tableExists(SCHEMA, MARKER_TABLE);
    verify(repository).deleteOwner();
  }

  @Test
  void isOwnedByAnotherVersion_positive_markerTableDroppedAfterBeingCached() {
    mockTablesExist();
    when(repository.findModuleId())
      .thenReturn(Optional.of(OWN_MODULE_ID))
      .thenThrow(new InvalidDataAccessResourceUsageException("relation does not exist"));
    service.isOwnedByAnotherVersion();
    when(repository.tableExists(SCHEMA, TIMER_TABLE)).thenReturn(false);

    assertThat(service.isOwnedByAnotherVersion()).isFalse();

    verify(repository, times(2)).tableExists(SCHEMA, TIMER_TABLE);
  }

  private void mockTablesExist() {
    mockTable(TIMER_TABLE, true);
    mockTable(MARKER_TABLE, true);
  }

  private void mockTable(String table, boolean exists) {
    when(moduleMetadata.getDBSchemaName(TENANT_ID)).thenReturn(SCHEMA);
    when(repository.tableExists(SCHEMA, table)).thenReturn(exists);
  }

  private void mockMarker(Optional<String> owner) {
    when(repository.findModuleId()).thenReturn(owner);
  }
}
