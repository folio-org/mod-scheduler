package org.folio.scheduler.integration.kafka;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaConstants {

  public static final String SCHEDULED_JOB_LISTENER_ID = "mod-scheduler-job-listener";
}
