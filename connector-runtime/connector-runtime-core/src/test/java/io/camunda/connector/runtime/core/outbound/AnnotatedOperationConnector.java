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
package io.camunda.connector.runtime.core.outbound;

import io.camunda.connector.api.annotation.Header;
import io.camunda.connector.api.annotation.Operation;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.annotation.Variable;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.function.Function;

@OutboundConnector(name = "Annotation Connector", type = "io.camunda:annotated-operation")
public class AnnotatedOperationConnector implements OutboundConnectorProvider {

  record MyObjectParam(String name, int value) {}

  @Operation(id = "myOperation")
  public String myOperation(
      @Variable("myStringParam") String myStringParam,
      @Variable("myObjectParam") MyObjectParam myObjectParam,
      @Variable(value = "nullObjectParam", required = false) MyObjectParam nullObjectParam,
      @Variable MyObjectParam nestedObjectWithoutName,
      OutboundConnectorContext context) {
    System.out.println("myStringParam: " + myStringParam);
    System.out.println("myObjectParam: " + myObjectParam);
    System.out.println("nullObjectParam: " + nullObjectParam);
    System.out.println("nestedObjectWithoutName: " + nestedObjectWithoutName);
    System.out.println("OutboundConnectorContext: " + context);
    return "Hello, " + myStringParam + "!";
  }

  @Operation(id = "myOperation2")
  public Object mySecondOperation(OutboundConnectorContext context) {
    System.out.println("Executing mySecondOperation with context: " + context);
    return context;
  }

  record MyValidatingObject(@NotNull String validatingName) {}

  @Operation(id = "myOperation3")
  public Object myThirdOperation(@Variable MyValidatingObject object) {
    return object;
  }

  @Operation(id = "myOperation4")
  public Object headerAnnotatedMethod(@Header("myHeader") String headerParam) {
    return headerParam;
  }

  @Operation(id = "myOperation5")
  public Object handleAnnotatedMethodWithFEELParameter(
      @Variable Map<String, Integer> vars,
      @Header("myFeelFunction") Function<Map<String, Integer>, Integer> feelFunctionFromHeader) {
    return feelFunctionFromHeader.apply(vars);
  }
}
