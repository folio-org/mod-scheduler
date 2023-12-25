package org.folio.scheduler.service;

import static org.mockito.Mockito.verify;

import org.folio.scheduler.integration.kafka.KafkaAdminService;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class SchedulerTenantServiceTest {

  @InjectMocks private SchedulerTenantService schedulerTenantService;
  @Mock private KafkaAdminService kafkaAdminService;
  @Mock private SchedulerTimerService schedulerTimerService;

  @Test
  void afterTenantUpdate_positive() {
    var tenantAttributes = new TenantAttributes().moduleTo("mod-scheduler");
    schedulerTenantService.afterTenantUpdate(tenantAttributes);

    verify(kafkaAdminService).restartEventListeners();
  }

  @Test
  void beforeTenantDeletion_positive() {
    var tenantAttributes = new TenantAttributes().moduleTo("mod-scheduler");
    schedulerTenantService.beforeTenantDeletion(tenantAttributes);
    verify(schedulerTimerService).deleteAll();
  }
}
