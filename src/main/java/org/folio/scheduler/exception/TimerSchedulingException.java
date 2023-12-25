package org.folio.scheduler.exception;

public class TimerSchedulingException extends RuntimeException {

  private static final long serialVersionUID = -2935622235046050419L;

  /**
   * Creates exception object if recurring job cannot be scheduled/rescheduled/deleted from scheduler service.
   *
   * @param message - error message as {@link String}
   * @param throwable - error message cause
   */
  public TimerSchedulingException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
