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
package io.camunda.connector.runtime.feel;

import io.camunda.connector.feel.FeelEngineWrapperUtil;
import io.camunda.connector.runtime.core.external.ExternalConnectorExecutor;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.context.JavaFunctionProvider;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValString;
import org.camunda.feel.valuemapper.ValueMapper;

public class ConnectorInvocationFeelFunctionProvider extends JavaFunctionProvider {

  private final ExternalConnectorExecutor connectorExecutor;

  private static final ValueMapper feelValueMapper = ValueMapper.defaultValueMapper();

  private JavaFunction function;

  public ConnectorInvocationFeelFunctionProvider(ExternalConnectorExecutor connectorExecutor) {
    this.connectorExecutor = connectorExecutor;
    initFunction();
  }

  private void initFunction() {
    function =
        new JavaFunction(
            Arrays.asList("type", "variables"),
            args -> {
              final ValString type = (ValString) args.get(0);
              String typeValue = type.value();

              final ValContext variables = (ValContext) args.get(1);
              Object unpackedVariables =
                  FeelEngineWrapperUtil.sanitizeScalaOutput(feelValueMapper.unpackVal(variables));

              Object result;
              try {
                result = connectorExecutor.execute(typeValue, unpackedVariables);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
              return feelValueMapper.toVal(result);
            });
  }

  public static final String CONNECTOR_FUNCTION_NAME = "connector";

  @Override
  public Optional<JavaFunction> resolveFunction(String functionName) {
    if (functionName.equals(CONNECTOR_FUNCTION_NAME)) {
      return Optional.of(function);
    }
    return Optional.empty();
  }

  @Override
  public Collection<String> getFunctionNames() {
    return List.of(CONNECTOR_FUNCTION_NAME);
  }
}
