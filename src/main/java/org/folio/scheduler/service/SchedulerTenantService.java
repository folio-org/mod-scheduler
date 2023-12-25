package org.folio.scheduler.service;

import lombok.extern.log4j.Log4j2;
import org.folio.scheduler.integration.kafka.KafkaAdminService;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.spring.service.TenantService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Log4j2
@Primary
@Service
public class SchedulerTenantService extends TenantService {

  private final KafkaAdminService kafkaAdminService;
  private final SchedulerTimerService schedulerTimerService;

  public SchedulerTenantService(JdbcTemplate jdbcTemplate, FolioExecutionContext context,
    FolioSpringLiquibase folioSpringLiquibase, KafkaAdminService kafkaAdminService,
    SchedulerTimerService schedulerTimerService) {
    super(jdbcTemplate, context, folioSpringLiquibase);
    this.kafkaAdminService = kafkaAdminService;
    this.schedulerTimerService = schedulerTimerService;
  }

  @Override
  protected void afterTenantUpdate(TenantAttributes tenantAttributes) {
    kafkaAdminService.restartEventListeners();
    log.info("Tenant init has been completed");
  }

  @Override
  protected void beforeTenantDeletion(TenantAttributes tenantAttributes) {
    schedulerTimerService.deleteAll();
    log.info("Tenant scheduled timers have been deleted");
  }
}
