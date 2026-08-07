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
import io.camunda.connector.generator.dsl.PropertyCondition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches {@link PropertyCondition} JSON to the matching record subtype via the discriminating
 * field ({@code equals}, {@code oneOf}, {@code allMatch}, or {@code isActive}).
 *
 * <p>Exactly one discriminator field must be present and non-null. Zero, more than one, or a
 * present-but-null discriminator all fail loudly — a silently-accepted condition would round-trip
 * in the optimizer and land as a malformed binding in the BPMN.
 */
public class PropertyConditionDeserializer extends JsonDeserializer<PropertyCondition> {

  private static final List<String> DISCRIMINATORS =
      List.of("equals", "oneOf", "isActive", "allMatch");

  @Override
  public PropertyCondition deserialize(JsonParser p, DeserializationContext ctxt)
      throws IOException {
    JsonNode node = p.readValueAsTree();

    List<String> present = new ArrayList<>();
    for (String name : DISCRIMINATORS) {
      if (node.hasNonNull(name)) {
        present.add(name);
      }
    }
    if (present.isEmpty()) {
      throw new IOException(
          "PropertyCondition is missing a discriminator field (one of "
              + DISCRIMINATORS
              + ") at "
              + p.currentLocation());
    }
    if (present.size() > 1) {
      throw new IOException(
          "PropertyCondition has multiple discriminator fields "
              + present
              + " — exactly one of "
              + DISCRIMINATORS
              + " must be present, at "
              + p.currentLocation());
    }

    return switch (present.get(0)) {
      case "equals" -> deserializeEquals(node, ctxt, p);
      case "oneOf" -> deserializeOneOf(node, p);
      case "isActive" -> deserializeIsActive(node, p);
      case "allMatch" -> deserializeAllMatch(node, ctxt, p);
      default -> throw new IllegalStateException("unreachable: " + present.get(0));
    };
  }

  private static PropertyCondition.Equals deserializeEquals(
      JsonNode node, DeserializationContext ctxt, JsonParser p) throws IOException {
    String property = requireProperty(node, "equals", p);
    Object value = ctxt.readTreeAsValue(node.get("equals"), Object.class);
    return new PropertyCondition.Equals(property, value);
  }

  private static PropertyCondition.OneOf deserializeOneOf(JsonNode node, JsonParser p)
      throws IOException {
    String property = requireProperty(node, "oneOf", p);
    JsonNode oneOf = node.get("oneOf");
    if (!oneOf.isArray()) {
      throw new IOException(
          "PropertyCondition \"oneOf\" must be an array (got "
              + oneOf.getNodeType()
              + ") at "
              + p.currentLocation());
    }
    List<String> values = new ArrayList<>();
    for (JsonNode v : oneOf) {
      if (!v.isTextual()) {
        throw new IOException(
            "PropertyCondition \"oneOf\" entries must be strings (got "
                + v.getNodeType()
                + ") at "
                + p.currentLocation());
      }
      values.add(v.asText());
    }
    if (values.isEmpty()) {
      throw new IOException(
          "PropertyCondition \"oneOf\" must be non-empty at " + p.currentLocation());
    }
    return new PropertyCondition.OneOf(property, values);
  }

  private static PropertyCondition.IsActive deserializeIsActive(JsonNode node, JsonParser p)
      throws IOException {
    String property = requireProperty(node, "isActive", p);
    JsonNode isActive = node.get("isActive");
    if (!isActive.isBoolean()) {
      throw new IOException(
          "PropertyCondition \"isActive\" must be a boolean (got "
              + isActive.getNodeType()
              + ") at "
              + p.currentLocation());
    }
    return new PropertyCondition.IsActive(property, isActive.booleanValue());
  }

  private static PropertyCondition.AllMatch deserializeAllMatch(
      JsonNode node, DeserializationContext ctxt, JsonParser p) throws IOException {
    JsonNode allMatch = node.get("allMatch");
    if (!allMatch.isArray()) {
      throw new IOException(
          "PropertyCondition \"allMatch\" must be an array (got "
              + allMatch.getNodeType()
              + ") at "
              + p.currentLocation());
    }
    List<PropertyCondition> conditions = new ArrayList<>();
    for (JsonNode child : allMatch) {
      conditions.add(ctxt.readTreeAsValue(child, PropertyCondition.class));
    }
    return new PropertyCondition.AllMatch(conditions);
  }

  private static String requireProperty(JsonNode node, String discriminator, JsonParser p)
      throws IOException {
    if (!node.hasNonNull("property")) {
      throw new IOException(
          "PropertyCondition with \""
              + discriminator
              + "\" is missing required field \"property\" at "
              + p.currentLocation());
    }
    return node.get("property").asText();
  }
}
