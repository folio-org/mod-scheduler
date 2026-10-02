package org.folio.scheduler.it;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.integration.kafka.model.ResourceEventType.DELETE;
import static org.folio.scheduler.domain.dto.TimerUnit.SECOND;
import static org.folio.scheduler.support.TestConstants.TENANT_ID;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.folio.integration.kafka.model.ResourceEvent;
import org.folio.integration.kafka.model.ResourceEventType;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
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
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "folio.kafka.tenant-filter.enabled=true")
class TenantOwnershipFilterIT extends BaseIntegrationTest {

  private static final String SCHEDULED_TIMER_TOPIC = "it.test.mgr-tenant-entitlements.scheduled-job";
  private static final String FOREIGN_TENANT_ID = "foreign";

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private FolioModuleMetadata folioModuleMetadata;
  @Autowired private KafkaTemplate<String, ResourceEvent<ScheduledTimers>> kafkaTemplate;

  @BeforeAll
  static void beforeAll(@Autowired KafkaAdmin kafkaAdmin) {
    createTopic(SCHEDULED_TIMER_TOPIC, kafkaAdmin);
    setUpTenant();
    enableTenant(FOREIGN_TENANT_ID);
  }

  @AfterAll
  static void afterAll(@Autowired Scheduler scheduler) throws Exception {
    removeTenant(FOREIGN_TENANT_ID);
    removeTenant();
    deleteAllQuartzJobs(scheduler);
  }

  @Test
  void scheduledJobEvent_positive_processedOnlyForTenantsOwnedByCurrentVersion() {
    inTenant(FOREIGN_TENANT_ID, () ->
      jdbcTemplate.update("UPDATE tenant_module_version SET module_id = 'mod-scheduler-0.0.1-OTHER'"));

    kafkaTemplate.send(producerRecord(FOREIGN_TENANT_ID));
    kafkaTemplate.send(producerRecord(TENANT_ID));

    // the topic has one partition, so once the owned tenant's event is handled the foreign one was seen too
    await().untilAsserted(() -> assertThat(timerCount(TENANT_ID)).isEqualTo(1));
    assertThat(timerCount(FOREIGN_TENANT_ID)).isZero();
  }

  @Test
  void scheduledJobEvent_positive_deleteForUninitializedTenantDoesNotBlockOtherEvents() {
    inTenant(TENANT_ID, () -> jdbcTemplate.update("DELETE FROM timer"));
    assertThat(timerCount(TENANT_ID)).isZero();

    kafkaTemplate.send(producerRecord("uninitialized", DELETE));
    kafkaTemplate.send(producerRecord(TENANT_ID, CREATE));

    // the topic has one partition, so the second event is handled only if the first one is not retried forever
    await().untilAsserted(() -> assertThat(timerCount(TENANT_ID)).isEqualTo(1));
  }

  private int timerCount(String tenant) {
    var count = new int[1];
    inTenant(tenant, () -> count[0] = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM timer", Integer.class));
    return count[0];
  }

  private void inTenant(String tenant, Runnable action) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(TENANT, singletonList(tenant));
    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata, Map.copyOf(headers))) {
      action.run();
    }
  }

  private static ProducerRecord<String, ResourceEvent<ScheduledTimers>> producerRecord(String tenant) {
    return producerRecord(tenant, CREATE);
  }

  private static ProducerRecord<String, ResourceEvent<ScheduledTimers>> producerRecord(String tenant,
    ResourceEventType type) {
    var producerRecord = new ProducerRecord<String, ResourceEvent<ScheduledTimers>>(
      SCHEDULED_TIMER_TOPIC, null, resourceEvent(tenant, type));
    producerRecord.headers().add(new RecordHeader(TENANT, tenant.getBytes(UTF_8)));
    return producerRecord;
  }

  private static ResourceEvent<ScheduledTimers> resourceEvent(String tenant, ResourceEventType type) {
    var scheduledTimers = scheduledTimers();
    var builder = ResourceEvent.<ScheduledTimers>baseBuilder()
      .resourceName("Scheduled Job")
      .tenant(tenant)
      .type(type);
    return (type == DELETE ? builder.oldValue(scheduledTimers) : builder.newValue(scheduledTimers)).build();
  }

  private static ScheduledTimers scheduledTimers() {
    return new ScheduledTimers()
      .moduleId("mod-foo-1.0.0")
      .applicationId("app-foo-1.0.0")
      .timers(singletonList(new RoutingEntry()
        .methods(singletonList("POST"))
        .pathPattern("/test")
        .delay("1")
        .unit(SECOND)));
  }
}
