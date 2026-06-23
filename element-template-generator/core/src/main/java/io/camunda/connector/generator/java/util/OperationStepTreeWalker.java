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

import static io.camunda.connector.generator.java.util.OperationBasedConnectorUtil.OPERATION_PROPERTY_ID;
import static io.camunda.connector.util.reflection.ReflectionUtil.getOperationId;
import static io.camunda.connector.util.reflection.ReflectionUtil.getOperationName;

import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.generator.dsl.LeafStep;
import io.camunda.connector.generator.dsl.Preset;
import io.camunda.connector.generator.dsl.Step;
import io.camunda.connector.util.reflection.ReflectionUtil.MethodWithAnnotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Second-pass walker that produces operation-metadata {@code steps} and {@code presets} */
public final class OperationStepTreeWalker {

  private OperationStepTreeWalker() {}

  public static StepTreeResult walk(List<MethodWithAnnotation<Operation>> methods) {
    if (methods == null || methods.isEmpty()) {
      return StepTreeResult.empty();
    }
    boolean anyKeywords = methods.stream().anyMatch(m -> m.annotation().keywords().length > 0);
    if (!anyKeywords) {
      return StepTreeResult.empty();
    }

    List<Step> steps = new ArrayList<>();
    List<Preset> presets = new ArrayList<>();

    for (MethodWithAnnotation<Operation> m : methods) {
      Operation op = m.annotation();
      String id = getOperationId(op);
      String[] keywords = requireKeywords(m);
      String name = getOperationName(op);
      String description = op.description().isBlank() ? null : op.description();
      String presetId = OPERATION_PROPERTY_ID + "_" + id;

      steps.add(new LeafStep(name, description, Arrays.asList(keywords), presetId));
      Map<String, String> properties = new LinkedHashMap<>();
      properties.put(OPERATION_PROPERTY_ID, id);
      presets.add(new Preset(presetId, properties));
    }

    return new StepTreeResult(steps, presets);
  }

  private static String[] requireKeywords(MethodWithAnnotation<Operation> m) {
    String[] keywords = m.annotation().keywords();
    if (keywords.length == 0) {
      throw new IllegalStateException(
          "@Operation method "
              + m.method().getDeclaringClass().getCanonicalName()
              + "#"
              + m.method().getName()
              + " is missing required @Operation(keywords = {...}). Every @Operation method in a "
              + "connector that participates in operation metadata must declare at least one "
              + "keyword.");
    }
    return keywords;
  }
}
