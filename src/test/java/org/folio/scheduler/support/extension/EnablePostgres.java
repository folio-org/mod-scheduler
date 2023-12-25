package org.folio.scheduler.support.extension;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.folio.scheduler.support.extension.impl.PostgresContainerExtension;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Annotation that can be specified on an integration test class that runs with PostgreSQL database.
 *
 * <p>
 * Provides PostgreSQL database with necessary datasource configuration.
 * </p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(PostgresContainerExtension.class)
public @interface EnablePostgres {}
