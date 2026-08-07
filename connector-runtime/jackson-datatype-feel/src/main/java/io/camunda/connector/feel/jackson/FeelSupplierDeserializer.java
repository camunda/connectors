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

import io.camunda.connector.feel.FeelExpressionEvaluator;
import java.util.function.Supplier;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.type.TypeFactory;

class FeelSupplierDeserializer<OUT> extends AbstractFeelDeserializer<Supplier<OUT>> {

  private final JavaType outputType;

  protected FeelSupplierDeserializer(JavaType outputType, FeelExpressionEvaluator evaluator) {
    super(evaluator, false);
    this.outputType = outputType;
  }

  @SuppressWarnings("unchecked")
  @Override
  protected Supplier<OUT> doDeserialize(
      JsonNode node, Object feelContext, DeserializationContext deserializationContext) {
    return () ->
        (OUT)
            evaluateFeelExpression(
                deserializationContext, node.textValue(), outputType, feelContext);
  }

  @Override
  public FeelSupplierDeserializer<?> createContextual(
      DeserializationContext ctxt, BeanProperty property) {

    if (property.getType().containedTypeCount() == 1) {
      var outputType = property.getType().containedType(0);
      return new FeelSupplierDeserializer<>(outputType, evaluator);
    }
    return new FeelSupplierDeserializer<>(TypeFactory.unknownType(), evaluator);
  }
}
