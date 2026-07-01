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
package io.camunda.connector.optimizer.core;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.camunda.connector.generator.dsl.PropertyBinding;
import java.io.IOException;

/**
 * Dispatches {@link PropertyBinding} JSON to the matching record subtype via its {@code type}.
 *
 * <p>Each binding kind has required fields ({@code name}, {@code key}, {@code property}, …); we
 * fail loudly on any missing field rather than constructing a binding with empty-string identifiers
 * — empty identifiers would pass through the optimizer and silently land in the downstream BPMN,
 * where they're invalid.
 */
public class PropertyBindingDeserializer extends JsonDeserializer<PropertyBinding> {

  @Override
  public PropertyBinding deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.readValueAsTree();
    if (!node.hasNonNull("type")) {
      throw new IOException(
          "PropertyBinding is missing a \"type\" discriminator at " + p.currentLocation());
    }
    String type = node.get("type").asText();
    return switch (type) {
      case "zeebe:input" -> new PropertyBinding.ZeebeInput(requireField(node, "name", type, p));
      case "zeebe:taskHeader" ->
          new PropertyBinding.ZeebeTaskHeader(requireField(node, "key", type, p));
      case "zeebe:taskDefinition" ->
          new PropertyBinding.ZeebeTaskDefinition(requireField(node, "property", type, p));
      case "zeebe:property" ->
          new PropertyBinding.ZeebeProperty(requireField(node, "name", type, p));
      case "bpmn:Message#zeebe:subscription#property" ->
          new PropertyBinding.ZeebeSubscriptionProperty(requireField(node, "name", type, p));
      case "bpmn:Message#property" ->
          new PropertyBinding.MessageProperty(requireField(node, "name", type, p));
      case "zeebe:linkedResource" ->
          new PropertyBinding.ZeebeLinkedResource(
              requireField(node, "linkName", type, p), requireField(node, "property", type, p));
      default ->
          throw new IOException(
              "Unknown PropertyBinding type \"" + type + "\" at " + p.currentLocation());
    };
  }

  private static String requireField(JsonNode node, String name, String type, JsonParser p)
      throws IOException {
    if (!node.hasNonNull(name)) {
      throw new IOException(
          "PropertyBinding of type \""
              + type
              + "\" is missing required field \""
              + name
              + "\" at "
              + p.currentLocation());
    }
    String value = node.get(name).asText();
    if (value.isEmpty()) {
      throw new IOException(
          "PropertyBinding of type \""
              + type
              + "\" has empty \""
              + name
              + "\" at "
              + p.currentLocation());
    }
    return value;
  }
}
