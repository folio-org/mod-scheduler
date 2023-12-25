package org.folio.scheduler.exception;

import static org.folio.scheduler.domain.dto.ErrorCode.VALIDATION_ERROR;

import lombok.Getter;
import org.folio.scheduler.domain.dto.ErrorCode;
import org.folio.scheduler.domain.dto.Parameter;

@Getter
public class RequestValidationException extends RuntimeException {

  private static final long serialVersionUID = 8992989651666737929L;

  private final Parameter errorParameter;
  private final ErrorCode errorCode;

  /**
   * Creates {@link RequestValidationException} object for given message, key and value.
   *
   * @param message - validation error message
   * @param key - validation key as field or parameter name
   * @param value - invalid parameter value
   */
  public RequestValidationException(String message, String key, String value) {
    super(message);

    this.errorCode = VALIDATION_ERROR;
    this.errorParameter = new Parameter().key(key).value(value);
  }

  /**
   * Creates {@link RequestValidationException} object for given message, key and value.
   *
   * @param message - validation error message
   */
  public RequestValidationException(String message) {
    super(message);
    this.errorCode = VALIDATION_ERROR;
    this.errorParameter = null;
  }
}
