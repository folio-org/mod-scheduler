package org.folio.scheduler.migration;

import java.sql.ResultSet;
import java.util.HashSet;
import java.util.function.Consumer;
import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.ValidationErrors;
import liquibase.integration.spring.SpringResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.util.function.ThrowingConsumer;

public abstract class AbstractCustomTaskChangeMigration implements CustomTaskChange {

  protected ApplicationContext springApplicationContext;

  @Override
  public String getConfirmationMessage() {
    return "Completed " + this.getClass().getSimpleName();
  }

  @Override
  public void setUp() {
    // Do nothing
  }

  @Override
  public void setFileOpener(ResourceAccessor resourceAccessor) {
    try {
      var springResourceAccessor = (SpringResourceAccessor) resourceAccessor;
      springApplicationContext =
        (ApplicationContext) FieldUtils.readField(springResourceAccessor, "resourceLoader", true);
    } catch (IllegalAccessException e) {
      throw new RuntimeException("Failed to obtain Spring Application Context", e);
    }
  }

  @Override
  public ValidationErrors validate(Database database) {
    return null;
  }

  protected void runQuery(Database database, String query, ThrowingConsumer<ResultSet> rowConsumer) {
    JdbcConnection connection = (JdbcConnection) database.getConnection();
    try (var statement = connection.getWrappedConnection().prepareStatement(query)) {
      var resultSet = statement.executeQuery();
      while (resultSet.next()) {
        rowConsumer.accept(resultSet);
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute migration " + this.getClass().getSimpleName(), e);
    }
  }
}
