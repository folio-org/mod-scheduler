package org.folio.scheduler.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@UnitTest
@SpringBootTest(classes = {org.folio.scheduler.mapper.TimerDescriptorMapperImpl.class})
class TimerDescriptorMapperTest {

  private static final UUID TEST_TIMER_ID = UUID.fromString("a1b2c3d4-e5f6-4789-a012-3456789abcde");
  private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired
  private TimerDescriptorMapper mapper;

  @Test
  void toDescriptorEntity_positive_ignoresMetadataFromDto() {
    // Arrange
    var metadata = new org.folio.scheduler.domain.dto.Metadata();
    metadata.setCreatedByUserId(TEST_USER_ID);
    metadata.setCreatedDate(OffsetDateTime.now(ZoneOffset.UTC));
    metadata.setUpdatedByUserId(TEST_USER_ID);
    metadata.setUpdatedDate(OffsetDateTime.now(ZoneOffset.UTC));

    var descriptor = new TimerDescriptor();
    descriptor.setId(TEST_TIMER_ID);
    descriptor.setEnabled(true);
    descriptor.setMetadata(metadata);
    descriptor.setRoutingEntry(new RoutingEntry()
      .pathPattern("/test")
      .methods(List.of("POST")));

    // Act
    var entity = mapper.toDescriptorEntity(descriptor);

    // Assert
    assertThat(entity.getCreatedByUserId()).isNull();
    assertThat(entity.getCreatedDate()).isNull();
    assertThat(entity.getUpdatedByUserId()).isNull();
    assertThat(entity.getUpdatedDate()).isNull();
  }

  @Test
  void toDescriptor_positive_mapsAuditFieldsToMetadata() {
    // Arrange
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
    descriptor.setRoutingEntry(new RoutingEntry()
      .pathPattern("/test")
      .methods(List.of("POST")));

    entity.setTimerDescriptor(descriptor);

    // Act
    var result = mapper.toDescriptor(entity);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.getMetadata()).isNotNull();
    assertThat(result.getMetadata().getCreatedDate()).isEqualTo(createdDate);
    assertThat(result.getMetadata().getCreatedByUserId()).isEqualTo(TEST_USER_ID);
    assertThat(result.getMetadata().getUpdatedDate()).isEqualTo(updatedDate);
    assertThat(result.getMetadata().getUpdatedByUserId()).isEqualTo(TEST_USER_ID);
  }
}
