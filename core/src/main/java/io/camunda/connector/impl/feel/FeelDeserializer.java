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

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.util.Map;

/**
 * A Jackson deserializer for FEEL expressions. It can be used to deserialize a string that contains
 * a FEEL expression, e.g. in inbound connector properties. NB: for outbound connectors, FEEL
 * expressions in connector variables are evaluated by Zeebe, so this deserializer is not needed.
 */
public class FeelDeserializer extends AbstractFeelDeserializer<Object> {

  private final Class<?> outputType;
  private static final FeelEngineWrapper FEEL_ENGINE_WRAPPER = new FeelEngineWrapper();

  public FeelDeserializer() { // needed for references in @JsonDeserialize
    this(FEEL_ENGINE_WRAPPER, Object.class);
  }

  protected FeelDeserializer(FeelEngineWrapper feelEngineWrapper, Class<?> outputType) {
    super(feelEngineWrapper, true);
    this.outputType = outputType;
  }

  @Override
  protected Object doDeserialize(String expression) {
    if (!isFeelExpression(expression)) {
      return expression;
    }
    return FEEL_ENGINE_WRAPPER.evaluate(expression, Map.of(), outputType);
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    return new FeelDeserializer(FEEL_ENGINE_WRAPPER, property.getType().getRawClass());
  }
}
