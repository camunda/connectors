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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.camunda.feel.context.Context;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.context.JavaFunctionProvider;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValString;
import scala.collection.immutable.Map;
import scala.collection.immutable.Map$;

/** Provider of Connector-related FEEL functions like 'bpmnError'. */
public class FeelConnectorFunctionProvider extends JavaFunctionProvider {

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
                      new Map.Map2<>(
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
                      new Map.Map3<>(
                          BPMN_ERROR_ARGUMENTS.get(0),
                          toString(args, 0),
                          BPMN_ERROR_ARGUMENTS.get(1),
                          toString(args, 1),
                          BPMN_ERROR_ARGUMENTS_WITH_VARS.get(2),
                          toContext(args, 2)),
                      Map$.MODULE$.empty())));
  private static final java.util.Map<String, List<JavaFunction>> functions =
      java.util.Map.of(
          BPMN_ERROR_FUNCTION_NAME, List.of(BPMN_ERROR_FUNCTION, BPMN_ERROR_FUNCTION_WITH_VARS));

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
    return List.of(BPMN_ERROR_FUNCTION_NAME);
  }

  private static String toString(List<Val> arguments, int index) {
    Val value = arguments.get(index);
    if (value instanceof ValString) {
      return ((ValString) value).value();
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
}
