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
package io.camunda.connector.feel;

import io.camunda.connector.feel.function.BackoffFunction;
import io.camunda.connector.feel.function.BpmnErrorFunction;
import io.camunda.connector.feel.function.IgnoreErrorFunction;
import io.camunda.connector.feel.function.JobErrorFunction;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.context.JavaFunctionProvider;

/** Provider of Connector-related FEEL functions like 'bpmnError'. */
public class FeelConnectorFunctionProvider extends JavaFunctionProvider {

  public static final String ERROR_TYPE_PROPERTY = "errorType";
  public static final String BPMN_ERROR_TYPE_VALUE = "bpmnError";
  public static final String JOB_ERROR_TYPE_VALUE = "jobError";
  public static final String IGNORE_ERROR_TYPE_VALUE = "ignoreError";

  private static final Map<String, List<JavaFunction>> functions =
      Map.of(
          BpmnErrorFunction.NAME, BpmnErrorFunction.FUNCTIONS,
          JobErrorFunction.NAME, JobErrorFunction.FUNCTIONS,
          IgnoreErrorFunction.NAME, IgnoreErrorFunction.FUNCTIONS,
          BackoffFunction.NAME, BackoffFunction.FUNCTIONS);

  @Override
  public Optional<JavaFunction> resolveFunction(String functionName) {
    throw new IllegalStateException("Should not be invoked.");
  }

  @Override
  public List<JavaFunction> resolveFunctions(String functionName) {
    return functions.getOrDefault(functionName, Collections.emptyList());
  }

  @Override
  public Collection<String> getFunctionNames() {
    return functions.keySet();
  }
}
