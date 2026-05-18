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

import static io.camunda.connector.generator.java.util.TemplatePropertiesUtil.createBinding;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.FieldVisibility;
import io.camunda.connector.generator.java.annotation.TemplateDocumentProperty;
import io.camunda.connector.generator.java.processor.TemplatePropertyAnnotationProcessor;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

/**
 * Generates the property tree backing {@link TemplateDocumentProperty}: a discriminator dropdown
 * and the per-source sub-fields. The bindings use dotted paths so Zeebe builds a nested object at
 * the document field's path; the connector runtime then unwraps that object into a {@code Document}
 * via the wrapper-aware Jackson deserializer in {@code jackson-datatype-document}.
 *
 * <p>Earlier iterations used a hidden FEEL composer to assemble the document JSON at the BPMN
 * level. That doesn't work because Zeebe input mappings evaluate their FEEL source against the
 * process-instance scope, not against sibling input-mapping results — so the composer couldn't see
 * the user's sub-field values. Composition is now done in Java instead.
 */
final class DocumentPropertyHandler {

  static final String CHOICE_CAMUNDA = "camunda";
  static final String CHOICE_INLINE = "inline";
  static final String CHOICE_EXTERNAL = "external";
  static final String CHOICE_SINGLE = "single";
  static final String CHOICE_MULTIPLE = "multiple";

  private static final List<DropdownChoice> SOURCE_CHOICES =
      List.of(
          new DropdownChoice("Camunda Document", CHOICE_CAMUNDA),
          new DropdownChoice("Inline Content", CHOICE_INLINE),
          new DropdownChoice("From URL", CHOICE_EXTERNAL));

  private static final List<DropdownChoice> MODE_CHOICES =
      List.of(
          new DropdownChoice("Single document", CHOICE_SINGLE),
          new DropdownChoice("Multiple documents", CHOICE_MULTIPLE));

  private DocumentPropertyHandler() {}

  static List<PropertyBuilder> handleDocumentProperty(
      Class<?> declaredType,
      String declaredName,
      TemplateDocumentProperty annotation,
      TemplateGenerationContext context) {
    if (!Document.class.isAssignableFrom(declaredType)) {
      throw new IllegalStateException(
          "@TemplateDocumentProperty on '"
              + declaredName
              + "' requires type Document, got "
              + declaredType.getSimpleName());
    }
    String root = resolveBindingRoot(annotation, declaredName);
    String group = blankToNull(annotation.group());
    PropertyCondition parentCondition = parentCondition(annotation);

    String sourceDropdownId = root + ".documentSource";

    var dependants = new ArrayList<PropertyBuilder>();
    addSingleDocSubProperties(
        dependants, root, sourceDropdownId, parentCondition, annotation, group, context);

    var sourceDropdown =
        new DiscriminatorPropertyBuilder().dependantProperties(dependants).choices(SOURCE_CHOICES);
    sourceDropdown
        .id(sourceDropdownId)
        .label(topLabel(annotation, "Document source"))
        .description(blankToNull(annotation.description()))
        .tooltip(blankToNull(annotation.tooltip()))
        .value(CHOICE_CAMUNDA)
        .feel(FeelMode.disabled)
        .binding(createBinding(sourceDropdownId, context))
        .group(group)
        .condition(parentCondition);

    var result = new ArrayList<PropertyBuilder>();
    result.add(sourceDropdown);
    result.addAll(dependants);
    return result;
  }

  static List<PropertyBuilder> handleListDocumentProperty(
      Class<?> elementType,
      String declaredName,
      TemplateDocumentProperty annotation,
      TemplateGenerationContext context) {
    if (!Document.class.isAssignableFrom(elementType)) {
      throw new IllegalStateException(
          "@TemplateDocumentProperty on '"
              + declaredName
              + "' requires List<Document>, got List<"
              + elementType.getSimpleName()
              + ">");
    }
    String root = resolveBindingRoot(annotation, declaredName);
    String group = blankToNull(annotation.group());
    PropertyCondition parentCondition = parentCondition(annotation);

    String modeDropdownId = root + ".documentMode";
    String singleRoot = root + ".single";
    String singleSourceDropdownId = singleRoot + ".documentSource";

    var dependants = new ArrayList<PropertyBuilder>();

    PropertyCondition singleModeCondition =
        combine(parentCondition, new Equals(modeDropdownId, CHOICE_SINGLE));
    PropertyCondition multipleModeCondition =
        combine(parentCondition, new Equals(modeDropdownId, CHOICE_MULTIPLE));

    var singleSourceDropdown =
        DropdownProperty.builder().choices(SOURCE_CHOICES).feel(FeelMode.disabled);
    singleSourceDropdown
        .id(singleSourceDropdownId)
        .label("Document source")
        .value(CHOICE_CAMUNDA)
        .binding(createBinding(singleSourceDropdownId, context))
        .group(group)
        .condition(singleModeCondition);
    dependants.add(singleSourceDropdown);

    addSingleDocSubProperties(
        dependants,
        singleRoot,
        singleSourceDropdownId,
        singleModeCondition,
        annotation,
        group,
        context);

    String multipleExpressionId = root + ".multiple.expression";
    var multipleExpression =
        StringProperty.builder().feel(FeelMode.required).constraints(notEmpty());
    multipleExpression
        .id(multipleExpressionId)
        .label("Documents")
        .binding(createBinding(multipleExpressionId, context))
        .group(group)
        .condition(multipleModeCondition);
    dependants.add(multipleExpression);

    var modeDropdown =
        new DiscriminatorPropertyBuilder().dependantProperties(dependants).choices(MODE_CHOICES);
    modeDropdown
        .id(modeDropdownId)
        .label(topLabel(annotation, "Number of documents"))
        .description(blankToNull(annotation.description()))
        .tooltip(blankToNull(annotation.tooltip()))
        .value(CHOICE_SINGLE)
        .feel(FeelMode.disabled)
        .binding(createBinding(modeDropdownId, context))
        .group(group)
        .condition(parentCondition);

    var result = new ArrayList<PropertyBuilder>();
    result.add(modeDropdown);
    result.addAll(dependants);
    return result;
  }

  private static void addSingleDocSubProperties(
      List<PropertyBuilder> out,
      String root,
      String sourceDropdownId,
      PropertyCondition parentCondition,
      TemplateDocumentProperty annotation,
      String group,
      TemplateGenerationContext context) {

    PropertyCondition camundaCondition =
        combine(parentCondition, new Equals(sourceDropdownId, CHOICE_CAMUNDA));
    PropertyCondition inlineCondition =
        combine(parentCondition, new Equals(sourceDropdownId, CHOICE_INLINE));
    PropertyCondition externalCondition =
        combine(parentCondition, new Equals(sourceDropdownId, CHOICE_EXTERNAL));

    out.add(
        stringSub(
            root + ".camundaReference",
            "Camunda document",
            FeelMode.required,
            true,
            null,
            group,
            camundaCondition,
            context));

    out.add(
        stringSub(
            root + ".inline.content",
            "Content",
            FeelMode.optional,
            true,
            null,
            group,
            inlineCondition,
            context));

    if (annotation.fileName() != FieldVisibility.HIDDEN) {
      boolean required = annotation.fileName() == FieldVisibility.REQUIRED;
      out.add(
          stringSub(
              root + ".inline.fileName",
              "File name",
              FeelMode.optional,
              required,
              null,
              group,
              inlineCondition,
              context));
    }

    if (annotation.contentType() != FieldVisibility.HIDDEN) {
      boolean required = annotation.contentType() == FieldVisibility.REQUIRED;
      out.add(
          stringSub(
              root + ".inline.contentType",
              "Content type",
              FeelMode.optional,
              required,
              null,
              group,
              inlineCondition,
              context));
    }

    out.add(
        stringSub(
            root + ".external.url",
            "URL",
            FeelMode.optional,
            true,
            null,
            group,
            externalCondition,
            context));

    if (annotation.fileName() != FieldVisibility.HIDDEN) {
      boolean required = annotation.fileName() == FieldVisibility.REQUIRED;
      out.add(
          stringSub(
              root + ".external.fileName",
              "File name",
              FeelMode.optional,
              required,
              null,
              group,
              externalCondition,
              context));
    }
  }

  private static PropertyBuilder stringSub(
      String id,
      String label,
      FeelMode feel,
      boolean notEmpty,
      String defaultValue,
      String group,
      PropertyCondition condition,
      TemplateGenerationContext context) {
    var builder = StringProperty.builder().feel(feel);
    builder
        .id(id)
        .label(label)
        .binding(createBinding(id, context))
        .group(group)
        .condition(condition);
    if (notEmpty) {
      builder.constraints(notEmpty());
    }
    if (defaultValue != null) {
      builder.value(defaultValue);
    }
    return builder;
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

  private static PropertyCondition parentCondition(TemplateDocumentProperty annotation) {
    var condition = annotation.condition();
    if (StringUtils.isBlank(condition.property())) {
      return null;
    }
    return TemplatePropertyAnnotationProcessor.transformToCondition(condition);
  }

  private static String resolveBindingRoot(
      TemplateDocumentProperty annotation, String declaredName) {
    if (!StringUtils.isBlank(annotation.binding().name())) {
      return annotation.binding().name();
    }
    if (!StringUtils.isBlank(annotation.id())) {
      return annotation.id();
    }
    return declaredName;
  }

  private static String topLabel(TemplateDocumentProperty annotation, String fallback) {
    return StringUtils.isBlank(annotation.label()) ? fallback : annotation.label();
  }

  private static String blankToNull(String s) {
    return StringUtils.isBlank(s) ? null : s;
  }

  private static PropertyConstraints notEmpty() {
    return PropertyConstraints.builder().notEmpty(true).build();
  }
}
