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

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
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
 * Generates the property tree backing {@link TemplateDocumentProperty}: a set of user-facing
 * sub-fields (source dropdown, per-source content/url/etc.) bound to flat helper variables, plus a
 * trailing {@code Hidden} composer input mapping that assembles those helpers into the canonical
 * {@code DocumentReferenceModel} JSON at the field's actual binding path.
 */
final class DocumentPropertyHandler {

  static final String CHOICE_CAMUNDA = "camunda";
  static final String CHOICE_INLINE = "inline";
  static final String CHOICE_EXTERNAL = "external";
  static final String CHOICE_SINGLE = "single";
  static final String CHOICE_MULTIPLE = "multiple";
  static final String CHOICE_NONE = "none";
  static final String CHOICE_YES = "yes";
  static final String CHOICE_NO = "no";

  private static final String DOCUMENT_TYPE_KEY = "camunda.document.type";
  private static final String DOCUMENT_TYPE_INLINE = "inline";
  private static final String DOCUMENT_TYPE_EXTERNAL = "external";

  private static final String LIST_MODE_LABEL = "Number of documents";
  private static final String OPTIONAL_SINGLE_LABEL = "Attach document?";

  private static final List<DropdownChoice> SOURCE_CHOICES =
      List.of(
          new DropdownChoice("Camunda Document", CHOICE_CAMUNDA),
          new DropdownChoice("Inline Content", CHOICE_INLINE),
          new DropdownChoice("From URL", CHOICE_EXTERNAL));

  private static final DropdownChoice CHOICE_SINGLE_ENTRY =
      new DropdownChoice("Single document", CHOICE_SINGLE);
  private static final DropdownChoice CHOICE_MULTIPLE_ENTRY =
      new DropdownChoice("Multiple documents", CHOICE_MULTIPLE);
  private static final DropdownChoice CHOICE_NONE_ENTRY = new DropdownChoice("None", CHOICE_NONE);

  private static final List<DropdownChoice> LIST_MODE_CHOICES_MANDATORY =
      List.of(CHOICE_SINGLE_ENTRY, CHOICE_MULTIPLE_ENTRY);

  private static final List<DropdownChoice> LIST_MODE_CHOICES_OPTIONAL =
      List.of(CHOICE_NONE_ENTRY, CHOICE_SINGLE_ENTRY, CHOICE_MULTIPLE_ENTRY);

  private static final List<DropdownChoice> SINGLE_OPTIONAL_MODE_CHOICES =
      List.of(new DropdownChoice("No", CHOICE_NO), new DropdownChoice("Yes", CHOICE_YES));

  private DocumentPropertyHandler() {}

  static List<PropertyBuilder> handleDocumentProperty(
      Class<?> declaredType, String declaredName, TemplateDocumentProperty annotation) {
    if (!Document.class.isAssignableFrom(declaredType)) {
      throw new IllegalStateException(
          "@TemplateDocumentProperty on '"
              + declaredName
              + "' requires type Document, got "
              + declaredType.getSimpleName());
    }
    String targetPath = resolveBindingRoot(annotation, declaredName);
    String targetParent = helperTargetParent(targetPath);
    String localPrefix = toLocalPrefix(targetPath);
    String group = blankToNull(annotation.group());
    PropertyCondition parentCondition = parentCondition(annotation);

    SingleDocFields fields = singleDocFields(localPrefix);

    if (annotation.optional()) {
      return buildOptionalSingleProperty(
          localPrefix, targetPath, targetParent, group, parentCondition, annotation, fields);
    }

    var dependants = new ArrayList<PropertyBuilder>();
    addSingleSubProperties(dependants, fields, parentCondition, annotation, group, targetParent);

    var result = new ArrayList<PropertyBuilder>();
    result.add(
        buildSingleSourceDropdown(
            fields,
            parentCondition,
            blankToNull(annotation.description()),
            blankToNull(annotation.tooltip()),
            group,
            targetParent,
            dependants));
    result.addAll(dependants);
    result.add(
        composerProperty(
            targetPath,
            singleDocComposerExpression(fields),
            parentCondition,
            group,
            resolveComposerId(annotation, targetPath)));
    return result;
  }

  private static List<PropertyBuilder> buildOptionalSingleProperty(
      String localPrefix,
      String targetPath,
      String targetParent,
      String group,
      PropertyCondition parentCondition,
      TemplateDocumentProperty annotation,
      SingleDocFields fields) {
    String modeId = localPrefix + "_documentMode";
    PropertyCondition yesCondition = combine(parentCondition, new Equals(modeId, CHOICE_YES));

    var subFields = new ArrayList<PropertyBuilder>();
    addSingleSubProperties(subFields, fields, yesCondition, annotation, group, targetParent);

    var sourceDropdown =
        buildSingleSourceDropdown(fields, yesCondition, null, null, group, targetParent, subFields);

    var modeDependants = new ArrayList<PropertyBuilder>();
    modeDependants.add(sourceDropdown);
    modeDependants.addAll(subFields);

    var modeDropdown = new DiscriminatorPropertyBuilder().dependantProperties(modeDependants);
    modeDropdown.choices(SINGLE_OPTIONAL_MODE_CHOICES);
    modeDropdown.feel(FeelMode.disabled);
    modeDropdown
        .id(modeId)
        .label(OPTIONAL_SINGLE_LABEL)
        .description(blankToNull(annotation.description()))
        .tooltip(blankToNull(annotation.tooltip()))
        .value(CHOICE_NO)
        .binding(bindingFor(modeId, targetParent))
        .group(group)
        .condition(parentCondition);

    var result = new ArrayList<PropertyBuilder>();
    result.add(modeDropdown);
    result.addAll(modeDependants);
    result.add(
        composerProperty(
            targetPath,
            optionalSingleDocComposerExpression(modeId, fields),
            parentCondition,
            group,
            resolveComposerId(annotation, targetPath)));
    return result;
  }

  static List<PropertyBuilder> handleListDocumentProperty(
      Class<?> elementType, String declaredName, TemplateDocumentProperty annotation) {
    if (!Document.class.isAssignableFrom(elementType)) {
      throw new IllegalStateException(
          "@TemplateDocumentProperty on '"
              + declaredName
              + "' requires List<Document>, got List<"
              + elementType.getSimpleName()
              + ">");
    }
    String targetPath = resolveBindingRoot(annotation, declaredName);
    String targetParent = helperTargetParent(targetPath);
    String localPrefix = toLocalPrefix(targetPath);
    String group = blankToNull(annotation.group());
    PropertyCondition parentCondition = parentCondition(annotation);

    String modeId = localPrefix + "_documentMode";
    SingleDocFields single = listSingleFields(localPrefix);
    String multipleExpressionId = localPrefix + "_multiple_expression";

    PropertyCondition singleModeCondition =
        combine(parentCondition, new Equals(modeId, CHOICE_SINGLE));
    PropertyCondition multipleModeCondition =
        combine(parentCondition, new Equals(modeId, CHOICE_MULTIPLE));

    var singleSubFields = new ArrayList<PropertyBuilder>();
    addSingleSubProperties(
        singleSubFields, single, singleModeCondition, annotation, group, targetParent);

    var singleSourceDropdown =
        buildSingleSourceDropdown(
            single, singleModeCondition, null, null, group, targetParent, singleSubFields);

    var multipleExpression =
        StringProperty.builder().feel(FeelMode.required).constraints(notEmpty());
    multipleExpression
        .id(multipleExpressionId)
        .label("Documents")
        .binding(bindingFor(multipleExpressionId, targetParent))
        .group(group)
        .condition(multipleModeCondition);

    var modeDependants = new ArrayList<PropertyBuilder>();
    modeDependants.add(singleSourceDropdown);
    modeDependants.addAll(singleSubFields);
    modeDependants.add(multipleExpression);

    boolean optional = annotation.optional();
    var modeDropdown = new DiscriminatorPropertyBuilder().dependantProperties(modeDependants);
    modeDropdown.choices(optional ? LIST_MODE_CHOICES_OPTIONAL : LIST_MODE_CHOICES_MANDATORY);
    modeDropdown.feel(FeelMode.disabled);
    modeDropdown
        .id(modeId)
        .label(LIST_MODE_LABEL)
        .description(blankToNull(annotation.description()))
        .tooltip(blankToNull(annotation.tooltip()))
        .value(optional ? CHOICE_NONE : CHOICE_SINGLE)
        .binding(bindingFor(modeId, targetParent))
        .group(group)
        .condition(parentCondition);

    var result = new ArrayList<PropertyBuilder>();
    result.add(modeDropdown);
    result.addAll(modeDependants);

    String composerExpression = listDocComposerExpression(modeId, single, multipleExpressionId);
    result.add(
        composerProperty(
            targetPath,
            composerExpression,
            parentCondition,
            group,
            resolveComposerId(annotation, targetPath)));
    return result;
  }

  private static DiscriminatorPropertyBuilder buildSingleSourceDropdown(
      SingleDocFields fields,
      PropertyCondition condition,
      String description,
      String tooltip,
      String group,
      String targetParent,
      List<PropertyBuilder> dependants) {
    var dropdown = new DiscriminatorPropertyBuilder().dependantProperties(dependants);
    dropdown.choices(SOURCE_CHOICES);
    dropdown.feel(FeelMode.disabled);
    dropdown
        .id(fields.sourceId)
        .label("Document source")
        .description(description)
        .tooltip(tooltip)
        .value(CHOICE_CAMUNDA)
        .binding(bindingFor(fields.sourceId, targetParent))
        .group(group)
        .condition(condition);
    return dropdown;
  }

  private static void addSingleSubProperties(
      List<PropertyBuilder> out,
      SingleDocFields fields,
      PropertyCondition parentCondition,
      TemplateDocumentProperty annotation,
      String group,
      String targetParent) {

    PropertyCondition camundaCondition =
        combine(parentCondition, new Equals(fields.sourceId, CHOICE_CAMUNDA));
    PropertyCondition inlineCondition =
        combine(parentCondition, new Equals(fields.sourceId, CHOICE_INLINE));
    PropertyCondition externalCondition =
        combine(parentCondition, new Equals(fields.sourceId, CHOICE_EXTERNAL));

    out.add(
        stringSub(
            fields.camundaRefId,
            "Camunda document",
            FeelMode.required,
            true,
            group,
            targetParent,
            camundaCondition));

    out.add(
        stringSub(
            fields.inlineContentId,
            "Content",
            FeelMode.optional,
            true,
            group,
            targetParent,
            inlineCondition));
    if (annotation.fileName() != FieldVisibility.HIDDEN) {
      boolean required = annotation.fileName() == FieldVisibility.REQUIRED;
      out.add(
          stringSub(
              fields.inlineFileNameId,
              "File name",
              FeelMode.optional,
              required,
              group,
              targetParent,
              inlineCondition));
    }
    if (annotation.contentType() != FieldVisibility.HIDDEN) {
      boolean required = annotation.contentType() == FieldVisibility.REQUIRED;
      out.add(
          stringSub(
              fields.inlineContentTypeId,
              "Content type",
              FeelMode.optional,
              required,
              group,
              targetParent,
              inlineCondition));
    }

    out.add(
        stringSub(
            fields.externalUrlId,
            "URL",
            FeelMode.optional,
            true,
            group,
            targetParent,
            externalCondition));
    if (annotation.fileName() != FieldVisibility.HIDDEN) {
      boolean required = annotation.fileName() == FieldVisibility.REQUIRED;
      out.add(
          stringSub(
              fields.externalFileNameId,
              "File name",
              FeelMode.optional,
              required,
              group,
              targetParent,
              externalCondition));
    }
  }

  private static PropertyBuilder stringSub(
      String id,
      String label,
      FeelMode feel,
      boolean notEmpty,
      String group,
      String targetParent,
      PropertyCondition condition) {
    var builder = StringProperty.builder().feel(feel);
    builder
        .id(id)
        .label(label)
        .binding(bindingFor(id, targetParent))
        .group(group)
        .condition(condition);
    if (notEmpty) {
      builder.constraints(notEmpty());
    }
    return builder;
  }

  private static ZeebeInput bindingFor(String leafName, String targetParent) {
    return new ZeebeInput(targetParent.isEmpty() ? leafName : targetParent + "." + leafName);
  }

  private static String helperTargetParent(String composerTarget) {
    int lastDot = composerTarget.lastIndexOf('.');
    return lastDot < 0 ? "" : composerTarget.substring(0, lastDot);
  }

  private static PropertyBuilder composerProperty(
      String targetPath,
      String feelExpression,
      PropertyCondition condition,
      String group,
      String composerId) {
    var composer = HiddenProperty.builder();
    composer
        .id(composerId)
        .value("=" + feelExpression)
        .binding(new ZeebeInput(targetPath))
        .group(group)
        .condition(condition);
    return composer;
  }

  private static String resolveComposerId(TemplateDocumentProperty annotation, String targetPath) {
    String custom = blankToNull(annotation.id());
    return custom != null ? custom : targetPath + "__composer";
  }

  private static String singleDocComposerExpression(SingleDocFields fields) {
    return """
        if %1$s = "camunda" then %2$s \
        else if %1$s = "inline" then %3$s \
        else if %1$s = "external" then %4$s \
        else null"""
        .formatted(
            fields.sourceId,
            fields.camundaRefId,
            inlineObjectLiteral(fields),
            externalObjectLiteral(fields));
  }

  private static String optionalSingleDocComposerExpression(String modeId, SingleDocFields fields) {
    return """
        if %1$s = "yes" then (%2$s) \
        else null"""
        .formatted(modeId, singleDocComposerExpression(fields));
  }

  private static String listDocComposerExpression(
      String modeId, SingleDocFields single, String multipleExpressionId) {
    return """
        if %1$s = "multiple" then %2$s \
        else if %1$s = "single" then (if %3$s = "camunda" then [%4$s] \
        else if %3$s = "inline" then [%5$s] \
        else if %3$s = "external" then [%6$s] \
        else null) \
        else null"""
        .formatted(
            modeId,
            multipleExpressionId,
            single.sourceId,
            single.camundaRefId,
            inlineObjectLiteral(single),
            externalObjectLiteral(single));
  }

  private static String inlineObjectLiteral(SingleDocFields f) {
    return """
        { "%s": "%s", content: %s, name: %s, contentType: %s }"""
        .formatted(
            DOCUMENT_TYPE_KEY,
            DOCUMENT_TYPE_INLINE,
            f.inlineContentId,
            f.inlineFileNameId,
            f.inlineContentTypeId);
  }

  private static String externalObjectLiteral(SingleDocFields f) {
    return """
        { "%s": "%s", url: %s, name: %s }"""
        .formatted(
            DOCUMENT_TYPE_KEY, DOCUMENT_TYPE_EXTERNAL, f.externalUrlId, f.externalFileNameId);
  }

  private static SingleDocFields singleDocFields(String prefix) {
    return new SingleDocFields(
        prefix + "_documentSource",
        prefix + "_camundaReference",
        prefix + "_inline_content",
        prefix + "_inline_fileName",
        prefix + "_inline_contentType",
        prefix + "_external_url",
        prefix + "_external_fileName");
  }

  private static SingleDocFields listSingleFields(String prefix) {
    return singleDocFields(prefix + "_single");
  }

  private record SingleDocFields(
      String sourceId,
      String camundaRefId,
      String inlineContentId,
      String inlineFileNameId,
      String inlineContentTypeId,
      String externalUrlId,
      String externalFileNameId) {}

  private static String toLocalPrefix(String targetPath) {
    return targetPath.replace('.', '_');
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
    return declaredName;
  }

  private static String blankToNull(String s) {
    return StringUtils.isBlank(s) ? null : s;
  }

  private static PropertyConstraints notEmpty() {
    return PropertyConstraints.builder().notEmpty(true).build();
  }
}
