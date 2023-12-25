package org.folio.scheduler.support;

import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TestConstants {

  public static final String TENANT_ID = "test";
  public static final String TIMER_ID = "cbff510d-a5ca-4f32-bd09-ff81288bc25c";
  public static final String USER_ID = "00000000-0000-0000-0000-000000000000";
  public static final String USER_TOKEN = "test-token";
  public static final UUID TIMER_UUID = UUID.fromString(TIMER_ID);
  public static final UUID USER_ID_UUID = UUID.fromString(USER_ID);
}
