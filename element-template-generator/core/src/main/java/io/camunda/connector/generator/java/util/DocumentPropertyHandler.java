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
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.PropertyConstraints;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.DocumentSource;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.FieldVisibility;
import io.camunda.connector.generator.java.annotation.TemplateDocumentProperty;
import io.camunda.connector.generator.java.processor.TemplatePropertyAnnotationProcessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import org.apache.commons.lang3.StringUtils;

/**
 * Generates the property tree backing {@link TemplateDocumentProperty}: a set of user-facing
 * sub-fields (source dropdown, per-source content/url/etc.) bound to flat helper variables, plus a
 * trailing {@code Hidden} composer input mapping that assembles those helpers into the canonical
 * {@code DocumentReferenceModel} JSON at the field's actual binding path.
 */
final class DocumentPropertyHandler {

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

  private record SubField(
      Function<SingleDocFields, String> id,
      String label,
      FeelMode feel,
      Function<TemplateDocumentProperty, FieldVisibility> visibility) {

    static SubField core(Function<SingleDocFields, String> id, String label, FeelMode feel) {
      return new SubField(id, label, feel, annotation -> FieldVisibility.REQUIRED);
    }
  }

  /** The sub-fields each source contributes, and what governs their visibility. */
  private static final Map<DocumentSource, List<SubField>> SUB_FIELDS =
      new EnumMap<>(
          Map.of(
              DocumentSource.CAMUNDA,
              List.of(SubField.core(f -> f.camundaRefId, "Camunda document", FeelMode.required)),
              DocumentSource.INLINE,
              List.of(
                  SubField.core(f -> f.inlineContentId, "Content", FeelMode.optional),
                  new SubField(
                      f -> f.inlineFileNameId,
                      "File name",
                      FeelMode.optional,
                      TemplateDocumentProperty::fileName),
                  new SubField(
                      f -> f.inlineContentTypeId,
                      "Content type",
                      FeelMode.optional,
                      TemplateDocumentProperty::contentType)),
              DocumentSource.EXTERNAL,
              List.of(
                  SubField.core(f -> f.externalUrlId, "URL", FeelMode.optional),
                  new SubField(
                      f -> f.externalFileNameId,
                      "File name",
                      FeelMode.optional,
                      TemplateDocumentProperty::fileName))));

  /**
   * Everything a document property derives from its annotation and declared name, resolved once so
   * the single, optional-single and list variants share one derivation.
   *
   * @param targetPath binding path of the composer, i.e. where the assembled document JSON lands
   * @param targetParent parent path the helper sub-fields are bound under, empty at the root
   * @param localPrefix {@code targetPath} with dots replaced, used to build helper ids
   */
  private record DocumentContext(
      String targetPath,
      String targetParent,
      String localPrefix,
      String group,
      PropertyCondition parentCondition,
      Set<DocumentSource> sources,
      String composerId) {

    static DocumentContext of(TemplateDocumentProperty annotation, String declaredName) {
      String targetPath = resolveBindingRoot(annotation, declaredName);
      return new DocumentContext(
          targetPath,
          helperTargetParent(targetPath),
          toLocalPrefix(targetPath),
          blankToNull(annotation.group()),
          // qualified: the record's own accessor would shadow the static helper
          DocumentPropertyHandler.parentCondition(annotation),
          resolveSources(annotation, declaredName),
          resolveComposerId(annotation, targetPath));
    }

    String modeId() {
      return localPrefix + "_documentMode";
    }
  }

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
    var context = DocumentContext.of(annotation, declaredName);
    SingleDocFields fields = singleDocFields(context.localPrefix());

    if (annotation.optional()) {
      return buildOptionalSingleProperty(context, annotation, fields);
    }

    var dependants = new ArrayList<PropertyBuilder>();
    addSingleSubProperties(dependants, fields, context.parentCondition(), annotation, context);

    var sourceDropdown =
        buildSingleSourceDropdown(
            fields,
            context.parentCondition(),
            blankToNull(annotation.description()),
            blankToNull(annotation.tooltip()),
            context,
            dependants);

    return assemble(
        sourceDropdown,
        dependants,
        context,
        singleDocComposerExpression(fields, context.sources()));
  }

  private static List<PropertyBuilder> buildOptionalSingleProperty(
      DocumentContext context, TemplateDocumentProperty annotation, SingleDocFields fields) {
    String modeId = context.modeId();
    PropertyCondition yesCondition =
        combine(context.parentCondition(), new Equals(modeId, CHOICE_YES));

    var subFields = new ArrayList<PropertyBuilder>();
    addSingleSubProperties(subFields, fields, yesCondition, annotation, context);

    var modeDependants = new ArrayList<PropertyBuilder>();
    modeDependants.add(
        buildSingleSourceDropdown(fields, yesCondition, null, null, context, subFields));
    modeDependants.addAll(subFields);

    var modeDropdown =
        buildModeDropdown(
            context,
            annotation,
            OPTIONAL_SINGLE_LABEL,
            SINGLE_OPTIONAL_MODE_CHOICES,
            CHOICE_NO,
            modeDependants);

    return assemble(
        modeDropdown,
        modeDependants,
        context,
        optionalSingleDocComposerExpression(modeId, fields, context.sources()));
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
    var context = DocumentContext.of(annotation, declaredName);
    String modeId = context.modeId();
    SingleDocFields single = listSingleFields(context.localPrefix());
    String multipleExpressionId = context.localPrefix() + "_multiple_expression";

    PropertyCondition singleModeCondition =
        combine(context.parentCondition(), new Equals(modeId, CHOICE_SINGLE));
    PropertyCondition multipleModeCondition =
        combine(context.parentCondition(), new Equals(modeId, CHOICE_MULTIPLE));

    var singleSubFields = new ArrayList<PropertyBuilder>();
    addSingleSubProperties(singleSubFields, single, singleModeCondition, annotation, context);

    var multipleExpression =
        StringProperty.builder().feel(FeelMode.required).constraints(notEmpty());
    multipleExpression
        .id(multipleExpressionId)
        .label("Documents")
        .binding(bindingFor(multipleExpressionId, context.targetParent()))
        .group(context.group())
        .condition(multipleModeCondition);

    var modeDependants = new ArrayList<PropertyBuilder>();
    modeDependants.add(
        buildSingleSourceDropdown(
            single, singleModeCondition, null, null, context, singleSubFields));
    modeDependants.addAll(singleSubFields);
    modeDependants.add(multipleExpression);

    boolean optional = annotation.optional();
    var modeDropdown =
        buildModeDropdown(
            context,
            annotation,
            LIST_MODE_LABEL,
            optional ? LIST_MODE_CHOICES_OPTIONAL : LIST_MODE_CHOICES_MANDATORY,
            optional ? CHOICE_NONE : CHOICE_SINGLE,
            modeDependants);

    return assemble(
        modeDropdown,
        modeDependants,
        context,
        listDocComposerExpression(modeId, single, multipleExpressionId, context.sources()));
  }

  /**
   * The mode dropdown that fronts a document property: {@code Attach document?} for an optional
   * single document, {@code Number of documents} for a list. Both differ only in label, choices and
   * default.
   */
  private static DiscriminatorPropertyBuilder buildModeDropdown(
      DocumentContext context,
      TemplateDocumentProperty annotation,
      String label,
      List<DropdownChoice> choices,
      String defaultChoice,
      List<PropertyBuilder> dependants) {
    var modeDropdown = new DiscriminatorPropertyBuilder().dependantProperties(dependants);
    modeDropdown.choices(choices);
    modeDropdown.feel(FeelMode.disabled);
    modeDropdown
        .id(context.modeId())
        .label(label)
        .description(blankToNull(annotation.description()))
        .tooltip(blankToNull(annotation.tooltip()))
        .value(defaultChoice)
        .binding(bindingFor(context.modeId(), context.targetParent()))
        .group(context.group())
        .condition(context.parentCondition());
    return modeDropdown;
  }

  /**
   * Every variant emits the same shape: the discriminator first, then its dependant sub-fields,
   * then the composer that assembles them — which must come last so the Modeler renders it after
   * the fields it reads.
   */
  private static List<PropertyBuilder> assemble(
      PropertyBuilder discriminator,
      List<PropertyBuilder> dependants,
      DocumentContext context,
      Function<UnaryOperator<String>, String> composerExpression) {
    var result = new ArrayList<PropertyBuilder>();
    result.add(discriminator);
    result.addAll(dependants);
    result.add(
        composerProperty(
            context.targetPath(),
            context.targetParent(),
            composerExpression,
            context.parentCondition(),
            context.group(),
            context.composerId()));
    return result;
  }

  private static DiscriminatorPropertyBuilder buildSingleSourceDropdown(
      SingleDocFields fields,
      PropertyCondition condition,
      String description,
      String tooltip,
      DocumentContext context,
      List<PropertyBuilder> dependants) {
    var sources = context.sources();
    var dropdown = new DiscriminatorPropertyBuilder().dependantProperties(dependants);
    dropdown.choices(
        sources.stream().map(s -> new DropdownChoice(s.getLabel(), s.getValue())).toList());
    dropdown.feel(FeelMode.disabled);
    dropdown
        .id(fields.sourceId)
        .label("Document source")
        .description(description)
        .tooltip(tooltip)
        .value(sources.iterator().next().getValue())
        .binding(bindingFor(fields.sourceId, context.targetParent()))
        .group(context.group())
        .condition(condition);
    return dropdown;
  }

  private static void addSingleSubProperties(
      List<PropertyBuilder> out,
      SingleDocFields fields,
      PropertyCondition parentCondition,
      TemplateDocumentProperty annotation,
      DocumentContext context) {

    for (DocumentSource source : context.sources()) {
      PropertyCondition condition =
          combine(parentCondition, new Equals(fields.sourceId, source.getValue()));
      for (SubField subField : SUB_FIELDS.get(source)) {
        FieldVisibility visibility = subField.visibility().apply(annotation);
        if (visibility == FieldVisibility.HIDDEN) {
          continue;
        }
        out.add(
            stringSub(
                subField.id().apply(fields),
                subField.label(),
                subField.feel(),
                visibility == FieldVisibility.REQUIRED,
                context.group(),
                context.targetParent(),
                condition));
      }
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
      String targetParent,
      Function<UnaryOperator<String>, String> feelExpression,
      PropertyCondition condition,
      String group,
      String composerId) {
    // The builder re-renders the expression whenever this property gets nested further.
    var composer = new DocumentComposerPropertyBuilder(targetParent, feelExpression);
    composer.id(composerId).binding(new ZeebeInput(targetPath)).group(group).condition(condition);
    return composer;
  }

  /**
   * Resolves {@link TemplateDocumentProperty#sources()}. The returned {@link EnumSet} iterates in
   * {@link DocumentSource} declaration order, so the generated dropdown, sub-fields and composer
   * branches stay in a stable sequence no matter how the annotation lists them.
   */
  private static Set<DocumentSource> resolveSources(
      TemplateDocumentProperty annotation, String declaredName) {
    var declared = EnumSet.noneOf(DocumentSource.class);
    declared.addAll(Arrays.asList(annotation.sources()));
    if (declared.isEmpty()) {
      throw new IllegalStateException(
          "@TemplateDocumentProperty on '" + declaredName + "' must declare at least one source");
    }
    return declared;
  }

  private static String resolveComposerId(TemplateDocumentProperty annotation, String targetPath) {
    String custom = blankToNull(annotation.id());
    return custom != null ? custom : targetPath + "__composer";
  }

  private static Function<UnaryOperator<String>, String> singleDocComposerExpression(
      SingleDocFields fields, Set<DocumentSource> sources) {
    return qualify -> sourceBranches(fields, sources, false, qualify);
  }

  private static Function<UnaryOperator<String>, String> optionalSingleDocComposerExpression(
      String modeId, SingleDocFields fields, Set<DocumentSource> sources) {
    return qualify ->
        """
        if %1$s = "yes" then (%2$s) \
        else null"""
            .formatted(qualify.apply(modeId), sourceBranches(fields, sources, false, qualify));
  }

  private static Function<UnaryOperator<String>, String> listDocComposerExpression(
      String modeId,
      SingleDocFields single,
      String multipleExpressionId,
      Set<DocumentSource> sources) {
    return qualify ->
        """
        if %1$s = "multiple" then %2$s \
        else if %1$s = "single" then (%3$s) \
        else null"""
            .formatted(
                qualify.apply(modeId),
                qualify.apply(multipleExpressionId),
                sourceBranches(single, sources, true, qualify));
  }

  /**
   * Builds the {@code if source = "..." then ... else ...} chain, emitting a branch only for the
   * enabled {@code sources} so the expression never references helper variables that were not
   * generated.
   */
  private static String sourceBranches(
      SingleDocFields fields,
      Set<DocumentSource> sources,
      boolean wrapInList,
      UnaryOperator<String> qualify) {
    var branches = new StringBuilder();
    for (DocumentSource source : sources) {
      String value =
          switch (source) {
            case CAMUNDA -> qualify.apply(fields.camundaRefId);
            case INLINE -> inlineObjectLiteral(fields, qualify);
            case EXTERNAL -> externalObjectLiteral(fields, qualify);
          };
      branches
          .append("if ")
          .append(qualify.apply(fields.sourceId))
          .append(" = \"")
          .append(source.getValue())
          .append("\" then ")
          .append(wrapInList ? "[" + value + "]" : value)
          .append(" else ");
    }
    return branches.append("null").toString();
  }

  private static String inlineObjectLiteral(SingleDocFields f, UnaryOperator<String> qualify) {
    return """
        { "%s": "%s", content: %s, name: %s, contentType: %s }"""
        .formatted(
            DOCUMENT_TYPE_KEY,
            DOCUMENT_TYPE_INLINE,
            qualify.apply(f.inlineContentId),
            qualify.apply(f.inlineFileNameId),
            qualify.apply(f.inlineContentTypeId));
  }

  private static String externalObjectLiteral(SingleDocFields f, UnaryOperator<String> qualify) {
    return """
        { "%s": "%s", url: %s, name: %s }"""
        .formatted(
            DOCUMENT_TYPE_KEY,
            DOCUMENT_TYPE_EXTERNAL,
            qualify.apply(f.externalUrlId),
            qualify.apply(f.externalFileNameId));
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
