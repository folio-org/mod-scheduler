package org.folio.scheduler.integration.kafka.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ResourceEvent {

  /**
   * Resource identifier.
   */
  private String id;

  /**
   * Event type.
   */
  private ResourceEventType type;

  /**
   * Tenant identifier (name).
   */
  private String tenant;

  /**
   * Name of resource.
   */
  private String resourceName;

  /**
   * New value (if resource is created or updated).
   */
  @JsonProperty("new")
  private Object newValue;

  /**
   * Previous version value (if resource was updated or deleted).
   */
  @JsonProperty("old")
  private Object oldValue;

  /**
   * Sets the id and returns updated {@link ResourceEvent} object.
   *
   * @param id - resource event identifier
   * @return updated {@link ResourceEvent} object
   */
  public ResourceEvent id(String id) {
    this.id = id;
    return this;
  }

  /**
   * Sets the type and returns updated {@link ResourceEvent} object.
   *
   * @param type - resource event type
   * @return updated {@link ResourceEvent} object
   */
  public ResourceEvent type(ResourceEventType type) {
    this.type = type;
    return this;
  }

  /**
   * Sets the tenant name and returns updated {@link ResourceEvent} object.
   *
   * @param tenant - tenant name
   * @return updated {@link ResourceEvent} object
   */
  public ResourceEvent tenant(String tenant) {
    this.tenant = tenant;
    return this;
  }

  /**
   * Sets the resource name and returns updated {@link ResourceEvent} object.
   *
   * @param resourceName - resource name
   * @return updated {@link ResourceEvent} object
   */
  public ResourceEvent resourceName(String resourceName) {
    this.resourceName = resourceName;
    return this;
  }

  /**
   * Sets the new version value and returns updated {@link ResourceEvent} object.
   *
   * @param newValue - resource event new value.
   * @return updated {@link ResourceEvent} object
   */
  public ResourceEvent newValue(Object newValue) {
    this.newValue = newValue;
    return this;
  }

  /**
   * Sets the previous version value and returns updated {@link ResourceEvent} object.
   *
   * @param oldValue - resource event previous version value
   * @return updated {@link ResourceEvent} object
   */
  public ResourceEvent oldValue(Object oldValue) {
    this.oldValue = oldValue;
    return this;
  }
}
