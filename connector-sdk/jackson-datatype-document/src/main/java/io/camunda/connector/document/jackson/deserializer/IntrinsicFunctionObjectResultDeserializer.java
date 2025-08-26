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
package io.camunda.connector.document.jackson.deserializer;

import static io.camunda.connector.document.jackson.deserializer.DeserializationUtil.isIntrinsicFunction;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.document.jackson.IntrinsicFunctionModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.intrinsic.IntrinsicFunctionExecutor;
import io.camunda.connector.intrinsic.IntrinsicFunctionParams;
import java.io.IOException;

public class IntrinsicFunctionObjectResultDeserializer extends AbstractDeserializer<Object> {

  private final IntrinsicFunctionExecutor operationExecutor;

  public IntrinsicFunctionObjectResultDeserializer(
      IntrinsicFunctionExecutor operationExecutor, DocumentModuleSettings settings) {
    super(settings);
    this.operationExecutor = operationExecutor;
  }

  @Override
  protected Object handleJsonNode(JsonNode node, DeserializationContext context)
      throws IOException {
    if (!isIntrinsicFunction(node)) {
      throw new IllegalArgumentException(
          "Unsupported document format. Expected an operation, got: " + node);
    }
    DeserializationUtil.tryDecrementIntrinsicFunctionCounter(context);
    final IntrinsicFunctionModel operation =
        context.readTreeAsValue(node, IntrinsicFunctionModel.class);
    final IntrinsicFunctionParams params =
        new IntrinsicFunctionParams.Positional(operation.params());

    return operationExecutor.execute(operation.name(), params);
  }
}
