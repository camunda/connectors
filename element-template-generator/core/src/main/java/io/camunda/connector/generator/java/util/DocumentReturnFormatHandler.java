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
package io.camunda.connector.generator.java.util;

import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.DocumentReturnFormat;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.FieldVisibility;
import io.camunda.connector.generator.java.processor.TemplatePropertyAnnotationProcessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * Generates the property tree backing {@link DocumentReturnFormat}: a dropdown that selects the
 * user's download response format ({@code Document reference} / {@code as text} / {@code as JSON})
 * plus a conditional encoding sub-field shown only when {@code TEXT} is selected.
 *
 * <p>Bindings are always root-level: dropdown → {@code documentReturnFormat.choice}, encoding →
 * {@code documentReturnFormat.encoding}. The fixed binding lets the runtime read back the value via
 * {@code context.readDocumentReturnFormat()} without per-connector path configuration.
 */
final class DocumentReturnFormatHandler {

  static final String DROPDOWN_ID = "documentReturnFormat";
  static final String ENCODING_ID = "documentReturnFormatEncoding";
  static final String CHOICE_BINDING = "documentReturnFormat.choice";
  static final String ENCODING_BINDING = "documentReturnFormat.encoding";

  private static final Map<DocumentReturnChoice, DropdownChoice> CHOICE_LABELS =
      new EnumMap<>(DocumentReturnChoice.class);

  static {
    CHOICE_LABELS.put(
        DocumentReturnChoice.DOCUMENT,
        new DropdownChoice("Document reference", DocumentReturnChoice.DOCUMENT.name()));
    CHOICE_LABELS.put(
        DocumentReturnChoice.TEXT, new DropdownChoice("as text", DocumentReturnChoice.TEXT.name()));
    CHOICE_LABELS.put(
        DocumentReturnChoice.JSON, new DropdownChoice("as JSON", DocumentReturnChoice.JSON.name()));
  }

  private DocumentReturnFormatHandler() {}

  static List<PropertyBuilder> handleDocumentReturnFormat(DocumentReturnFormat annotation) {
    String group = blankToNull(annotation.group());
    PropertyCondition userCondition = userCondition(annotation);

    DocumentReturnChoice[] supportedFormats = annotation.supportedFormats();
    if (supportedFormats.length == 0) {
      throw new IllegalStateException(
          "@DocumentReturnFormat.supportedFormats must not be empty — the generated dropdown"
              + " needs at least one choice.");
    }
    DocumentReturnChoice defaultFormat = annotation.defaultFormat();
    boolean defaultInSupported = false;
    for (DocumentReturnChoice c : supportedFormats) {
      if (c == defaultFormat) {
        defaultInSupported = true;
        break;
      }
    }
    if (!defaultInSupported) {
      throw new IllegalStateException(
          "@DocumentReturnFormat.defaultFormat ("
              + defaultFormat
              + ") is not listed in supportedFormats "
              + Arrays.toString(supportedFormats)
              + " — the default must be one of the supported choices.");
    }

    List<DropdownChoice> choices = new ArrayList<>();
    for (DocumentReturnChoice c : supportedFormats) {
      DropdownChoice mapped = CHOICE_LABELS.get(c);
      if (mapped == null) {
        throw new IllegalStateException("Unknown DocumentReturnChoice: " + c);
      }
      choices.add(mapped);
    }

    // Encoding sub-field: only visible when TEXT is selected. Registered as a dependant of the
    // dropdown so DiscriminatorPropertyBuilder rewrites its condition if the dropdown id is
    // path-prefixed by sealed-subtype nesting (keeps the field visible in Modeler).
    var dependants = new ArrayList<PropertyBuilder>();
    if (annotation.encoding() != FieldVisibility.HIDDEN) {
      PropertyCondition encodingCondition =
          combine(userCondition, new Equals(DROPDOWN_ID, DocumentReturnChoice.TEXT.name()));
      var encoding =
          StringProperty.builder()
              .feel(FeelMode.optional)
              .id(ENCODING_ID)
              .label("Encoding")
              .description("Character set used to decode the response. Default UTF-8.")
              .value(annotation.defaultEncoding())
              .binding(new ZeebeInput(ENCODING_BINDING))
              .group(group)
              .condition(encodingCondition);
      dependants.add(encoding);
    }

    var dropdown = new DiscriminatorPropertyBuilder().dependantProperties(dependants);
    dropdown.choices(choices);
    dropdown.feel(FeelMode.disabled);
    dropdown
        .id(DROPDOWN_ID)
        .label(blankToNull(annotation.label()))
        .description(blankToNull(annotation.description()))
        .tooltip(blankToNull(annotation.tooltip()))
        .value(annotation.defaultFormat().name())
        .binding(new ZeebeInput(CHOICE_BINDING))
        .group(group)
        .condition(userCondition);

    var result = new ArrayList<PropertyBuilder>();
    result.add(dropdown);
    result.addAll(dependants);
    return result;
  }

  private static PropertyCondition userCondition(DocumentReturnFormat annotation) {
    var condition = annotation.condition();
    if (StringUtils.isBlank(condition.property())) {
      return null;
    }
    return TemplatePropertyAnnotationProcessor.transformToCondition(condition);
  }

  private static PropertyCondition combine(PropertyCondition parent, PropertyCondition own) {
    if (parent == null) {
      return own;
    }
    if (parent instanceof AllMatch allMatch) {
      var combined = new ArrayList<>(allMatch.allMatch());
      combined.add(own);
      return new AllMatch(combined);
    }
    return new AllMatch(List.of(parent, own));
  }

  private static String blankToNull(String s) {
    return StringUtils.isBlank(s) ? null : s;
  }
}
