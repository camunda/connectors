/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

public class PromptUtils {
  private PromptUtils() {}

  public static String resolveParameterizedPrompt(String template, Map<String, Object> parameters) {
    if (StringUtils.isBlank(template) || CollectionUtils.isEmpty(parameters)) {
      return template;
    }

    var result = template;
    for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
      final var replacementKey = "{{%s}}".formatted(parameter.getKey().trim());
      final var replacementValue =
          Optional.ofNullable(parameter.getValue()).map(Object::toString).orElse("");

      result = result.replace(replacementKey, replacementValue);
    }

    return result;
  }
}
