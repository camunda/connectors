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
package io.camunda.connector.test;

import java.util.Arrays;
import java.util.stream.Collectors;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

public class SystemIntegrationTestCondition implements ExecutionCondition {
  private static final String EXTERNAL_SYSTEM_LIST =
      Arrays.stream(ExternalSystem.values())
          .map(system -> system.id)
          .collect(Collectors.joining(", "));
  private static final ConditionEvaluationResult DISABLED =
      ConditionEvaluationResult.disabled(
          "Disabled because required environment variable is not set any of: "
              + EXTERNAL_SYSTEM_LIST);

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    Class<?> testClass = (Class<?>) context.getElement().orElse(null);
    if (testClass == null || !testClass.isAnnotationPresent(SystemIntegrationTest.class)) {
      return DISABLED;
    }
    SystemIntegrationTest annotation = testClass.getAnnotation(SystemIntegrationTest.class);
    ExternalSystem externalSystem = annotation.with();
    String envVar = System.getenv("SYSTEM_INTEGRATION_TEST_" + externalSystem.id.toUpperCase());
    return (envVar != null && !envVar.isEmpty())
        ? ConditionEvaluationResult.enabled(
            "Running external system integration test with " + externalSystem.id)
        : DISABLED;
  }
}
