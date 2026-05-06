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
package io.camunda.connector.feel.function;

import static io.camunda.connector.feel.FeelConnectorFunctionProvider.ERROR_TYPE_PROPERTY;
import static io.camunda.connector.feel.FeelConnectorFunctionProvider.JOB_ERROR_TYPE_VALUE;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import org.camunda.feel.context.Context;
import org.camunda.feel.context.Context.StaticContext;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValDayTimeDuration;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.syntaxtree.ValString;
import scala.collection.JavaConverters;
import scala.collection.immutable.Map;
import scala.collection.immutable.Map$;
import scala.math.BigDecimal;

public class JobErrorFunction {

  public static final String NAME = "jobError";

  private static final List<String> ARGUMENTS =
      List.of("errorMessage", "variables", "retries", "retryBackoff");

  private static final ValContext DEFAULT_VARIABLES =
      new ValContext(new StaticContext(Map$.MODULE$.empty(), Map$.MODULE$.empty()));
  private static final ValNumber DEFAULT_RETRIES =
      new ValNumber(new BigDecimal(new java.math.BigDecimal(0)));
  private static final ValDayTimeDuration DEFAULT_RETRY_BACKOFF =
      new ValDayTimeDuration(Duration.ZERO);

  private static final JavaFunction FUNCTION_4 =
      new JavaFunction(
          ARGUMENTS,
          args ->
              createContext(
                  (ValString) args.get(0),
                  (ValContext) args.get(1),
                  (ValNumber) args.get(2),
                  (ValDayTimeDuration) args.get(3)));

  private static final JavaFunction FUNCTION_3 =
      new JavaFunction(
          ARGUMENTS.subList(0, 3),
          args ->
              createContext(
                  (ValString) args.get(0),
                  (ValContext) args.get(1),
                  (ValNumber) args.get(2),
                  DEFAULT_RETRY_BACKOFF));

  private static final JavaFunction FUNCTION_2 =
      new JavaFunction(
          ARGUMENTS.subList(0, 2),
          args ->
              createContext(
                  (ValString) args.get(0),
                  (ValContext) args.get(1),
                  DEFAULT_RETRIES,
                  DEFAULT_RETRY_BACKOFF));

  private static final JavaFunction FUNCTION_1 =
      new JavaFunction(
          ARGUMENTS.subList(0, 1),
          args ->
              createContext(
                  (ValString) args.get(0),
                  DEFAULT_VARIABLES,
                  DEFAULT_RETRIES,
                  DEFAULT_RETRY_BACKOFF));

  public static final List<JavaFunction> FUNCTIONS =
      List.of(FUNCTION_1, FUNCTION_2, FUNCTION_3, FUNCTION_4);

  private static ValContext createContext(
      ValString message, ValContext variables, ValNumber retries, ValDayTimeDuration retryBackoff) {
    java.util.Map<String, Object> javaMap = new HashMap<>();
    javaMap.put(ERROR_TYPE_PROPERTY, JOB_ERROR_TYPE_VALUE);
    javaMap.put(ARGUMENTS.get(0), message);
    javaMap.put(ARGUMENTS.get(1), variables);
    javaMap.put(ARGUMENTS.get(2), retries);
    javaMap.put(ARGUMENTS.get(3), retryBackoff);
    return new ValContext(
        new Context.StaticContext(Map.from(JavaConverters.asScala(javaMap)), Map$.MODULE$.empty()));
  }
}
