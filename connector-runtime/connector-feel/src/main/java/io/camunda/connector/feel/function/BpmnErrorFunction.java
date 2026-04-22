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

import static io.camunda.connector.feel.FeelConnectorFunctionProvider.BPMN_ERROR_TYPE_VALUE;
import static io.camunda.connector.feel.FeelConnectorFunctionProvider.ERROR_TYPE_PROPERTY;

import java.util.List;
import org.camunda.feel.context.Context;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.ValContext;
import scala.collection.immutable.Map;
import scala.collection.immutable.Map$;

public class BpmnErrorFunction {

  public static final String NAME = "bpmnError";

  private static final List<String> ARGUMENTS_CODE_ONLY = List.of("errorCode");
  private static final List<String> ARGUMENTS = List.of("errorCode", "errorMessage");
  private static final List<String> ARGUMENTS_WITH_VARS =
      List.of("errorCode", "errorMessage", "variables");

  private static final JavaFunction CODE_ONLY =
      new JavaFunction(
          ARGUMENTS_CODE_ONLY,
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map2<>(
                          ERROR_TYPE_PROPERTY,
                          BPMN_ERROR_TYPE_VALUE,
                          ARGUMENTS_CODE_ONLY.get(0),
                          FunctionHelper.toString(args, 0, NAME, ARGUMENTS_CODE_ONLY.get(0))),
                      Map$.MODULE$.empty())));

  private static final JavaFunction WITH_CODE_AND_MESSAGE =
      new JavaFunction(
          ARGUMENTS,
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map3<>(
                          ERROR_TYPE_PROPERTY,
                          BPMN_ERROR_TYPE_VALUE,
                          ARGUMENTS.get(0),
                          FunctionHelper.toString(args, 0, NAME, ARGUMENTS.get(0)),
                          ARGUMENTS.get(1),
                          FunctionHelper.toString(args, 1, NAME, ARGUMENTS.get(1))),
                      Map$.MODULE$.empty())));

  private static final JavaFunction WITH_VARS =
      new JavaFunction(
          ARGUMENTS_WITH_VARS,
          args ->
              new ValContext(
                  new Context.StaticContext(
                      new Map.Map4<>(
                          ERROR_TYPE_PROPERTY,
                          BPMN_ERROR_TYPE_VALUE,
                          ARGUMENTS.get(0),
                          FunctionHelper.toString(args, 0, NAME, ARGUMENTS.get(0)),
                          ARGUMENTS.get(1),
                          FunctionHelper.toString(args, 1, NAME, ARGUMENTS.get(1)),
                          ARGUMENTS_WITH_VARS.get(2),
                          FunctionHelper.toContext(args, 2, NAME, ARGUMENTS_WITH_VARS.get(2))),
                      Map$.MODULE$.empty())));

  public static final List<JavaFunction> FUNCTIONS =
      List.of(CODE_ONLY, WITH_CODE_AND_MESSAGE, WITH_VARS);
}
