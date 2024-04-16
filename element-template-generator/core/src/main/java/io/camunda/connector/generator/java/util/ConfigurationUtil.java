/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.generator.java.util;

import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorElementType;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConfigurationUtil {

  public static GeneratorConfiguration fromAnnotation(
      ElementTemplate annotation, GeneratorConfiguration override) {
    var templateId = annotation.id();
    if (override.templateId() != null) {
      templateId = override.templateId();
    }
    var templateName = Optional.ofNullable(override.templateName()).orElseGet(annotation::name);
    var templateVersion =
        Optional.ofNullable(override.templateVersion()).orElseGet(annotation::version);
    var connectorMode = override.connectorMode();
    var elementTypes =
        Arrays.stream(annotation.elementTypes())
            .map(
                type ->
                    new ConnectorElementType(
                        Arrays.stream(type.appliesTo()).collect(Collectors.toSet()),
                        type.elementType(),
                        type.templateNameOverride().isBlank() ? null : type.templateNameOverride(),
                        type.templateIdOverride().isBlank() ? null : type.templateIdOverride()))
            .collect(Collectors.toSet());
    if (override.elementTypes() != null && !override.elementTypes().isEmpty()) {
      elementTypes = override.elementTypes();
    }
    var features = Optional.ofNullable(override.features()).orElseGet(Map::of);
    return new GeneratorConfiguration(
        connectorMode, templateId, templateName, templateVersion, elementTypes, features);
  }
}
