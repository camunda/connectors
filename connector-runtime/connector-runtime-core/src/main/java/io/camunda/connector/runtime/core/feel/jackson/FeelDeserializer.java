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
package io.camunda.connector.runtime.core.feel.jackson;

import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import io.camunda.connector.runtime.core.feel.FeelEngineWrapper;
import java.util.Map;

public class FeelDeserializer extends AbstractFeelDeserializer<Object> {

  private final Class<?> outputType;

  public FeelDeserializer() { // needed for references in @JsonDeserialize
    this(new FeelEngineWrapper(), Object.class);
  }

  protected FeelDeserializer(FeelEngineWrapper feelEngineWrapper, Class<?> outputType) {
    super(feelEngineWrapper);
    this.outputType = outputType;
  }

  @Override
  protected Object doDeserialize(String expression) {
    return feelEngineWrapper.evaluate(expression, Map.of(), outputType);
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    return new FeelDeserializer(feelEngineWrapper, property.getType().getRawClass());
  }
}
