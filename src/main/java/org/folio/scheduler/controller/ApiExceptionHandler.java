package org.folio.scheduler.controller;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.WARN;
import static org.folio.scheduler.domain.dto.ErrorCode.FOUND_ERROR;
import static org.folio.scheduler.domain.dto.ErrorCode.NOT_FOUND_ERROR;
import static org.folio.scheduler.domain.dto.ErrorCode.SERVICE_ERROR;
import static org.folio.scheduler.domain.dto.ErrorCode.UNKNOWN_ERROR;
import static org.folio.scheduler.domain.dto.ErrorCode.VALIDATION_ERROR;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.folio.cql2pgjson.exception.CQLFeatureUnsupportedException;
import org.folio.scheduler.domain.dto.Error;
import org.folio.scheduler.domain.dto.ErrorCode;
import org.folio.scheduler.domain.dto.ErrorResponse;
import org.folio.scheduler.domain.dto.Parameter;
import org.folio.scheduler.exception.RequestValidationException;
import org.folio.scheduler.exception.TimerSchedulingException;
import org.folio.spring.cql.CqlQueryValidationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Log4j2
@RestControllerAdvice
public class ApiExceptionHandler {

  /**
   * Catches and handles all exceptions for type {@link UnsupportedOperationException}.
   *
   * @param exception {@link UnsupportedOperationException} to process
   * @return {@link org.springframework.http.ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<ErrorResponse> handleUnsupportedOperationException(UnsupportedOperationException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, NOT_IMPLEMENTED, SERVICE_ERROR);
  }

  /**
   * Catches and handles all exceptions for type {@link org.springframework.web.bind.MethodArgumentNotValidException}.
   *
   * @param e {@link org.springframework.web.bind.MethodArgumentNotValidException} to process
   * @return {@link org.springframework.http.ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
    var validationErrors = Optional.of(e.getBindingResult()).map(Errors::getAllErrors).orElse(emptyList());
    var errorResponse = new ErrorResponse();
    validationErrors.forEach(error ->
      errorResponse.addErrorsItem(new Error()
        .message(error.getDefaultMessage())
        .code(ErrorCode.VALIDATION_ERROR)
        .type(MethodArgumentNotValidException.class.getSimpleName())
        .addParametersItem(new Parameter()
          .key(((FieldError) error).getField())
          .value(String.valueOf(((FieldError) error).getRejectedValue())))));
    errorResponse.totalRecords(errorResponse.getErrors().size());

    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles all exceptions for type {@link javax.validation.ConstraintViolationException}.
   *
   * @param exception {@link javax.validation.ConstraintViolationException} to process
   * @return {@link org.springframework.http.ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException exception) {
    logException(DEBUG, exception);
    var errorResponse = new ErrorResponse();
    exception.getConstraintViolations().forEach(constraintViolation ->
      errorResponse.addErrorsItem(new Error()
        .message(String.format("%s %s", constraintViolation.getPropertyPath(), constraintViolation.getMessage()))
        .code(VALIDATION_ERROR)
        .type(ConstraintViolationException.class.getSimpleName())));
    errorResponse.totalRecords(errorResponse.getErrors().size());

    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles all exceptions for type {@link RequestValidationException}.
   *
   * @param exception {@link RequestValidationException} to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler(RequestValidationException.class)
  public ResponseEntity<ErrorResponse> handleRequestValidationException(RequestValidationException exception) {
    logException(DEBUG, exception);
    var errorResponse = buildValidationError(exception, exception.getErrorParameter());
    return buildResponseEntity(errorResponse, BAD_REQUEST);
  }

  /**
   * Catches and handles common request validation exceptions.
   *
   * @param exception {@link Exception} object to process
   * @return {@link ResponseEntity} with {@link ErrorResponse} body
   */
  @ExceptionHandler({
    IllegalArgumentException.class,
    CqlQueryValidationException.class,
    MissingRequestHeaderException.class,
    CQLFeatureUnsupportedException.class,
    InvalidDataAccessApiUsageException.class,
    HttpMediaTypeNotSupportedException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class,
  })
  public ResponseEntity<ErrorResponse> handleValidationExceptions(Exception exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, BAD_REQUEST, VALIDATION_ERROR);
  }

  /**
   * Catches and handles all exceptions for type {@link EntityNotFoundException}.
   *
   * @param exception {@link EntityNotFoundException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleEntityNotFoundException(EntityNotFoundException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, NOT_FOUND, NOT_FOUND_ERROR);
  }

  /**
   * Catches and handles all exceptions for type {@link EntityExistsException}.
   *
   * @param exception {@link EntityExistsException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(EntityExistsException.class)
  public ResponseEntity<ErrorResponse> handleEntityExistsException(EntityExistsException exception) {
    logException(DEBUG, exception);
    return buildResponseEntity(exception, BAD_REQUEST, FOUND_ERROR);
  }

  /**
   * Catches and handles all exceptions for type {@link HttpMessageNotReadableException}.
   *
   * @param e {@link HttpMessageNotReadableException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
    return Optional.ofNullable(e.getCause())
      .map(Throwable::getCause)
      .filter(IllegalArgumentException.class::isInstance)
      .map(IllegalArgumentException.class::cast)
      .map(this::handleValidationExceptions)
      .orElseGet(() -> {
        logException(DEBUG, e);
        return buildResponseEntity(e, BAD_REQUEST, VALIDATION_ERROR);
      });
  }

  /**
   * Catches and handles all exceptions for type {@link TimerSchedulingException}.
   *
   * @param exception - {@link TimerSchedulingException} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(TimerSchedulingException.class)
  public ResponseEntity<ErrorResponse> handleTimerSchedulingException(TimerSchedulingException exception) {
    logException(WARN, exception);
    return buildResponseEntity(exception, UNPROCESSABLE_ENTITY, SERVICE_ERROR);
  }

  /**
   * Handles all uncaught exceptions.
   *
   * @param exception {@link Exception} object
   * @return {@link ResponseEntity} with {@link ErrorResponse} body.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleAllOtherExceptions(Exception exception) {
    var errorMessage = exception.getMessage();
    if (errorMessage != null && errorMessage.matches("CronExpression .* is invalid\\.")) {
      logException(DEBUG, exception);
      return buildResponseEntity(exception, BAD_REQUEST, VALIDATION_ERROR);
    }

    logException(WARN, exception);
    return buildResponseEntity(exception, INTERNAL_SERVER_ERROR, UNKNOWN_ERROR);
  }

  private static ErrorResponse buildValidationError(Exception exception, Parameter parameter) {
    var error = new Error()
      .type(exception.getClass().getSimpleName())
      .code(VALIDATION_ERROR)
      .message(exception.getMessage())
      .parameters(parameter != null ? singletonList(parameter) : null);
    return new ErrorResponse().errors(List.of(error)).totalRecords(1);
  }

  private static ResponseEntity<ErrorResponse> buildResponseEntity(
    Exception exception, HttpStatus status, ErrorCode code) {

    var errorResponse = new ErrorResponse()
      .errors(List.of(new Error()
        .message(exception.getMessage())
        .type(exception.getClass().getSimpleName())
        .code(code)))
      .totalRecords(1);

    return buildResponseEntity(errorResponse, status);
  }

  private static ResponseEntity<ErrorResponse> buildResponseEntity(ErrorResponse errorResponse, HttpStatus status) {
    return ResponseEntity.status(status).body(errorResponse);
  }

  private static void logException(Level level, Exception exception) {
    log.log(level, "Handling exception", exception);
  }
}
