package org.folio.scheduler.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FolioLiquibaseConfigurationValueProviderTest {

  private FolioLiquibaseConfigurationValueProvider unit = new FolioLiquibaseConfigurationValueProvider();

  @Test
  void testEmptyKeys() {
    assertThat(unit.getProvidedValue(null)).isNull();
    assertThat(unit.getProvidedValue(new String[0])).isNull();
  }
}
