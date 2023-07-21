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
package io.camunda.connector.impl.feel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.io.IOException;
import java.util.Map;

/**
 * A Jackson deserializer for FEEL expressions. It can be used to deserialize a string that contains
 * a FEEL expression, e.g. in inbound connector properties. NB: for outbound connectors, FEEL
 * expressions in connector variables are evaluated by Zeebe, so this deserializer is not needed.
 */
public class FeelDeserializer extends AbstractFeelDeserializer<Object> {

  private final JavaType outputType;
  private static final FeelEngineWrapper FEEL_ENGINE_WRAPPER = new FeelEngineWrapper();

  public FeelDeserializer() { // needed for references in @JsonDeserialize
    this(FEEL_ENGINE_WRAPPER, TypeFactory.unknownType());
  }

  protected FeelDeserializer(FeelEngineWrapper feelEngineWrapper, JavaType outputType) {
    super(feelEngineWrapper, true);
    this.outputType = outputType;
  }

  @Override
  protected Object doDeserialize(JsonNode node, ObjectMapper mapper)
      throws JsonProcessingException {
    if (isFeelExpression(node.textValue())) {
      return FEEL_ENGINE_WRAPPER.evaluate(node.textValue(), Map.of(), outputType);
    }
    if (node.isTextual()) {
      try {
        // check if this string contains a JSON object/array/etc inside (i.e. it's not just a
        // string)
        return mapper.readValue(node.textValue(), outputType);
      } catch (IOException e) {
        // ignore, this is just a string, we will take care of it below
      }
    }
    return mapper.treeToValue(node, outputType);
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    return new FeelDeserializer(FEEL_ENGINE_WRAPPER, property.getType());
  }
}
