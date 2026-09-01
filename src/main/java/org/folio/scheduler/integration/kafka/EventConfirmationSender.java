package org.folio.scheduler.integration.kafka;

import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.integration.kafka.model.TimerProcessingResultEvent;

public interface EventConfirmationSender {

  void onTimerSuccessEvent(TimerProcessingResultEvent.Success event);

  void onTimerFailureEvent(TimerProcessingResultEvent.Failure event);

  @Log4j2
  class NoOp implements EventConfirmationSender {
    @Override
    public void onTimerSuccessEvent(TimerProcessingResultEvent.Success event) {
      debug(event);
    }

    @Override
    public void onTimerFailureEvent(TimerProcessingResultEvent.Failure event) {
      debug(event);
    }

    private void debug(TimerProcessingResultEvent event) {
      log.debug("Event confirmation is disabled. Skipping sending confirmation for timer event: "
          + "eventId = {}, tenant ={}, moduleId = {}",
        event.getResourceEventId(), event.getTenant(), event.getTimerModuleId());
    }
  }
}
