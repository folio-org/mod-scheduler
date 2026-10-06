package org.folio.scheduler.integration.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.folio.scheduler.service.TenantModuleVersionService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantAwareMessageFilterTest {

  private static final String TOPIC = "it.test.mgr-tenant-entitlements.scheduled-job";

  @Mock private FolioModuleMetadata folioModuleMetadata;
  @Mock private TenantModuleVersionService tenantModuleVersionService;

  private TenantAwareMessageFilter filter;

  @BeforeEach
  void setUp() {
    filter = new TenantAwareMessageFilter(folioModuleMetadata, tenantModuleVersionService, true);
  }

  @Test
  void filter_positive_disabledFilterAcceptsEverything() {
    var disabledFilter = new TenantAwareMessageFilter(folioModuleMetadata, tenantModuleVersionService, false);

    assertThat(disabledFilter.filter(consumerRecord(TENANT_ID))).isFalse();

    verifyNoInteractions(tenantModuleVersionService);
  }

  @Test
  void filter_positive_eventOfTenantNotOwnedByAnotherVersionIsAccepted() {
    when(tenantModuleVersionService.isOwnedByAnotherVersion()).thenReturn(false);

    assertThat(filter.filter(consumerRecord(TENANT_ID))).isFalse();

    verify(tenantModuleVersionService).isOwnedByAnotherVersion();
  }

  @Test
  void filter_positive_eventOfTenantOwnedByAnotherVersionIsDiscarded() {
    when(tenantModuleVersionService.isOwnedByAnotherVersion()).thenReturn(true);

    assertThat(filter.filter(consumerRecord(TENANT_ID))).isTrue();
  }

  @Test
  void filter_positive_recordWithoutTenantHeaderIsAccepted() {
    assertThat(filter.filter(consumerRecord(null))).isFalse();
    assertThat(filter.filter(consumerRecord("  "))).isFalse();

    verifyNoInteractions(tenantModuleVersionService);
  }

  @Test
  void resolveTenant_positive_prefersOkapiTenantHeader() {
    var consumerRecord = consumerRecord(null);
    consumerRecord.headers().add("folio.tenantId", "folio-tenant".getBytes(UTF_8));
    consumerRecord.headers().add("X-Okapi-Tenant", " header-tenant ".getBytes(UTF_8));

    assertThat(TenantAwareMessageFilter.resolveTenant(consumerRecord)).isEqualTo("header-tenant");
  }

  @Test
  void resolveTenant_positive_fallsBackToFolioTenantIdHeader() {
    var consumerRecord = consumerRecord(null);
    consumerRecord.headers().add("x-okapi-tenant", "  ".getBytes(UTF_8));
    consumerRecord.headers().add("folio.tenantId", "folio-tenant".getBytes(UTF_8));

    assertThat(TenantAwareMessageFilter.resolveTenant(consumerRecord)).isEqualTo("folio-tenant");
  }

  private static ConsumerRecord<String, Object> consumerRecord(String okapiTenant) {
    var consumerRecord = new ConsumerRecord<String, Object>(TOPIC, 0, 0L, TENANT_ID, new Object());
    if (okapiTenant != null) {
      consumerRecord.headers().add("x-okapi-tenant", okapiTenant.getBytes(UTF_8));
    }

    return consumerRecord;
  }
}
