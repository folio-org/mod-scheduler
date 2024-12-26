package org.folio.scheduler.utils;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import lombok.experimental.UtilityClass;
import org.folio.common.utils.SemverUtils;
import org.folio.scheduler.domain.dto.TimerDescriptor;

@UtilityClass
public class TimerDescriptorUtils {

  public static String evalModuleName(TimerDescriptor td) {
    var moduleId = td.getModuleId();
    return isNotEmpty(moduleId) ? SemverUtils.getName(moduleId) : td.getModuleName();
  }
}
