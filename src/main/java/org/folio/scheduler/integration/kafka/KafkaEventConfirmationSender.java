package org.folio.scheduler.integration.kafka;

import static org.folio.common.configuration.properties.FolioEnvironment.getFolioEnvName;
import static org.folio.integration.kafka.model.ResourceResultStatus.FAILURE;
import static org.folio.integration.kafka.model.ResourceResultStatus.SUCCESS;
import static org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.scheduler.integration.kafka.model.TimerProcessingResultEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

@Log4j2
@RequiredArgsConstructor
public class KafkaEventConfirmationSender implements EventConfirmationSender {

  private final String confirmationTopic;
  private final KafkaTemplate<String, ResourceResultEvent> kafkaTemplate;

  @Async
  @Override
  @TransactionalEventListener(phase = AFTER_COMMIT)
  public void onTimerSuccessEvent(TimerProcessingResultEvent.Success event) {
    var resultEvent = new ResourceResultEvent(event.getResourceEventId(), event.getTenant(),
      event.getTimerModuleId(), event.getResourceName(), SUCCESS, null);

    send(resultEvent);
  }

  @Async
  @Override
  public void onTimerFailureEvent(TimerProcessingResultEvent.Failure event) {
    var resultEvent = new ResourceResultEvent(event.getResourceEventId(), event.getTenant(),
      event.getTimerModuleId(), event.getResourceName(), FAILURE,
      event.getException() != null ? event.getException().getMessage() : null);

    send(resultEvent);
  }

  private void send(ResourceResultEvent resultEvent) {
    var key = resultEvent.getTenant();

    kafkaTemplate.send(getKafkaTopic(), key, resultEvent).thenAccept(result ->
      log.info("Confirmation for timer event sent successfully: eventId = {}, tenant ={}, moduleId = {}, status = {}",
        resultEvent.getId(), resultEvent.getTenant(), resultEvent.getModuleId(), resultEvent.getStatus()));
  }

  private String getKafkaTopic() {
    return String.format("%s.%s", getFolioEnvName(), confirmationTopic);
  }
}
