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
import io.camunda.connector.generator.dsl.BooleanProperty;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.NumberProperty;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.dsl.TextProperty;
import io.camunda.connector.generator.java.annotation.FeelMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a {@link Property} from JSON by inspecting the {@code "type"} discriminator field and
 * dispatching to the matching concrete subtype's builder.
 *
 * <p>The core DSL doesn't carry Jackson polymorphism annotations, so Jackson can't pick a subtype
 * on its own. This deserializer reads the JSON tree and constructs the right subtype manually via
 * its public builder. It is strict about its input: required discriminator fields, well-typed
 * scalar fields, and known enum values are all enforced with locations included in the error.
 */
public class PropertyDeserializer extends JsonDeserializer<Property> {

  @Override
  public Property deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.readValueAsTree();
    if (!node.hasNonNull("type")) {
      throw new IOException(
          "Property is missing a \"type\" discriminator at " + p.currentLocation());
    }
    String type = node.get("type").asText();

    PropertyBuilder builder =
        switch (type) {
          case "Hidden" -> HiddenProperty.builder();
          case "String" -> StringProperty.builder();
          case "Text" -> TextProperty.builder();
          case "Number" -> NumberProperty.builder();
          case "Boolean" -> BooleanProperty.builder();
          case "Dropdown" -> DropdownProperty.builder();
          default ->
              throw new IOException(
                  "Unknown Property type \"" + type + "\" at " + p.currentLocation());
        };

    populateCommon(builder, node, ctxt, p);

    if (builder instanceof DropdownProperty.DropdownPropertyBuilder dropdownBuilder) {
      dropdownBuilder.choices(readChoices(node, p));
    }

    try {
      return builder.build();
    } catch (IllegalStateException e) {
      throw new IOException(
          "Failed to build Property of type \""
              + type
              + "\" at "
              + p.currentLocation()
              + ": "
              + e.getMessage(),
          e);
    }
  }

  private static void populateCommon(
      PropertyBuilder builder, JsonNode node, DeserializationContext ctxt, JsonParser p)
      throws IOException {
    if (node.hasNonNull("id")) builder.id(node.get("id").asText());
    if (node.hasNonNull("label")) builder.label(node.get("label").asText());
    if (node.hasNonNull("description")) builder.description(node.get("description").asText());
    if (node.hasNonNull("group")) builder.group(node.get("group").asText());
    if (node.hasNonNull("tooltip")) builder.tooltip(node.get("tooltip").asText());
    if (node.hasNonNull("placeholder")) builder.placeholder(node.get("placeholder").asText());

    if (node.hasNonNull("optional")) {
      JsonNode optional = node.get("optional");
      if (!optional.isBoolean()) {
        throw new IOException(
            "Property \"optional\" must be a boolean (got "
                + optional.getNodeType()
                + ") at "
                + p.currentLocation());
      }
      builder.optional(optional.booleanValue());
    }

    if (node.hasNonNull("feel")) {
      String feelText = node.get("feel").asText();
      builder.feel(parseFeel(feelText, p));
    }

    if (node.hasNonNull("binding")) {
      builder.binding(ctxt.readTreeAsValue(node.get("binding"), PropertyBinding.class));
    }

    if (node.hasNonNull("condition")) {
      builder.condition(ctxt.readTreeAsValue(node.get("condition"), PropertyCondition.class));
    }

    if (node.hasNonNull("constraints")) {
      builder.constraints(ctxt.readTreeAsValue(node.get("constraints"), PropertyConstraints.class));
    }

    if (node.hasNonNull("exampleValue")) {
      builder.exampleValue(ctxt.readTreeAsValue(node.get("exampleValue"), Object.class));
    }

    if (node.hasNonNull("generatedValue")) {
      JsonNode gv = node.get("generatedValue");
      String gvType = gv.path("type").asText("");
      if (!"uuid".equals(gvType)) {
        throw new IOException(
            "Unsupported generatedValue.type \""
                + gvType
                + "\" — only \"uuid\" is currently supported, at "
                + p.currentLocation());
      }
      builder.generatedValue();
    } else if (node.hasNonNull("value")) {
      builder.value(ctxt.readTreeAsValue(node.get("value"), Object.class));
    }
  }

  private static List<DropdownProperty.DropdownChoice> readChoices(JsonNode node, JsonParser p)
      throws IOException {
    if (!node.hasNonNull("choices")) {
      // Absent / null choices is legal at this layer; the build() call may reject it downstream.
      return null;
    }
    JsonNode choicesNode = node.get("choices");
    if (!choicesNode.isArray()) {
      throw new IOException(
          "Dropdown \"choices\" must be an array (got "
              + choicesNode.getNodeType()
              + ") at "
              + p.currentLocation());
    }
    List<DropdownProperty.DropdownChoice> choices = new ArrayList<>();
    for (JsonNode choiceNode : choicesNode) {
      if (!choiceNode.hasNonNull("value")) {
        throw new IOException(
            "Dropdown choice is missing a \"value\" field at " + p.currentLocation());
      }
      String name = choiceNode.path("name").asText(null);
      String value = choiceNode.get("value").asText();
      choices.add(new DropdownProperty.DropdownChoice(name, value));
    }
    return choices;
  }

  private static FeelMode parseFeel(String text, JsonParser p) throws IOException {
    // The generator serialises FeelMode.staticFeel as "static" (see FeelModelSerializer).
    if ("static".equals(text)) {
      return FeelMode.staticFeel;
    }
    try {
      return FeelMode.valueOf(text);
    } catch (IllegalArgumentException e) {
      throw new IOException("Unknown feel mode \"" + text + "\" at " + p.currentLocation(), e);
    }
  }
}
