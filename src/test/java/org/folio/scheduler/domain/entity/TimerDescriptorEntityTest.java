package org.folio.scheduler.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class TimerDescriptorEntityTest {

  private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Test
  void timerDescriptorEntity_positive_extendsAuditable() {
    // Act & Assert
    assertThat(TimerDescriptorEntity.class.getSuperclass())
      .isEqualTo(Auditable.class);
  }

  @Test
  void auditFields_positive_accessibleViaGettersAndSetters() {
    // Arrange
    var entity = new TimerDescriptorEntity();
    var timestamp = OffsetDateTime.now(ZoneOffset.UTC);

    // Act
    entity.setCreatedByUserId(TEST_USER_ID);
    entity.setCreatedDate(timestamp);
    entity.setUpdatedByUserId(TEST_USER_ID);
    entity.setUpdatedDate(timestamp);

    // Assert
    assertThat(entity.getCreatedByUserId()).isEqualTo(TEST_USER_ID);
    assertThat(entity.getCreatedDate()).isEqualTo(timestamp);
    assertThat(entity.getUpdatedByUserId()).isEqualTo(TEST_USER_ID);
    assertThat(entity.getUpdatedDate()).isEqualTo(timestamp);
  }

  @Test
  void auditFields_positive_userIdsAreNullByDefault() {
    // Arrange & Act
    var entity = new TimerDescriptorEntity();

    // Assert
    assertThat(entity.getCreatedByUserId()).isNull();
    assertThat(entity.getUpdatedByUserId()).isNull();
  }
}
