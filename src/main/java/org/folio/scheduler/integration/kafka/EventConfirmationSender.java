package org.folio.scheduler.integration.kafka;

import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import lombok.extern.log4j.Log4j2;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.event.TransactionalEventListener;

public interface EventConfirmationSender {

  @TransactionalEventListener(
    condition = "#event.status == T(org.folio.integration.kafka.model.ResourceResultStatus).SUCCESS",
    phase = AFTER_COMMIT)
  void onSuccessfulResourceResult(ResourceResultEvent event);

  @EventListener(condition = "#event.status == T(org.folio.integration.kafka.model.ResourceResultStatus).FAILURE")
  void onFailedResourceResult(ResourceResultEvent event);

  @Log4j2
  class NoOp implements EventConfirmationSender {

    @Override
    public void onSuccessfulResourceResult(ResourceResultEvent event) {
      debug(event);
    }

    @Override
    public void onFailedResourceResult(ResourceResultEvent event) {
      debug(event);
    }

    private void debug(ResourceResultEvent event) {
      log.debug("Event confirmation is disabled. Skipping sending confirmation for resource result event: "
          + "eventId = {}, tenant ={}, moduleId = {}",
        event.getId(), event.getTenant(), event.getModuleId());
    }
  }
}
