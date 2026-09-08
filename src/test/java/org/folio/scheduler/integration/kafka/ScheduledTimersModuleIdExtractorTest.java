package org.folio.scheduler.integration.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import org.folio.integration.kafka.model.ResourceEvent;
import org.folio.scheduler.integration.kafka.model.ScheduledTimers;
import org.folio.scheduler.utils.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ScheduledTimersModuleIdExtractorTest {

  private static final String EVENT_ID = "5f26fe20-d7bc-11ef-9cd2-0242ac120002";
  private static final String MODULE_ID = "mod-foo-1.0.0";

  @Spy private ObjectMapper objectMapper = TestUtils.OBJECT_MAPPER;
  @InjectMocks private ScheduledTimersModuleIdExtractor extractor;

  @Test
  void apply_positive_extractsModuleIdFromNewValue() {
    var event = ResourceEvent.<ScheduledTimers>baseBuilder()
      .id(EVENT_ID)
      .newValue(new ScheduledTimers().moduleId(MODULE_ID))
      .build();

    var result = extractor.apply(event);

    assertThat(result).isEqualTo(MODULE_ID);
  }

  @Test
  void apply_positive_extractsModuleIdFromOldValue_whenNewValueIsNull() {
    var event = ResourceEvent.<ScheduledTimers>baseBuilder()
      .id(EVENT_ID)
      .oldValue(new ScheduledTimers().moduleId(MODULE_ID))
      .build();

    var result = extractor.apply(event);

    assertThat(result).isEqualTo(MODULE_ID);
  }

  @Test
  void apply_negative_returnsNull_whenConvertValueFails() {
    doThrow(new IllegalArgumentException("conversion failed"))
      .when(objectMapper).convertValue(any(), any(Class.class));
    var event = ResourceEvent.<ScheduledTimers>baseBuilder()
      .id(EVENT_ID)
      .newValue(new ScheduledTimers())
      .build();

    var result = extractor.apply(event);

    assertThat(result).isNull();
  }
}
