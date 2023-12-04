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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.connector.feel.FeelEngineWrapper;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

class FeelFunctionDeserializer<IN, OUT> extends AbstractFeelDeserializer<Function<IN, OUT>> {

  private final JavaType outputType;

  private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

  public FeelFunctionDeserializer(JavaType outputType, FeelEngineWrapper feelEngineWrapper) {
    super(feelEngineWrapper, false);
    this.outputType = outputType;
  }

  private final FeelEngineWrapper feelEngineWrapper = new FeelEngineWrapper();

  @Override
  protected Function<IN, OUT> doDeserialize(
      JsonNode node, ObjectMapper mapper, JsonNode feelContext) {
    return (input) -> {
      var jsonNode =
          feelEngineWrapper.evaluate(
              node.textValue(), JsonNode.class, mergeContexts(input, feelContext, mapper));
      try {
        if (outputType.getRawClass() == String.class && jsonNode.isObject()) {
          return (OUT) mapper.writeValueAsString(jsonNode);
        } else {
          return mapper.treeToValue(jsonNode, outputType);
        }
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    };
  }

  private Object mergeContexts(Object inputContext, Object feelContext, ObjectMapper mapper) {
    try {
      var wrappedInput = new MergedContext(mapper.convertValue(inputContext, MAP_TYPE_REF));
      var wrappedFeelContext = new MergedContext(mapper.convertValue(feelContext, MAP_TYPE_REF));
      var merged =
          mapper
              .readerForUpdating(wrappedInput)
              .treeToValue(mapper.valueToTree(wrappedFeelContext), MergedContext.class);
      return merged.context;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    if (property.getType().containedTypeCount() == 2) {
      var outputType = property.getType().containedType(1);
      return new FeelFunctionDeserializer<>(outputType, feelEngineWrapper);
    }
    return new FeelFunctionDeserializer<>(TypeFactory.unknownType(), feelEngineWrapper);
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
