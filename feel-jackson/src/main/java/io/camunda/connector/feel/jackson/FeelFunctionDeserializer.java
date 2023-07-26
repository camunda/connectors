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
package io.camunda.connector.feel.jackson;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.connector.feel.FeelEngineWrapper;
import java.util.function.Function;

class FeelFunctionDeserializer<IN, OUT> extends AbstractFeelDeserializer<Function<IN, OUT>> {

  private final JavaType outputType;

  public FeelFunctionDeserializer(JavaType outputType, FeelEngineWrapper feelEngineWrapper) {
    super(feelEngineWrapper, false);
    this.outputType = outputType;
  }

  private final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();

  @Override
  protected Function<IN, OUT> doDeserialize(JsonNode node, ObjectMapper mapper) {
    return (input) -> feelEngineWrapper.evaluate(node.textValue(), input, outputType);
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    if (property.getType().containedTypeCount() == 2) {
      var outputType = property.getType().containedType(1);
      return new FeelFunctionDeserializer<>(outputType, feelEngineWrapper);
    }
    return new FeelFunctionDeserializer<>(TypeFactory.unknownType(), feelEngineWrapper);
  }
}
