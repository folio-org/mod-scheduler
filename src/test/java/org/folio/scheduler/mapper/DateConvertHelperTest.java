package org.folio.scheduler.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class DateConvertHelperTest {

  private final DateConvertHelper helper = new DateConvertHelper();

  @Test
  void offsetDateTimeAsDate_positive_convertsToDate() {
    var offsetDateTime = OffsetDateTime.of(2025, 2, 4, 12, 30, 45, 123000000, ZoneOffset.UTC);

    var result = helper.offsetDateTimeAsDate(offsetDateTime);

    assertThat(result).isNotNull();
    assertThat(result.getTime()).isEqualTo(offsetDateTime.toInstant().toEpochMilli());
  }

  @Test
  void offsetDateTimeAsDate_positive_handlesNull() {
    var result = helper.offsetDateTimeAsDate(null);

    assertThat(result).isNull();
  }

  @Test
  void dateAsOffsetDateTime_positive_convertsToOffsetDateTime() {
    var date = new Date(1707049845123L);

    var result = helper.dateAsOffsetDateTime(date);

    assertThat(result).isNotNull();
    assertThat(result.toInstant().toEpochMilli()).isEqualTo(date.getTime());
    assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
  }

  @Test
  void dateAsOffsetDateTime_positive_handlesNull() {
    var result = helper.dateAsOffsetDateTime(null);

    assertThat(result).isNull();
  }

  @Test
  void dateConversion_positive_roundTripPreservesValue() {
    var originalOffsetDateTime = OffsetDateTime.of(2025, 2, 4, 12, 30, 45, 123000000, ZoneOffset.UTC);

    var date = helper.offsetDateTimeAsDate(originalOffsetDateTime);
    var resultOffsetDateTime = helper.dateAsOffsetDateTime(date);

    assertThat(resultOffsetDateTime).isNotNull();
    assertThat(resultOffsetDateTime.toInstant()).isEqualTo(originalOffsetDateTime.toInstant());
  }

  @Test
  void dateConversion_positive_usesUtcTimeZone() {
    var date = new Date();

    var result = helper.dateAsOffsetDateTime(date);

    assertThat(result).isNotNull();
    assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
  }
}
