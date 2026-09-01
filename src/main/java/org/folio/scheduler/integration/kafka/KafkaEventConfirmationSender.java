package org.folio.scheduler.integration.kafka;

import static org.folio.common.configuration.properties.FolioEnvironment.getFolioEnvName;

import lombok.extern.log4j.Log4j2;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;

@Log4j2
public class KafkaEventConfirmationSender implements EventConfirmationSender {

  private final String confirmationTopic;
  private final KafkaTemplate<String, ResourceResultEvent> kafkaTemplate;

  public KafkaEventConfirmationSender(String confirmationTopic,
    KafkaTemplate<String, ResourceResultEvent> kafkaTemplate) {
    this.confirmationTopic = String.format("%s.%s", getFolioEnvName(), confirmationTopic);
    this.kafkaTemplate = kafkaTemplate;
  }

  @Async
  @Override
  public void onSuccessfulResourceResult(ResourceResultEvent event) {
    send(event);
  }

  @Async
  @Override
  public void onFailedResourceResult(ResourceResultEvent event) {
    send(event);
  }

  private void send(ResourceResultEvent resultEvent) {
    var key = resultEvent.getTenant();

    kafkaTemplate.send(confirmationTopic, key, resultEvent).whenComplete((result, exception) -> {
      if (exception != null) {
        // A lost confirmation leaves the originating entitlement stage IN_PROGRESS until the sender's
        // async-confirmation timeout expires, so the failure must be visible in the log.
        log.error("Failed to send confirmation for timer event: eventId = {}, tenant ={}, moduleId = {}, status = {}",
          resultEvent.getId(), resultEvent.getTenant(), resultEvent.getModuleId(), resultEvent.getStatus(), exception);
        return;
      }

      log.info("Confirmation for timer event sent successfully: eventId = {}, tenant ={}, moduleId = {}, status = {}",
        resultEvent.getId(), resultEvent.getTenant(), resultEvent.getModuleId(), resultEvent.getStatus());
    });
  }
}
