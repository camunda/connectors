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
import static io.camunda.connector.feel.FeelConnectorFunctionProvider.IGNORE_ERROR_TYPE_VALUE;

import java.util.List;
import org.camunda.feel.context.Context;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.ValContext;
import scala.collection.immutable.Map;
import scala.collection.immutable.Map$;

public class IgnoreErrorFunction {

  public static final String NAME = "ignoreError";

  private static final List<String> ARGUMENTS = List.of("variables");

  private static final JavaFunction WITH_VARIABLES =
      new JavaFunction(
          ARGUMENTS,
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map2<>(
                          ERROR_TYPE_PROPERTY,
                          IGNORE_ERROR_TYPE_VALUE,
                          ARGUMENTS.getFirst(),
                          FunctionHelper.toContext(args, 0, NAME, ARGUMENTS.getFirst())),
                      Map$.MODULE$.empty())));

  private static final JavaFunction NO_ARGS =
      new JavaFunction(
          List.of(),
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map1<>(ERROR_TYPE_PROPERTY, IGNORE_ERROR_TYPE_VALUE),
                      Map$.MODULE$.empty())));

  public static final List<JavaFunction> FUNCTIONS = List.of(WITH_VARIABLES, NO_ARGS);
}
