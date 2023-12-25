package org.folio.scheduler.utils;

import static org.apache.commons.lang3.StringUtils.startsWith;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.folio.scheduler.domain.dto.RoutingEntry;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OkapiRequestUtils {

  /**
   * Returns static path for routing entry.
   *
   * @param re - routing entry object
   * @return static path for routing entry
   */
  public static String getStaticPath(RoutingEntry re) {
    var resolvedPath = StringUtils.isEmpty(re.getPath()) ? re.getPathPattern() : re.getPath();
    return startsWith(resolvedPath, "/") ? resolvedPath : "/" + resolvedPath;
  }
}
