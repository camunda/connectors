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
package io.camunda.connector.runtime.core.feel;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.camunda.feel.context.Context;
import org.camunda.feel.context.Context.StaticContext;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.context.JavaFunctionProvider;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValDayTimeDuration;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.syntaxtree.ValString;
import scala.collection.JavaConverters;
import scala.collection.immutable.Map;
import scala.collection.immutable.Map$;
import scala.math.BigDecimal;

/** Provider of Connector-related FEEL functions like 'bpmnError'. */
public class FeelConnectorFunctionProvider extends JavaFunctionProvider {
  public static final String ERROR_TYPE_PROPERTY = "errorType";
  public static final String BPMN_ERROR_TYPE_VALUE = "bpmnError";
  public static final String JOB_ERROR_TYPE_VALUE = "jobError";
  public static final String CONNECTOR_FUNCTION_NAME = "connector";

  // BPMN error
  private static final String BPMN_ERROR_FUNCTION_NAME = "bpmnError";
  private static final List<String> BPMN_ERROR_ARGUMENTS = List.of("code", "message");
  private static final List<String> BPMN_ERROR_ARGUMENTS_WITH_VARS =
      List.of("code", "message", "variables");
  private static final JavaFunction BPMN_ERROR_FUNCTION =
      new JavaFunction(
          BPMN_ERROR_ARGUMENTS,
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map3<>(
                          ERROR_TYPE_PROPERTY,
                          BPMN_ERROR_TYPE_VALUE,
                          BPMN_ERROR_ARGUMENTS.get(0),
                          toString(args, 0),
                          BPMN_ERROR_ARGUMENTS.get(1),
                          toString(args, 1)),
                      Map$.MODULE$.empty())));

  private static final JavaFunction BPMN_ERROR_FUNCTION_WITH_VARS =
      new JavaFunction(
          BPMN_ERROR_ARGUMENTS_WITH_VARS,
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map4<>(
                          ERROR_TYPE_PROPERTY,
                          BPMN_ERROR_TYPE_VALUE,
                          BPMN_ERROR_ARGUMENTS.get(0),
                          toString(args, 0),
                          BPMN_ERROR_ARGUMENTS.get(1),
                          toString(args, 1),
                          BPMN_ERROR_ARGUMENTS_WITH_VARS.get(2),
                          toContext(args, 2)),
                      Map$.MODULE$.empty())));
  private static final String JOB_ERROR_FUNCTION_NAME = "jobError";
  // Fail Job

  private static final ValContext JOB_ERROR_DEFAULT_ARG_VARIABLES =
      new ValContext(new StaticContext(Map$.MODULE$.empty(), Map$.MODULE$.empty()));
  private static final ValNumber JOB_ERROR_DEFAULT_ARG_RETRIES =
      new ValNumber(new BigDecimal(new java.math.BigDecimal(0)));
  private static final ValDayTimeDuration JOB_ERROR_DEFAULT_ARG_RETRY_BACKOFF =
      new ValDayTimeDuration(Duration.ZERO);
  private static final List<String> JOB_ERROR_FUNCTION_ARGUMENTS =
      List.of("message", "variables", "retries", "retryBackoff");
  private static final JavaFunction JOB_ERROR_FUNCTION_4 =
      new JavaFunction(
          JOB_ERROR_FUNCTION_ARGUMENTS,
          args ->
              createJobErrorContext(
                  (ValString) args.get(0),
                  (ValContext) args.get(1),
                  (ValNumber) args.get(2),
                  (ValDayTimeDuration) args.get(3)));
  private static final JavaFunction JOB_ERROR_FUNCTION_3 =
      new JavaFunction(
          JOB_ERROR_FUNCTION_ARGUMENTS.subList(0, 3),
          args ->
              createJobErrorContext(
                  (ValString) args.get(0),
                  (ValContext) args.get(1),
                  (ValNumber) args.get(2),
                  JOB_ERROR_DEFAULT_ARG_RETRY_BACKOFF));
  private static final JavaFunction JOB_ERROR_FUNCTION_2 =
      new JavaFunction(
          JOB_ERROR_FUNCTION_ARGUMENTS.subList(0, 2),
          args ->
              createJobErrorContext(
                  (ValString) args.get(0),
                  (ValContext) args.get(1),
                  JOB_ERROR_DEFAULT_ARG_RETRIES,
                  JOB_ERROR_DEFAULT_ARG_RETRY_BACKOFF));
  private static final JavaFunction JOB_ERROR_FUNCTION_1 =
      new JavaFunction(
          JOB_ERROR_FUNCTION_ARGUMENTS.subList(0, 1),
          args ->
              createJobErrorContext(
                  (ValString) args.get(0),
                  JOB_ERROR_DEFAULT_ARG_VARIABLES,
                  JOB_ERROR_DEFAULT_ARG_RETRIES,
                  JOB_ERROR_DEFAULT_ARG_RETRY_BACKOFF));
  private static final java.util.Map<String, List<JavaFunction>> functions =
      java.util.Map.of(
          BPMN_ERROR_FUNCTION_NAME,
          List.of(BPMN_ERROR_FUNCTION, BPMN_ERROR_FUNCTION_WITH_VARS),
          JOB_ERROR_FUNCTION_NAME,
          List.of(
              JOB_ERROR_FUNCTION_1,
              JOB_ERROR_FUNCTION_2,
              JOB_ERROR_FUNCTION_3,
              JOB_ERROR_FUNCTION_4),
          CONNECTOR_FUNCTION_NAME,
          List.of(ConnectorFeelFunction.function));

  private static ValContext createJobErrorContext(
      ValString message, ValContext variables, ValNumber retries, ValDayTimeDuration retryBackoff) {
    java.util.Map<String, Object> javaMap = new HashMap<>();
    javaMap.put(ERROR_TYPE_PROPERTY, JOB_ERROR_TYPE_VALUE);
    javaMap.put(JOB_ERROR_FUNCTION_ARGUMENTS.get(0), message.value());
    javaMap.put(JOB_ERROR_FUNCTION_ARGUMENTS.get(1), JavaConverters.asJava(variables.properties()));
    javaMap.put(JOB_ERROR_FUNCTION_ARGUMENTS.get(2), retries.value());
    javaMap.put(JOB_ERROR_FUNCTION_ARGUMENTS.get(3), retryBackoff.value());
    return new ValContext(
        new Context.StaticContext(Map.from(JavaConverters.asScala(javaMap)), Map$.MODULE$.empty()));
  }

  private static String toString(List<Val> arguments, int index) {
    Val value = arguments.get(index);
    if (value instanceof ValString string) {
      return string.value();
    }
    throw new IllegalArgumentException(
        String.format(
            "Parameter '%s' of function '%s' must be a String",
            BPMN_ERROR_ARGUMENTS.get(index), BPMN_ERROR_FUNCTION_NAME));
  }

  private static ValContext toContext(List<Val> arguments, int index) {
    Val value = arguments.get(index);
    if (value instanceof ValContext map) {
      return map;
    }
    throw new IllegalArgumentException(
        String.format(
            "Parameter '%s' of function '%s' must be a Context",
            BPMN_ERROR_ARGUMENTS_WITH_VARS.get(index), BPMN_ERROR_FUNCTION_NAME));
  }

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
