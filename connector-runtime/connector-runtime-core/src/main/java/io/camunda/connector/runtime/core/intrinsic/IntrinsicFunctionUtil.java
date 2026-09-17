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
package io.camunda.connector.runtime.core.intrinsic;

import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.document.jackson.IntrinsicFunctionModel;
import io.camunda.connector.runtime.core.intrinsic.IntrinsicFunctionAllowList.Call;
import java.util.ArrayList;
import java.util.List;

/**
 * Walks an already-bound job-variable tree looking for {@code camunda.function.type} nodes and
 * refuses any whose {@code (functionName, fieldPath)} isn't in the given allow-list — run once,
 * before the outbound property mapper's typed Jackson binding, so a disallowed call never reaches
 * {@code DefaultIntrinsicFunctionExecutor}. See security-testing-findings#275.
 */
public final class IntrinsicFunctionUtil {

  private IntrinsicFunctionUtil() {}

  public static void verifyAgainstAllowList(JsonNode input, IntrinsicFunctionAllowList allowList) {
    walk(input, new ArrayList<>(), allowList);
  }

  private static void walk(
      JsonNode node, List<String> fieldPath, IntrinsicFunctionAllowList allowList) {
    if (node.isObject()) {
      if (node.has(IntrinsicFunctionModel.DISCRIMINATOR_KEY)) {
        String functionName = node.get(IntrinsicFunctionModel.DISCRIMINATOR_KEY).asText();
        if (!allowList.isAllowed(new Call(functionName, fieldPath))) {
          throw new ConnectorInputException(
              "Intrinsic function '"
                  + functionName
                  + "' at '"
                  + String.join(".", fieldPath)
                  + "' is not declared in the deployed process model and cannot be dispatched from"
                  + " process data.");
        }
      }
      node.properties()
          .forEach(
              entry -> {
                var childPath = new ArrayList<>(fieldPath);
                childPath.add(entry.getKey());
                walk(entry.getValue(), childPath, allowList);
              });
    } else if (node.isArray()) {
      node.forEach(element -> walk(element, fieldPath, allowList));
    }
  }
}
