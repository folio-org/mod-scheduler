package org.folio.scheduler.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.scheduler.domain.dto.TimerUnit;
import org.folio.scheduler.domain.entity.TimerDescriptorEntity;
import org.folio.scheduler.domain.model.TimerType;
import org.folio.scheduler.repository.SchedulerTimerRepository;
import org.folio.scheduler.support.base.BaseIntegrationTest;
import org.folio.spring.FolioExecutionContext;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

@IntegrationTest
class TimerDescriptorRepositoryIT extends BaseIntegrationTest {

  private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID TEST_USER_A_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID TEST_USER_B_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final String TEST_MODULE_NAME = "test-module";
  private static final String TEST_PATH_PATTERN = "/test";

  @Autowired
  private SchedulerTimerRepository repository;

  @MockBean
  private FolioExecutionContext folioExecutionContext;

  @Test
  void saveAndFlush_positive_populatesAllAuditFields() {
    // Arrange
    when(folioExecutionContext.getUserId()).thenReturn(TEST_USER_ID);
    var entity = createTimerEntity();
    var now = OffsetDateTime.now(ZoneOffset.UTC);

    // Act
    var saved = repository.saveAndFlush(entity);

    // Assert
    assertThat(saved.getCreatedDate()).isCloseTo(now, within(1, ChronoUnit.MINUTES));
    assertThat(saved.getCreatedByUserId()).isEqualTo(TEST_USER_ID);
    assertThat(saved.getUpdatedDate()).isCloseTo(now, within(1, ChronoUnit.MINUTES));
    assertThat(saved.getUpdatedByUserId()).isEqualTo(TEST_USER_ID);
    assertThat(saved.getCreatedDate().getOffset()).isEqualTo(ZoneOffset.UTC);
    assertThat(saved.getUpdatedDate().getOffset()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void saveAndFlush_positive_refreshesOnlyUpdateFields() {
    // Arrange - Create with user A
    when(folioExecutionContext.getUserId()).thenReturn(TEST_USER_A_ID);
    var entity = createTimerEntity();
    var created = repository.saveAndFlush(entity);

    var originalCreatedDate = created.getCreatedDate();
    var originalCreatedByUserId = created.getCreatedByUserId();

    // Update with user B
    when(folioExecutionContext.getUserId()).thenReturn(TEST_USER_B_ID);
    created.getTimerDescriptor().setEnabled(false);
    var now = OffsetDateTime.now(ZoneOffset.UTC);

    // Act
    var updated = repository.saveAndFlush(created);

    // Assert
    assertThat(updated.getCreatedDate()).isEqualTo(originalCreatedDate);
    assertThat(updated.getCreatedByUserId()).isEqualTo(originalCreatedByUserId);
    assertThat(updated.getUpdatedDate()).isCloseTo(now, within(1, ChronoUnit.MINUTES));
    assertThat(updated.getUpdatedByUserId()).isEqualTo(TEST_USER_B_ID);
  }

  @Test
  void saveAndFlush_positive_handlesNullUserContext() {
    // Arrange
    when(folioExecutionContext.getUserId()).thenReturn(null);
    var entity = createTimerEntity();

    // Act
    var saved = repository.saveAndFlush(entity);

    // Assert
    assertThat(saved.getCreatedDate()).isNotNull();
    assertThat(saved.getUpdatedDate()).isNotNull();
    assertThat(saved.getCreatedByUserId()).isNull();
    assertThat(saved.getUpdatedByUserId()).isNull();
  }

  private TimerDescriptorEntity createTimerEntity() {
    var entity = new TimerDescriptorEntity();
    entity.setId(UUID.randomUUID());
    entity.setType(TimerType.USER);
    entity.setModuleName(TEST_MODULE_NAME);

    var descriptor = new TimerDescriptor();
    descriptor.setId(entity.getId());
    descriptor.setEnabled(true);
    descriptor.setModuleName(TEST_MODULE_NAME);

    var routingEntry = new RoutingEntry();
    routingEntry.setPathPattern(TEST_PATH_PATTERN);
    routingEntry.setMethods(List.of("POST"));
    routingEntry.setDelay("10");
    routingEntry.setUnit(TimerUnit.SECOND);

    descriptor.setRoutingEntry(routingEntry);
    entity.setTimerDescriptor(descriptor);

    return entity;
  }
}
