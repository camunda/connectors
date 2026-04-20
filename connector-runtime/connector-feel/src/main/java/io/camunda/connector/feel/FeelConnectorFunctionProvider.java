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

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
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
  public static final String IGNORE_ERROR_TYPE_VALUE = "ignoreError";

  // BPMN error
  private static final String BPMN_ERROR_FUNCTION_NAME = "bpmnError";
  private static final List<String> BPMN_ERROR_ARGUMENTS_CODE_ONLY = List.of("errorCode");
  private static final List<String> BPMN_ERROR_ARGUMENTS = List.of("errorCode", "errorMessage");
  private static final List<String> BPMN_ERROR_ARGUMENTS_WITH_VARS =
      List.of("errorCode", "errorMessage", "variables");

  private static final JavaFunction BPMN_ERROR_FUNCTION_CODE_ONLY =
      new JavaFunction(
          BPMN_ERROR_ARGUMENTS_CODE_ONLY,
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map2<>(
                          ERROR_TYPE_PROPERTY,
                          BPMN_ERROR_TYPE_VALUE,
                          BPMN_ERROR_ARGUMENTS_CODE_ONLY.get(0),
                          toString(args, 0)),
                      Map$.MODULE$.empty())));

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
      List.of("errorMessage", "variables", "retries", "retryBackoff");
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

  /** Ignore Error Function */
  private static final String IGNORE_ERROR_FUNCTION_NAME = "ignoreError";

  private static final List<String> IGNORE_ERROR_ARGUMENTS = List.of("variables");

  private static final JavaFunction IGNORE_ERROR_FUNCTION =
      new JavaFunction(
          IGNORE_ERROR_ARGUMENTS,
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map2<>(
                          ERROR_TYPE_PROPERTY,
                          IGNORE_ERROR_TYPE_VALUE,
                          IGNORE_ERROR_ARGUMENTS.getFirst(),
                          toContext(args, 0)),
                      Map$.MODULE$.empty())));

  private static final JavaFunction IGNORE_ERROR_FUNCTION_NO_ARGS =
      new JavaFunction(
          List.of(),
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map1<>(ERROR_TYPE_PROPERTY, IGNORE_ERROR_TYPE_VALUE),
                      Map$.MODULE$.empty())));

  /** Back Off Function */
  private static final String BACKOFF_FUNCTION_NAME = "backoff";

  /** Default constants - mirror ExponentialBackoffBuilderImpl */
  private static final long BACKOFF_DEFAULT_MIN_DELAY_MS = 50L;

  private static final long BACKOFF_DEFAULT_MAX_DELAY_MS = 5_000L;
  private static final double BACKOFF_DEFAULT_FACTOR = 1.6;
  private static final double BACKOFF_DEFAULT_JITTER_FACTOR = 0.1;

  private static final ValDayTimeDuration BACKOFF_DEFAULT_MIN_DELAY =
      new ValDayTimeDuration(Duration.ofMillis(BACKOFF_DEFAULT_MIN_DELAY_MS));
  private static final ValDayTimeDuration BACKOFF_DEFAULT_MAX_DELAY =
      new ValDayTimeDuration(Duration.ofMillis(BACKOFF_DEFAULT_MAX_DELAY_MS));
  private static final ValNumber BACKOFF_DEFAULT_FACTOR_VAL =
      new ValNumber(new BigDecimal(new java.math.BigDecimal(BACKOFF_DEFAULT_FACTOR)));
  private static final ValNumber BACKOFF_DEFAULT_JITTER_FACTOR_VAL =
      new ValNumber(new BigDecimal(new java.math.BigDecimal(BACKOFF_DEFAULT_JITTER_FACTOR)));

  private static final List<String> BACKOFF_ARGUMENTS =
      List.of("attempt", "minDelay", "factor", "maxDelay", "jitterFactor");

  private static final JavaFunction BACKOFF_FUNCTION_5 =
      new JavaFunction(
          BACKOFF_ARGUMENTS,
          args ->
              computeBackoff(
                  toNumber(args, 0, BACKOFF_FUNCTION_NAME, "attempt"),
                  toDuration(args, 1, BACKOFF_FUNCTION_NAME, "minDelay"),
                  toNumber(args, 2, BACKOFF_FUNCTION_NAME, "factor"),
                  toDuration(args, 3, BACKOFF_FUNCTION_NAME, "maxDelay"),
                  toNumber(args, 4, BACKOFF_FUNCTION_NAME, "jitterFactor")));

  private static final JavaFunction BACKOFF_FUNCTION_4 =
      new JavaFunction(
          BACKOFF_ARGUMENTS.subList(0, 4),
          args ->
              computeBackoff(
                  toNumber(args, 0, BACKOFF_FUNCTION_NAME, "attempt"),
                  toDuration(args, 1, BACKOFF_FUNCTION_NAME, "minDelay"),
                  toNumber(args, 2, BACKOFF_FUNCTION_NAME, "factor"),
                  toDuration(args, 3, BACKOFF_FUNCTION_NAME, "maxDelay"),
                  BACKOFF_DEFAULT_JITTER_FACTOR_VAL));

  private static final JavaFunction BACKOFF_FUNCTION_3 =
      new JavaFunction(
          BACKOFF_ARGUMENTS.subList(0, 3),
          args ->
              computeBackoff(
                  toNumber(args, 0, BACKOFF_FUNCTION_NAME, "attempt"),
                  toDuration(args, 1, BACKOFF_FUNCTION_NAME, "minDelay"),
                  toNumber(args, 2, BACKOFF_FUNCTION_NAME, "factor"),
                  BACKOFF_DEFAULT_MAX_DELAY,
                  BACKOFF_DEFAULT_JITTER_FACTOR_VAL));

  private static final JavaFunction BACKOFF_FUNCTION_1 =
      new JavaFunction(
          BACKOFF_ARGUMENTS.subList(0, 1),
          args ->
              computeBackoff(
                  toNumber(args, 0, BACKOFF_FUNCTION_NAME, "attempt"),
                  BACKOFF_DEFAULT_MIN_DELAY,
                  BACKOFF_DEFAULT_FACTOR_VAL,
                  BACKOFF_DEFAULT_MAX_DELAY,
                  BACKOFF_DEFAULT_JITTER_FACTOR_VAL));

  private static ValDayTimeDuration computeBackoff(
      ValNumber attempt,
      ValDayTimeDuration minDelay,
      ValNumber factor,
      ValDayTimeDuration maxDelay,
      ValNumber jitterFactor) {

    int attemptInt = attempt.value().intValue();
    if (attemptInt < 1) {
      throw new IllegalArgumentException(
          "backoff(): 'attempt' must be >= 1, but was " + attemptInt);
    }

    long minMs = minDelay.value().toMillis();
    long maxMs = maxDelay.value().toMillis();
    double f = factor.value().toDouble();
    double jf = jitterFactor.value().toDouble();

    if (f <= 0) {
      throw new IllegalArgumentException("backoff(): 'factor' must be > 0, but was " + f);
    }
    if (minMs > maxMs) {
      throw new IllegalArgumentException(
          "backoff(): 'minDelay' must be <= 'maxDelay', but " + minMs + "ms > " + maxMs + "ms");
    }
    if (jf < 0) {
      throw new IllegalArgumentException("backoff(): 'jitterFactor' must be >= 0, but was " + jf);
    }

    double rawMs = minMs * Math.pow(f, attemptInt - 1);

    // clamp to [minMs, maxMs]
    double clampedMs = Math.max(minMs, Math.min(maxMs, rawMs));

    double jitter = 0.0;
    if (jf > 0) {
      double jitterMin = -jf * clampedMs;
      double jitterMax = jf * clampedMs;
      jitter = ThreadLocalRandom.current().nextDouble(jitterMin, jitterMax);
    }

    long resultMs = Math.round(clampedMs + jitter);
    return new ValDayTimeDuration(Duration.ofMillis(resultMs));
  }

  private static ValNumber toNumber(
      List<Val> args, int index, String functionName, String paramName) {
    Val value = args.get(index);
    if (value instanceof ValNumber number) {
      return number;
    }
    throw new IllegalArgumentException(
        String.format("Parameter '%s' of function '%s' must be a Number", paramName, functionName));
  }

  private static ValDayTimeDuration toDuration(
      List<Val> args, int index, String functionName, String paramName) {
    Val value = args.get(index);
    if (value instanceof ValDayTimeDuration duration) {
      return duration;
    }
    throw new IllegalArgumentException(
        String.format(
            "Parameter '%s' of function '%s' must be a day-time duration (e.g. duration(\"PT1S\"))",
            paramName, functionName));
  }

  private static final java.util.Map<String, List<JavaFunction>> functions =
      java.util.Map.of(
          BPMN_ERROR_FUNCTION_NAME,
          List.of(
              BPMN_ERROR_FUNCTION_CODE_ONLY, BPMN_ERROR_FUNCTION, BPMN_ERROR_FUNCTION_WITH_VARS),
          JOB_ERROR_FUNCTION_NAME,
          List.of(
              JOB_ERROR_FUNCTION_1,
              JOB_ERROR_FUNCTION_2,
              JOB_ERROR_FUNCTION_3,
              JOB_ERROR_FUNCTION_4),
          IGNORE_ERROR_FUNCTION_NAME,
          List.of(IGNORE_ERROR_FUNCTION, IGNORE_ERROR_FUNCTION_NO_ARGS),
          BACKOFF_FUNCTION_NAME,
          List.of(BACKOFF_FUNCTION_1, BACKOFF_FUNCTION_3, BACKOFF_FUNCTION_4, BACKOFF_FUNCTION_5));

  private static ValContext createJobErrorContext(
      ValString message, ValContext variables, ValNumber retries, ValDayTimeDuration retryBackoff) {
    java.util.Map<String, Object> javaMap = new HashMap<>();
    javaMap.put(ERROR_TYPE_PROPERTY, JOB_ERROR_TYPE_VALUE);
    javaMap.put(JOB_ERROR_FUNCTION_ARGUMENTS.get(0), message);
    javaMap.put(JOB_ERROR_FUNCTION_ARGUMENTS.get(1), variables);
    javaMap.put(JOB_ERROR_FUNCTION_ARGUMENTS.get(2), retries);
    javaMap.put(JOB_ERROR_FUNCTION_ARGUMENTS.get(3), retryBackoff);
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
