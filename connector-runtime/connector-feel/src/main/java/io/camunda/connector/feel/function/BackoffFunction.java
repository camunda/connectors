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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.ValDayTimeDuration;
import org.camunda.feel.syntaxtree.ValNumber;
import scala.math.BigDecimal;

public class BackoffFunction {

  public static final String NAME = "backoff";

  private static final long DEFAULT_MIN_DELAY_MS = 50L;
  private static final long DEFAULT_MAX_DELAY_MS = 5_000L;
  private static final double DEFAULT_FACTOR = 1.6;
  private static final double DEFAULT_JITTER_FACTOR = 0.1;

  private static final ValDayTimeDuration DEFAULT_MIN_DELAY =
      new ValDayTimeDuration(Duration.ofMillis(DEFAULT_MIN_DELAY_MS));
  private static final ValDayTimeDuration DEFAULT_MAX_DELAY =
      new ValDayTimeDuration(Duration.ofMillis(DEFAULT_MAX_DELAY_MS));
  private static final ValNumber DEFAULT_FACTOR_VAL =
      new ValNumber(new BigDecimal(new java.math.BigDecimal(DEFAULT_FACTOR)));
  private static final ValNumber DEFAULT_JITTER_FACTOR_VAL =
      new ValNumber(new BigDecimal(new java.math.BigDecimal(DEFAULT_JITTER_FACTOR)));

  private static final List<String> ARGUMENTS =
      List.of("attempt", "minDelay", "factor", "maxDelay", "jitterFactor");

  private static final JavaFunction FUNCTION_5 =
      new JavaFunction(
          ARGUMENTS,
          args ->
              compute(
                  FunctionHelper.toNumber(args, 0, NAME, "attempt"),
                  FunctionHelper.toDuration(args, 1, NAME, "minDelay"),
                  FunctionHelper.toNumber(args, 2, NAME, "factor"),
                  FunctionHelper.toDuration(args, 3, NAME, "maxDelay"),
                  FunctionHelper.toNumber(args, 4, NAME, "jitterFactor")));

  private static final JavaFunction FUNCTION_4 =
      new JavaFunction(
          ARGUMENTS.subList(0, 4),
          args ->
              compute(
                  FunctionHelper.toNumber(args, 0, NAME, "attempt"),
                  FunctionHelper.toDuration(args, 1, NAME, "minDelay"),
                  FunctionHelper.toNumber(args, 2, NAME, "factor"),
                  FunctionHelper.toDuration(args, 3, NAME, "maxDelay"),
                  DEFAULT_JITTER_FACTOR_VAL));

  private static final JavaFunction FUNCTION_3 =
      new JavaFunction(
          ARGUMENTS.subList(0, 3),
          args ->
              compute(
                  FunctionHelper.toNumber(args, 0, NAME, "attempt"),
                  FunctionHelper.toDuration(args, 1, NAME, "minDelay"),
                  FunctionHelper.toNumber(args, 2, NAME, "factor"),
                  DEFAULT_MAX_DELAY,
                  DEFAULT_JITTER_FACTOR_VAL));

  private static final JavaFunction FUNCTION_2 =
      new JavaFunction(
          ARGUMENTS.subList(0, 2),
          args ->
              compute(
                  FunctionHelper.toNumber(args, 0, NAME, "attempt"),
                  FunctionHelper.toDuration(args, 1, NAME, "minDelay"),
                  DEFAULT_FACTOR_VAL,
                  DEFAULT_MAX_DELAY,
                  DEFAULT_JITTER_FACTOR_VAL));

  private static final JavaFunction FUNCTION_1 =
      new JavaFunction(
          ARGUMENTS.subList(0, 1),
          args ->
              compute(
                  FunctionHelper.toNumber(args, 0, NAME, "attempt"),
                  DEFAULT_MIN_DELAY,
                  DEFAULT_FACTOR_VAL,
                  DEFAULT_MAX_DELAY,
                  DEFAULT_JITTER_FACTOR_VAL));

  public static final List<JavaFunction> FUNCTIONS =
      List.of(FUNCTION_1, FUNCTION_2, FUNCTION_3, FUNCTION_4, FUNCTION_5);

  private static ValDayTimeDuration compute(
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
    double clampedMs = Math.max(minMs, Math.min(maxMs, rawMs));

    double jitter = 0.0;
    if (jf > 0) {
      jitter = ThreadLocalRandom.current().nextDouble(-jf * clampedMs, jf * clampedMs);
    }

    return new ValDayTimeDuration(Duration.ofMillis(Math.max(0, Math.round(clampedMs + jitter))));
  }
}
