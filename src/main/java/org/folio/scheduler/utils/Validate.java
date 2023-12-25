package org.folio.scheduler.utils;

import static lombok.AccessLevel.PRIVATE;

import java.util.Collection;
import java.util.function.Supplier;
import lombok.NoArgsConstructor;
import org.folio.scheduler.exception.RequestValidationException;

/**
 * Assertion utility class which helps with validation of request/method parameters.
 *
 * <p>Provides single line methods to quickly check parameter or boolean condition</p>
 */
@NoArgsConstructor(access = PRIVATE)
public class Validate {

  /**
   * Validates if given value is not null or else throws a validation exception.
   *
   * @param value - value to check
   * @param messageSupplier - error message supplier as java {@link Supplier} lambda
   * @throws RequestValidationException - if given value is null
   */
  public static void nonNull(Object value, Supplier<String> messageSupplier) {
    if (value == null) {
      throw new RequestValidationException(messageSupplier.get());
    }
  }

  /**
   * Validates if given expression is true or else throws a validation exception.
   *
   * @param expression - boolean expression to check
   * @param messageSupplier - error message supplier as java {@link Supplier} lambda
   * @throws RequestValidationException - if given value is null
   */
  public static void isTrue(boolean expression, Supplier<String> messageSupplier) {
    if (!expression) {
      throw new RequestValidationException(messageSupplier.get());
    }
  }

  /**
   * Validates if given collection is not null or empty or else throws a validation exception.
   *
   * @param collection - collection to check
   * @param messageSupplier - error message supplier as java {@link Supplier} lambda
   * @throws RequestValidationException - if given value is null
   */
  public static void notEmpty(Collection<?> collection, Supplier<String> messageSupplier) {
    if (collection == null || collection.isEmpty()) {
      throw new RequestValidationException(messageSupplier.get());
    }
  }
}
