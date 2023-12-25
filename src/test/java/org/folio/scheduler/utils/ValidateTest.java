package org.folio.scheduler.utils;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Supplier;
import org.folio.scheduler.exception.RequestValidationException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ValidateTest {

  @Mock private Supplier<String> messageSupplier;

  @Test
  void notNull_positive() {
    Validate.nonNull("value", messageSupplier);
    verifyNoInteractions(messageSupplier);
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  void notNull_negative() {
    when(messageSupplier.get()).thenReturn("error message");

    assertThatThrownBy(() -> Validate.nonNull(null, messageSupplier))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("error message");
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  void isTrue_positive() {
    Validate.isTrue("test" != null, messageSupplier);
    verifyNoInteractions(messageSupplier);
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  void isTrue_negative() {
    when(messageSupplier.get()).thenReturn("error message");

    assertThatThrownBy(() -> Validate.isTrue(1 != 1, messageSupplier))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("error message");
  }

  @Test
  void notEmpty_positive() {
    Validate.notEmpty(List.of("test1", "test2"), messageSupplier);
    verifyNoInteractions(messageSupplier);
  }

  @Test
  void notEmpty_negative_emptyList() {
    when(messageSupplier.get()).thenReturn("error message");
    var givenValue = emptyList();

    assertThatThrownBy(() -> Validate.notEmpty(givenValue, messageSupplier))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("error message");
  }

  @Test
  @SuppressWarnings("ConstantConditions")
  void notEmpty_negative_nullValue() {
    when(messageSupplier.get()).thenReturn("error message");

    assertThatThrownBy(() -> Validate.notEmpty(null, messageSupplier))
      .isInstanceOf(RequestValidationException.class)
      .hasMessage("error message");
  }
}
