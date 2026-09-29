package org.folio.scheduler.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.repository.TenantModuleVersionRepository;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.FolioModuleMetadata;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.stereotype.Service;

/**
 * Records which module version initialized a tenant and tells whether the tenant is entitled to the running version.
 */
@Log4j2
@Service
public class TenantModuleVersionService {

  static final String TIMER_TABLE = "timer";
  static final String MARKER_TABLE = "tenant_module_version";

  private final TenantModuleVersionRepository repository;
  private final FolioExecutionContext context;
  private final FolioModuleMetadata moduleMetadata;
  private final String moduleId;
  @SuppressWarnings("java:S3749") // thread-safe set, intentionally local to each instance of the module
  private final Set<String> tenantsWithMarkerTable = ConcurrentHashMap.newKeySet();

  public TenantModuleVersionService(TenantModuleVersionRepository repository, FolioExecutionContext context,
    FolioModuleMetadata moduleMetadata) {
    this.repository = repository;
    this.context = context;
    this.moduleMetadata = moduleMetadata;
    this.moduleId = moduleMetadata.getModuleId();
  }

  /**
   * Records the running module version as the owner of the current tenant.
   */
  public void markCurrentVersion() {
    repository.upsert(moduleId);
    log.info("Tenant is now owned by module version: tenant = {}, moduleId = {}", context.getTenantId(), moduleId);
  }

  /**
   * Removes the ownership record of the current tenant.
   */
  public void clear() {
    var tenant = context.getTenantId();
    tenantsWithMarkerTable.remove(tenant);
    if (isTableMissing(tenant, MARKER_TABLE)) {
      log.debug("Nothing to clear, tenant ownership record does not exist: tenant = {}", tenant);
      return;
    }

    repository.deleteOwner();
    log.info("Tenant ownership record has been cleared: tenant = {}", tenant);
  }

  /**
   * Tells whether the current tenant is entitled to another version of the module.
   *
   * <p>A tenant that is not initialized yet, or whose record is not written yet, is not owned by another version.
   * Handling such a tenant is left to the listeners.</p>
   *
   * @return {@code true} if another version initialized the tenant, {@code false} otherwise
   */
  public boolean isOwnedByAnotherVersion() {
    var tenant = context.getTenantId();
    if (tenantsWithMarkerTable.contains(tenant)) {
      try {
        return isOwnerAnotherVersion(tenant);
      } catch (InvalidDataAccessResourceUsageException e) {
        log.debug("Tenant ownership table does not exist anymore: tenant = {}", tenant, e);
        tenantsWithMarkerTable.remove(tenant);
      }
    }

    return resolveOwnership(tenant);
  }

  private boolean resolveOwnership(String tenant) {
    if (isTableMissing(tenant, TIMER_TABLE)) {
      log.debug("Tenant is not initialized yet, no other version owns it: tenant = {}", tenant);
      return false;
    }

    if (isTableMissing(tenant, MARKER_TABLE)) {
      log.debug("Tenant schema predates the ownership record, owned by an older module version: tenant = {}", tenant);
      return true;
    }

    tenantsWithMarkerTable.add(tenant);
    return isOwnerAnotherVersion(tenant);
  }

  private boolean isOwnerAnotherVersion(String tenant) {
    var owner = repository.findModuleId();
    if (owner.isEmpty()) {
      log.debug("Tenant ownership record is missing, no other version owns it: tenant = {}", tenant);
      return false;
    }

    log.debug("Tenant ownership check: tenant = {}, ownerModuleId = {}, currentModuleId = {}",
      tenant, owner.get(), moduleId);
    return !moduleId.equals(owner.get());
  }

  private boolean isTableMissing(String tenant, String table) {
    return !repository.tableExists(moduleMetadata.getDBSchemaName(tenant), table);
  }
}
