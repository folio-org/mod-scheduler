package org.folio.scheduler.integration.kafka.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.Value;
import org.folio.integration.kafka.model.ResourceEvent;
import org.folio.integration.kafka.model.ResourceEventType;
import org.springframework.context.ApplicationEvent;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class TimerProcessingResultEvent extends ApplicationEvent {

  String resourceEventId;
  String tenant;
  String timerModuleId;
  String resourceName;
  ResourceEventType type;

  public static Success success(ResourceEvent<?> event, String moduleId, Object eventSource) {
    return new Success(eventSource, event.getId(), event.getTenant(), moduleId, event.getResourceName(),
      event.getType());
  }

  public static Failure failure(ResourceEvent<?> event, String moduleId, Object eventSource, Exception exception) {
    return new Failure(eventSource, event.getId(), event.getTenant(), moduleId, event.getResourceName(),
      event.getType(), exception);
  }

  protected TimerProcessingResultEvent(Object source, String resourceEventId, String tenant, String timerModuleId,
    String resourceName, ResourceEventType type) {
    super(source);
    this.resourceEventId = resourceEventId;
    this.tenant = tenant;
    this.timerModuleId = timerModuleId;
    this.resourceName = resourceName;
    this.type = type;
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public static class Success extends TimerProcessingResultEvent {
    public Success(Object source, String resourceEventId, String tenant, String timerModuleId,
      String resourceName, ResourceEventType type) {
      super(source, resourceEventId, tenant, timerModuleId, resourceName, type);
    }
  }

  @Value
  @EqualsAndHashCode(callSuper = true)
  @ToString(callSuper = true)
  public static class Failure extends TimerProcessingResultEvent {

    Exception exception;

    public Failure(Object source, String resourceEventId, String tenant, String timerModuleId,
      String resourceName, ResourceEventType type, Exception exception) {
      super(source, resourceEventId, tenant, timerModuleId, resourceName, type);
      this.exception = exception;
    }
  }
}
