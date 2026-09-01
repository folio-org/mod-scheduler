package org.folio.scheduler.integration.kafka;

import static org.folio.common.configuration.properties.FolioEnvironment.getFolioEnvName;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;

@Log4j2
@RequiredArgsConstructor
public class KafkaEventConfirmationSender implements EventConfirmationSender {

  private final String confirmationTopic;
  private final KafkaTemplate<String, ResourceResultEvent> kafkaTemplate;

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

    kafkaTemplate.send(getKafkaTopic(), key, resultEvent).thenAccept(result ->
      log.info("Confirmation for timer event sent successfully: eventId = {}, tenant ={}, moduleId = {}, status = {}",
        resultEvent.getId(), resultEvent.getTenant(), resultEvent.getModuleId(), resultEvent.getStatus()));
  }

  private String getKafkaTopic() {
    return String.format("%s.%s", getFolioEnvName(), confirmationTopic);
  }
}
