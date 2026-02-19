/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public final class BpmnUtils {
  private BpmnUtils() {}

  public static Optional<String> getElementDocumentation(FlowNode element) {
    return element.getDocumentations().stream()
        .filter(d -> "text/plain".equals(d.getTextFormat()))
        .findFirst()
        .map(ModelElementInstance::getTextContent)
        .filter(StringUtils::isNotBlank);
  }

  public static Map<String, String> getExtensionProperties(FlowNode element) {
    final var extensionProperties = element.getSingleExtensionElement(ZeebeProperties.class);
    if (extensionProperties == null) {
      return Map.of();
    }

    return extensionProperties.getProperties().stream()
        .collect(Collectors.toMap(ZeebeProperty::getName, ZeebeProperty::getValue));
  }
}
