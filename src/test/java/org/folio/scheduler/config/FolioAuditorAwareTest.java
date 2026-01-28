package org.folio.scheduler.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.folio.spring.FolioExecutionContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioAuditorAwareTest {

  private static final UUID TEST_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Mock
  private FolioExecutionContext folioExecutionContext;

  @InjectMocks
  private FolioAuditorAware auditorAware;

  @Test
  void getCurrentAuditor_positive_returnsUserIdFromContext() {
    // Arrange
    when(folioExecutionContext.getUserId()).thenReturn(TEST_USER_ID);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(TEST_USER_ID);
  }

  @Test
  void getCurrentAuditor_positive_returnsEmptyWhenNoUserContext() {
    // Arrange
    when(folioExecutionContext.getUserId()).thenReturn(null);

    // Act
    var result = auditorAware.getCurrentAuditor();

    // Assert
    assertThat(result).isEmpty();
  }
}
