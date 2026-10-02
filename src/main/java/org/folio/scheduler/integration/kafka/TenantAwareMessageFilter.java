package org.folio.scheduler.integration.kafka;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.trimToNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.folio.scheduler.service.TenantModuleVersionService;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.scope.FolioExecutionContextSetter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.stereotype.Component;

/**
 * Discards kafka messages for tenants that are entitled to another version of this module, so that in a mixed-version
 * deployment each version processes only its own tenants.
 *
 * <p>Applied only when {@code folio.kafka.tenant-filter.enabled} is {@code true}; otherwise every record is
 * accepted.</p>
 *
 * <p>The tenant is taken from the {@code x-okapi-tenant} header, falling back to the {@code folio.tenantId} header.
 * A record is discarded only when another version is known to own the tenant; a tenant not initialized
 * yet is left to the listeners, which retry or ignore.</p>
 */
@Log4j2
@Component
public class TenantAwareMessageFilter implements RecordFilterStrategy<String, Object> {

  private static final String FOLIO_TENANT_ID_HEADER = "folio.tenantId";

  private final FolioModuleMetadata folioModuleMetadata;
  private final TenantModuleVersionService tenantModuleVersionService;
  private final boolean enabled;

  public TenantAwareMessageFilter(FolioModuleMetadata folioModuleMetadata,
    TenantModuleVersionService tenantModuleVersionService,
    @Value("${folio.kafka.tenant-filter.enabled:false}") boolean enabled) {
    this.folioModuleMetadata = folioModuleMetadata;
    this.tenantModuleVersionService = tenantModuleVersionService;
    this.enabled = enabled;
    log.info("Kafka tenant ownership filter {}", enabled ? "enabled" : "disabled");
  }

  @Override
  public boolean filter(ConsumerRecord<String, Object> consumerRecord) {
    if (!enabled) {
      return false;
    }

    var tenant = resolveTenant(consumerRecord);
    if (tenant == null) {
      log.warn("Missing or blank tenant header, ownership filter is not applied: topic = {}, key = {}, offset = {}",
        consumerRecord.topic(), consumerRecord.key(), consumerRecord.offset());
      return false;
    }

    if (isOwnedByAnotherVersion(tenant)) {
      log.debug("Skipping event: tenant {} is owned by another module version: topic = {}, key = {}, offset = {}",
        tenant, consumerRecord.topic(), consumerRecord.key(), consumerRecord.offset());
      return true;
    }

    log.debug("Accepting event: tenant {} is not owned by another module version: topic = {}, key = {}, offset = {}",
      tenant, consumerRecord.topic(), consumerRecord.key(), consumerRecord.offset());
    return false;
  }

  private boolean isOwnedByAnotherVersion(String tenant) {
    try (var ignored = new FolioExecutionContextSetter(folioModuleMetadata,
      prepareContextHeaders(tenant))) {
      return tenantModuleVersionService.isOwnedByAnotherVersion();
    }
  }

  static String resolveTenant(ConsumerRecord<?, ?> consumerRecord) {
    var tenant = findHeaderValue(consumerRecord, XOkapiHeaders.TENANT);
    if (tenant == null) {
      tenant = findHeaderValue(consumerRecord, FOLIO_TENANT_ID_HEADER);
    }

    return tenant;
  }

  private static String findHeaderValue(ConsumerRecord<?, ?> consumerRecord, String headerName) {
    for (Header header : consumerRecord.headers()) {
      if (headerName.equalsIgnoreCase(header.key()) && header.value() != null) {
        var value = trimToNull(new String(header.value(), UTF_8));
        if (value != null) {
          return value;
        }
      }
    }

    return null;
  }

  private static Map<String, Collection<String>> prepareContextHeaders(String tenant) {
    var headers = new HashMap<String, Collection<String>>();
    headers.put(XOkapiHeaders.TENANT, singletonList(tenant));
    return headers;
  }
}
