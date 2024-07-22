package org.folio.scheduler.configuration;

import lombok.RequiredArgsConstructor;
import org.folio.spring.liquibase.FolioSpringLiquibase;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuartzSchemaInitializer implements InitializingBean {

  private final LiquibaseProperties liquibaseProperties;
  private final FolioSpringLiquibase folioSpringLiquibase;

  /**
   * Performs database update using {@link FolioSpringLiquibase} and then return previous configuration for this bean.
   *
   * <p>Usage of bean is </p>
   *
   * @throws Exception - if liquibase update failed.
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    folioSpringLiquibase.setChangeLog("classpath:changelog/changelog-quartz.xml");
    folioSpringLiquibase.setDefaultSchema("sys_quartz_mod_scheduler");

    folioSpringLiquibase.performLiquibaseUpdate();

    folioSpringLiquibase.setChangeLog(liquibaseProperties.getChangeLog());
    folioSpringLiquibase.setDefaultSchema(liquibaseProperties.getDefaultSchema());
  }
}
