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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
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
    AnnotatedElement element = context.getElement().orElse(null);
    if (element == null) {
      return DISABLED;
    }

    // check the source of the annotation
    SystemIntegrationTest annotation;
    if (element instanceof Method m) {
      annotation = m.getAnnotation(SystemIntegrationTest.class);
      if (annotation == null) {
        annotation = m.getDeclaringClass().getAnnotation(SystemIntegrationTest.class);
      }
    } else if (element instanceof Class<?> clazz) {
      annotation = clazz.getAnnotation(SystemIntegrationTest.class);
    } else {
      return DISABLED; // unsupported element type
    }

    if (annotation == null) {
      return DISABLED;
    }

    ExternalSystem externalSystem = annotation.with();
    String envVar = System.getenv("SYSTEM_INTEGRATION_TEST_" + externalSystem.id.toUpperCase());
    return (envVar != null && !envVar.isEmpty())
        ? ConditionEvaluationResult.enabled(
            "Running external system integration test with " + externalSystem.id)
        : DISABLED;
  }
}
