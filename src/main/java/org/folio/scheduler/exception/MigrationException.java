package org.folio.scheduler.exception;

public class MigrationException extends RuntimeException {

  public MigrationException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
