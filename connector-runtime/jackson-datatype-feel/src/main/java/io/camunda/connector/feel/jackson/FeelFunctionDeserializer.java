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

import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

class FeelFunctionDeserializer<IN, OUT> extends AbstractFeelDeserializer<Function<IN, OUT>> {

  private final JavaType outputType;

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

  public FeelFunctionDeserializer(
      JavaType outputType, FeelExpressionEvaluator evaluator, ObjectMapper resultMapper) {
    super(evaluator, false, resultMapper);
    this.outputType = outputType;
  }

  @Override
  @SuppressWarnings("unchecked")
  protected Function<IN, OUT> doDeserialize(
      JsonNode node, JsonNode feelContext, DeserializationContext deserializationContext) {
    return (input) -> {
      try {
        JsonNode jsonNode =
            BLANK_OBJECT_MAPPER.valueToTree(
                evaluateFeelExpression(
                    deserializationContext,
                    node.textValue(),
                    deserializationContext.getTypeFactory().constructType(JsonNode.class),
                    input,
                    feelContext));
        if (jsonNode == null || jsonNode.isNull()) {
          return null;
        }
        if (outputType.getRawClass() == String.class && jsonNode.isObject()) {
          return (OUT) BLANK_OBJECT_MAPPER.writeValueAsString(jsonNode);
        }
        // The callback's result is runtime data: a webhook request, an HTTP response. The result
        // mapper registers no FEEL or secret-reference deserializer, so a caller-supplied string
        // such as "=camunda.secrets.TOKEN" is bound as a string.
        return resultReader(deserializationContext, outputType).readValue(jsonNode);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    if (property != null) {
      if (property.getType().containedTypeCount() == 2) {
        var outputType = property.getType().containedType(1);
        return new FeelFunctionDeserializer<>(outputType, evaluator, resultMapper);
      }
    }

    return new FeelFunctionDeserializer<>(TypeFactory.unknownType(), evaluator, resultMapper);
  }

  private static class MergedContext {
    @JsonMerge Map<String, Object> context;

    public MergedContext() {
      this.context = null;
    }

    public MergedContext(Map<String, Object> context) {
      this.context = context;
    }

    public void setContext(Map<String, Object> context) {
      this.context = context;
    }

    public Map<String, Object> getContext() {
      return context;
    }
  }
}
