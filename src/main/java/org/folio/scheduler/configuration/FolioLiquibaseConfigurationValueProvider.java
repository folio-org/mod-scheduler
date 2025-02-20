package org.folio.scheduler.configuration;

import static liquibase.sql.SqlConfiguration.ALWAYS_SET_FETCH_SIZE;

import liquibase.configuration.AbstractConfigurationValueProvider;
import liquibase.configuration.ProvidedValue;

public class FolioLiquibaseConfigurationValueProvider extends AbstractConfigurationValueProvider {

  @Override
  public int getPrecedence() {
    return 600;
  }

  @Override
  public ProvidedValue getProvidedValue(String... keyAndAliases) {
    if (keyAndAliases == null || keyAndAliases.length < 1) {
      return null;
    }
    String currentKey = keyAndAliases[0];
    if (ALWAYS_SET_FETCH_SIZE.getKey().equals(currentKey)) {
      return new ProvidedValue(ALWAYS_SET_FETCH_SIZE.getKey(), ALWAYS_SET_FETCH_SIZE.getKey(), "false",
        "DB upgrade overridden Liquibase Configuration", this);
    }
    return null;
  }
}
