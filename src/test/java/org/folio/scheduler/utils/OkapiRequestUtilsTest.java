package org.folio.scheduler.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;
import org.folio.scheduler.domain.dto.RoutingEntry;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class OkapiRequestUtilsTest {

  @Nested
  @DisplayName("getStaticPath")
  class GetStaticPath {

    @MethodSource("getStaticPathDataProvider")
    @ParameterizedTest(name = "[{index}] {0}")
    @DisplayName("positive_parameterized")
    void positive_parameterized(@SuppressWarnings("unused") String name, RoutingEntry routingEntry, String expected) {
      var result = OkapiRequestUtils.getStaticPath(routingEntry);
      assertThat(result).isEqualTo(expected);
    }

    private static Stream<Arguments> getStaticPathDataProvider() {
      return Stream.of(
        arguments("routing entry with pathPattern starting with /",
          new RoutingEntry().pathPattern("/entities"), "/entities"),
        arguments("routing entry with pathPattern not starting with /",
          new RoutingEntry().pathPattern("entities"), "/entities"),
        arguments("routing entry with path starting with /", new RoutingEntry().path("/entities"), "/entities"),
        arguments("routing entry with path not starting with /", new RoutingEntry().path("entities"), "/entities")
      );
    }
  }
}
