package org.folio.scheduler.configuration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;

@UnitTest
@ExtendWith(MockitoExtension.class)
class QuartzSchemaInitializerTest {

  @InjectMocks private QuartzSchemaInitializer quartzSchemaInitializer;
  @Mock private FolioSpringLiquibase folioSpringLiquibase;
  @Mock private LiquibaseProperties liquibaseProperties;

  @Test
  void afterPropertiesSet_positive() throws Exception {
    when(liquibaseProperties.getChangeLog()).thenReturn("changelog.xml");
    when(liquibaseProperties.getDefaultSchema()).thenReturn("public");

    quartzSchemaInitializer.afterPropertiesSet();

    verify(folioSpringLiquibase).performLiquibaseUpdate();
    verify(folioSpringLiquibase).setDefaultSchema("sys_quartz_mod_scheduler");
    verify(folioSpringLiquibase).setChangeLog("classpath:changelog/changelog-quartz.xml");
    verify(folioSpringLiquibase).setDefaultSchema("public");
    verify(folioSpringLiquibase).setChangeLog("changelog.xml");
  }
}
