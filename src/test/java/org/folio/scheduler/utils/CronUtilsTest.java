package org.folio.scheduler.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class CronUtilsTest {

  @ParameterizedTest(name = "[{index}] «{0}» → «{1}»")
  @MethodSource("validConversions")
  void convertToQuartz_positive(String input, String expected) {
    assertEquals(expected, CronUtils.convertToQuartz(input));
  }

  @ParameterizedTest(name = "[{index}] invalid «{0}»")
  @MethodSource("invalidInputs")
  void convertToQuartz_negative(String invalidExpression) {
    assertThrows(IllegalArgumentException.class, () -> CronUtils.convertToQuartz(invalidExpression));
  }

  @ParameterizedTest(name = "[{index}] null → NPE")
  @MethodSource("provideNull")
  void convertToQuartz_negative_npe(String ignored) {
    assertThrows(NullPointerException.class, () -> CronUtils.convertToQuartz(null));
  }

  static Stream<Arguments> provideNull() {
    return Stream.of(Arguments.of((String) null));
  }

  static Stream<Arguments> invalidInputs() {
    return Stream.of(
      Arguments.of(""),
      Arguments.of("   "),
      Arguments.of("* * *"),
      Arguments.of("a b c d e f g h"),
      Arguments.of("*/1 * * *")
    );
  }

  static Stream<Arguments> validConversions() {
    return Stream.of(
      // UNIX → Quartz
      Arguments.of("* * * * *", "0 * * * * ? *"),
      Arguments.of("*/5 * * * *", "0 */5 * * * ? *"),
      Arguments.of("0 * * * *", "0 0 * * * ? *"),
      Arguments.of("30 15 * * MON-FRI", "0 30 15 ? * 2-6 *"),
      Arguments.of("0 12 1 1 *", "0 0 12 1 1 ? *"),

      // Quartz-6
      Arguments.of("0 0 12 * * ?", "0 0 12 * * ?"),
      Arguments.of("5 15 10 * * MON-FRI", "5 15 10 * * MON-FRI"),

      // Quartz-7
      Arguments.of("0 0 12 * * ? 2025", "0 0 12 * * ? 2025"),
      Arguments.of("30 0 0 1 1 ? 2025/2", "30 0 0 1 1 ? 2025/2")
    );
  }
}
