package org.folio.scheduler.utils;

import static org.folio.scheduler.support.TestConstants.MODULE_ID;
import static org.folio.scheduler.support.TestConstants.MODULE_NAME;
import static org.folio.scheduler.utils.TimerDescriptorUtils.evalModuleName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.of;

import java.util.stream.Stream;
import org.folio.scheduler.domain.dto.TimerDescriptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TimerDescriptorUtilsTest {

  @ParameterizedTest
  @MethodSource("evalModuleNameArguments")
  void testEvalModuleName(String moduleId, String moduleName, String expected) {
    var td = new TimerDescriptor()
      .moduleId(moduleId).moduleName(moduleName);

    String result = evalModuleName(td);

    assertEquals(expected, result);
  }

  static Stream<Arguments> evalModuleNameArguments() {
    return Stream.of(
      of(MODULE_ID, MODULE_NAME, MODULE_NAME),
      of(MODULE_ID, null, MODULE_NAME),
      of(null, MODULE_NAME, MODULE_NAME),
      of(null, null, null)
    );
  }
}
