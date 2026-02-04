package org.folio.scheduler.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.folio.scheduler.support.TestConstants.MODULE_NAME;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@UnitTest
@SpringBootTest(classes = {
  org.folio.scheduler.mapper.TimerDescriptorMapperImpl.class,
  org.folio.scheduler.mapper.DateConvertHelper.class
})
class TimerDescriptorMapperTest {

  private static final UUID TEST_TIMER_ID = UUID.fromString("a1b2c3d4-e5f6-4789-a012-3456789abcde");
  private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired
  private TimerDescriptorMapper mapper;

  @Test
  void toDescriptorEntity_positive_ignoresMetadataFromDto() {
    var metadata = new org.folio.scheduler.domain.dto.Metadata();
    metadata.setCreatedByUserId(TEST_USER_ID);
    metadata.setCreatedDate(Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));
    metadata.setUpdatedByUserId(TEST_USER_ID);
    metadata.setUpdatedDate(Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));

    var descriptor = new TimerDescriptor();
    descriptor.setId(TEST_TIMER_ID);
    descriptor.setEnabled(true);
    descriptor.setModuleName(MODULE_NAME);
    descriptor.setMetadata(metadata);
    descriptor.setRoutingEntry(new RoutingEntry()
      .pathPattern("/test")
      .methods(List.of("POST")));

    var entity = mapper.toDescriptorEntity(descriptor);

    assertThat(entity.getCreatedByUserId()).isNull();
    assertThat(entity.getCreatedDate()).isNull();
    assertThat(entity.getUpdatedByUserId()).isNull();
    assertThat(entity.getUpdatedDate()).isNull();
  }

  @Test
  void toDescriptor_positive_mapsAuditFieldsToMetadata() {
    var entity = new org.folio.scheduler.domain.entity.TimerDescriptorEntity();
    entity.setId(TEST_TIMER_ID);

    var createdDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
    var updatedDate = OffsetDateTime.now(ZoneOffset.UTC);

    entity.setCreatedDate(createdDate);
    entity.setCreatedByUserId(TEST_USER_ID);
    entity.setUpdatedDate(updatedDate);
    entity.setUpdatedByUserId(TEST_USER_ID);

    var descriptor = new TimerDescriptor();
    descriptor.setId(TEST_TIMER_ID);
    descriptor.setEnabled(true);
    descriptor.setModuleName(MODULE_NAME);
    descriptor.setRoutingEntry(new RoutingEntry()
      .pathPattern("/test")
      .methods(List.of("POST")));

    entity.setTimerDescriptor(descriptor);

    var result = mapper.toDescriptor(entity);

    assertThat(result).isNotNull();
    assertThat(result.getMetadata()).isNotNull();
    assertThat(result.getMetadata().getCreatedDate()).isNotNull();
    assertThat(result.getMetadata().getUpdatedDate()).isNotNull();
    assertThat(OffsetDateTime.ofInstant(result.getMetadata().getCreatedDate().toInstant(), ZoneOffset.UTC))
      .isCloseTo(createdDate, within(1, ChronoUnit.MILLIS));
    assertThat(result.getMetadata().getCreatedByUserId()).isEqualTo(TEST_USER_ID);
    assertThat(OffsetDateTime.ofInstant(result.getMetadata().getUpdatedDate().toInstant(), ZoneOffset.UTC))
      .isCloseTo(updatedDate, within(1, ChronoUnit.MILLIS));
    assertThat(result.getMetadata().getUpdatedByUserId()).isEqualTo(TEST_USER_ID);
  }

  @Test
  void toDescriptor_positive_createsDeepCopyOfDescriptor() {
    var entity = new org.folio.scheduler.domain.entity.TimerDescriptorEntity();
    entity.setId(TEST_TIMER_ID);
    entity.setCreatedDate(OffsetDateTime.now(ZoneOffset.UTC));
    entity.setCreatedByUserId(TEST_USER_ID);
    entity.setUpdatedDate(OffsetDateTime.now(ZoneOffset.UTC));
    entity.setUpdatedByUserId(TEST_USER_ID);

    var originalDescriptor = new TimerDescriptor();
    originalDescriptor.setId(TEST_TIMER_ID);
    originalDescriptor.setEnabled(true);
    originalDescriptor.setModuleName(MODULE_NAME);
    originalDescriptor.setRoutingEntry(new RoutingEntry()
      .pathPattern("/test")
      .methods(List.of("POST")));

    entity.setTimerDescriptor(originalDescriptor);

    var result = mapper.toDescriptor(entity);

    assertThat(result).isNotSameAs(originalDescriptor);
    assertThat(result.getRoutingEntry()).isNotSameAs(originalDescriptor.getRoutingEntry());
    assertThat(result.getMetadata()).isNotNull();

    result.setEnabled(false);
    result.getRoutingEntry().setPathPattern("/modified");

    assertThat(originalDescriptor.getEnabled()).isTrue();
    assertThat(originalDescriptor.getRoutingEntry().getPathPattern()).isEqualTo("/test");
  }

  @Test
  void toDescriptor_positive_metadataIsIndependentCopy() {
    var entity = new org.folio.scheduler.domain.entity.TimerDescriptorEntity();
    entity.setId(TEST_TIMER_ID);

    var createdDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
    var updatedDate = OffsetDateTime.now(ZoneOffset.UTC);

    entity.setCreatedDate(createdDate);
    entity.setCreatedByUserId(TEST_USER_ID);
    entity.setUpdatedDate(updatedDate);
    entity.setUpdatedByUserId(TEST_USER_ID);

    var descriptor = new TimerDescriptor();
    descriptor.setId(TEST_TIMER_ID);
    descriptor.setEnabled(true);
    descriptor.setModuleName(MODULE_NAME);
    descriptor.setRoutingEntry(new RoutingEntry()
      .pathPattern("/test")
      .methods(List.of("POST")));

    entity.setTimerDescriptor(descriptor);

    var result = mapper.toDescriptor(entity);

    assertThat(result.getMetadata()).isNotNull();
    assertThat(result.getMetadata().getCreatedDate()).isNotNull();
    assertThat(OffsetDateTime.ofInstant(result.getMetadata().getCreatedDate().toInstant(), ZoneOffset.UTC))
      .isCloseTo(createdDate, within(1, ChronoUnit.MILLIS));

    var newDate = Date.from(OffsetDateTime.now(ZoneOffset.UTC).plusDays(1).toInstant());
    result.getMetadata().setCreatedDate(newDate);

    assertThat(entity.getCreatedDate()).isEqualTo(createdDate);
    assertThat(result.getMetadata().getCreatedDate()).isEqualTo(newDate);
  }
}
