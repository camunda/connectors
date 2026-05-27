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
package io.camunda.connector.generator.java;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.BaseTest;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.FieldVisibility;
import io.camunda.connector.generator.java.annotation.TemplateDocumentProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class DocumentPropertyHandlerTest extends BaseTest {

  private final ClassBasedTemplateGenerator generator = new ClassBasedTemplateGenerator();

  // --- Fixture input records ---

  record SingleDocInput(@TemplateDocumentProperty Document doc) {}

  record ListDocInput(@TemplateDocumentProperty List<Document> docs) {}

  record WrongTypeSingleInput(@TemplateDocumentProperty String notADoc) {}

  record WrongTypeListInput(@TemplateDocumentProperty List<String> notDocs) {}

  record NestedDocInput(NestedDocRecord nested) {}

  record NestedDocRecord(@TemplateDocumentProperty Document doc) {}

  record VisibilityHiddenInput(
      @TemplateDocumentProperty(
              fileName = FieldVisibility.HIDDEN,
              contentType = FieldVisibility.HIDDEN)
          Document doc) {}

  record VisibilityRequiredInput(
      @TemplateDocumentProperty(
              fileName = FieldVisibility.REQUIRED,
              contentType = FieldVisibility.REQUIRED)
          Document doc) {}

  record ConditionalDocInput(
      String mode,
      @TemplateDocumentProperty(
              condition =
                  @TemplateProperty.PropertyCondition(property = "mode", equals = "withDoc"))
          Document doc) {}

  record OptionalSingleDocInput(@TemplateDocumentProperty(optional = true) Document doc) {}

  record OptionalListDocInput(@TemplateDocumentProperty(optional = true) List<Document> docs) {}

  record CustomComposerIdInput(@TemplateDocumentProperty(id = "legacyId") Document doc) {}

  record BothAnnotationsInput(
      @TemplateProperty(id = "doc") @TemplateDocumentProperty Document doc) {}

  // --- Fixture connector functions ---

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = SingleDocInput.class)
  static class WithSingleDoc implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = ListDocInput.class)
  static class WithListDoc implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = WrongTypeSingleInput.class)
  static class WithWrongTypeSingle implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = WrongTypeListInput.class)
  static class WithWrongTypeList implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = NestedDocInput.class)
  static class WithNestedDoc implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = VisibilityHiddenInput.class)
  static class WithHiddenVisibility implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = VisibilityRequiredInput.class)
  static class WithRequiredVisibility implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = ConditionalDocInput.class)
  static class WithConditionalDoc implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = OptionalSingleDocInput.class)
  static class WithOptionalSingleDoc implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = OptionalListDocInput.class)
  static class WithOptionalListDoc implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = CustomComposerIdInput.class)
  static class WithCustomComposerId implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @OutboundConnector(name = "doc-test", type = "doc-test-type")
  @ElementTemplate(
      engineVersion = "^8.7",
      id = "doc-test",
      name = "Doc Test",
      inputDataClass = BothAnnotationsInput.class)
  static class WithBothAnnotationsOnField implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @Nested
  class TypeGuards {

    @Test
    void wrongType_nonDocument_throwsIllegalStateException() {
      var exception =
          assertThrows(
              IllegalStateException.class, () -> generator.generate(WithWrongTypeSingle.class));
      assertThat(exception.getMessage()).contains("notADoc");
      assertThat(exception.getMessage()).contains("Document");
    }

    @Test
    void wrongType_listOfNonDocument_throwsIllegalStateException() {
      var exception =
          assertThrows(
              IllegalStateException.class, () -> generator.generate(WithWrongTypeList.class));
      assertThat(exception.getMessage()).contains("notDocs");
      assertThat(exception.getMessage()).contains("Document");
    }
  }

  @Nested
  class SingleDocument {

    @Test
    void sourceDropdown_hasThreeChoices() {
      var template = generator.generate(WithSingleDoc.class).getFirst();
      var sourceDropdown = (DropdownProperty) getPropertyById("doc_documentSource", template);

      assertThat(sourceDropdown.getChoices())
          .containsExactly(
              new DropdownChoice("Camunda Document", "camunda"),
              new DropdownChoice("Inline Content", "inline"),
              new DropdownChoice("From URL", "external"));
    }

    @Test
    void sourceDropdown_defaultValue_isCamunda() {
      var template = generator.generate(WithSingleDoc.class).getFirst();
      var sourceDropdown = getPropertyById("doc_documentSource", template);

      assertThat(sourceDropdown.getValue()).isEqualTo("camunda");
    }

    @Test
    void camundaRef_subProperty_isPresent() {
      var template = generator.generate(WithSingleDoc.class).getFirst();
      var camundaRef = getPropertyById("doc_camundaReference", template);

      assertThat(camundaRef).isInstanceOf(StringProperty.class);
      assertThat(camundaRef.getBinding()).isEqualTo(new ZeebeInput("doc_camundaReference"));
    }

    @Test
    void inlineSubProperties_arePresent() {
      var template = generator.generate(WithSingleDoc.class).getFirst();

      assertThat(getPropertyById("doc_inline_content", template))
          .isInstanceOf(StringProperty.class);
      assertThat(getPropertyById("doc_inline_fileName", template))
          .isInstanceOf(StringProperty.class);
      assertThat(getPropertyById("doc_inline_contentType", template))
          .isInstanceOf(StringProperty.class);
    }

    @Test
    void externalSubProperties_arePresent() {
      var template = generator.generate(WithSingleDoc.class).getFirst();

      assertThat(getPropertyById("doc_external_url", template)).isInstanceOf(StringProperty.class);
      assertThat(getPropertyById("doc_external_fileName", template))
          .isInstanceOf(StringProperty.class);
    }

    @Test
    void hiddenComposer_hasCorrectBinding() {
      var template = generator.generate(WithSingleDoc.class).getFirst();
      var composer = (HiddenProperty) getPropertyById("doc__composer", template);

      assertThat(composer.getBinding()).isEqualTo(new ZeebeInput("doc"));
    }

    @Test
    void hiddenComposer_feelExpression_coversAllThreeSources() {
      var template = generator.generate(WithSingleDoc.class).getFirst();
      var composer = (HiddenProperty) getPropertyById("doc__composer", template);
      var value = (String) composer.getValue();

      assertThat(value).startsWith("=");
      assertThat(value).contains("\"camunda\"");
      assertThat(value).contains("\"inline\"");
      assertThat(value).contains("\"external\"");
    }
  }

  @Nested
  class ListDocument {

    @Test
    void modeDropdown_hasTwoChoices() {
      var template = generator.generate(WithListDoc.class).getFirst();
      var modeDropdown = (DropdownProperty) getPropertyById("docs_documentMode", template);

      assertThat(modeDropdown.getChoices())
          .containsExactly(
              new DropdownChoice("Single document", "single"),
              new DropdownChoice("Multiple documents", "multiple"));
    }

    @Test
    void sourceDropdown_nestedUnderSingleMode() {
      var template = generator.generate(WithListDoc.class).getFirst();
      var sourceDropdown =
          (DropdownProperty) getPropertyById("docs_single_documentSource", template);

      assertThat(sourceDropdown.getChoices())
          .containsExactly(
              new DropdownChoice("Camunda Document", "camunda"),
              new DropdownChoice("Inline Content", "inline"),
              new DropdownChoice("From URL", "external"));
    }

    @Test
    void singleSubProperties_arePresent() {
      var template = generator.generate(WithListDoc.class).getFirst();

      assertThat(getPropertyById("docs_single_camundaReference", template))
          .isInstanceOf(StringProperty.class);
      assertThat(getPropertyById("docs_single_inline_content", template))
          .isInstanceOf(StringProperty.class);
      assertThat(getPropertyById("docs_single_external_url", template))
          .isInstanceOf(StringProperty.class);
    }

    @Test
    void multipleExpression_isPresent() {
      var template = generator.generate(WithListDoc.class).getFirst();
      var multipleExpr = getPropertyById("docs_multiple_expression", template);

      assertThat(multipleExpr).isInstanceOf(StringProperty.class);
      assertThat(multipleExpr.getBinding()).isEqualTo(new ZeebeInput("docs_multiple_expression"));
    }

    @Test
    void hiddenComposer_hasCorrectBinding() {
      var template = generator.generate(WithListDoc.class).getFirst();
      var composer = (HiddenProperty) getPropertyById("docs__composer", template);

      assertThat(composer.getBinding()).isEqualTo(new ZeebeInput("docs"));
    }

    @Test
    void hiddenComposer_feelExpression_handlesMultipleMode() {
      var template = generator.generate(WithListDoc.class).getFirst();
      var composer = (HiddenProperty) getPropertyById("docs__composer", template);
      var value = (String) composer.getValue();

      assertThat(value).startsWith("=");
      assertThat(value).contains("\"multiple\"");
      assertThat(value).contains("docs_multiple_expression");
      assertThat(value).contains("docs_single_documentSource");
    }
  }

  /**
   * Guards the load-bearing invariant that the composer and every helper sub-field share a common
   * parent path in their {@code ZeebeInput} bindings. The composer's FEEL expression resolves
   * helper variables by bare name, which requires them to be siblings in the Zeebe input-mapping
   * context tree at evaluation time. {@code DocumentPropertyHandler.helperTargetParent} is the
   * single place that derives this parent; these tests pin its output for both top-level and
   * nested-record cases.
   */
  @Nested
  class BindingPathInvariant {

    @Test
    void topLevelField_composerAndHelpers_allAtRoot() {
      var template = generator.generate(WithSingleDoc.class).getFirst();

      assertThat(getPropertyById("doc__composer", template).getBinding())
          .isEqualTo(new ZeebeInput("doc"));
      assertThat(getPropertyById("doc_documentSource", template).getBinding())
          .isEqualTo(new ZeebeInput("doc_documentSource"));
      assertThat(getPropertyById("doc_camundaReference", template).getBinding())
          .isEqualTo(new ZeebeInput("doc_camundaReference"));
      assertThat(getPropertyById("doc_inline_content", template).getBinding())
          .isEqualTo(new ZeebeInput("doc_inline_content"));
      assertThat(getPropertyById("doc_external_url", template).getBinding())
          .isEqualTo(new ZeebeInput("doc_external_url"));
    }

    @Test
    void nestedField_composerAndHelpers_sharePrefixedParent() {
      var template = generator.generate(WithNestedDoc.class).getFirst();

      assertThat(getPropertyById("nested.doc__composer", template).getBinding())
          .isEqualTo(new ZeebeInput("nested.doc"));
      assertThat(getPropertyById("nested.doc_documentSource", template).getBinding())
          .isEqualTo(new ZeebeInput("nested.doc_documentSource"));
      assertThat(getPropertyById("nested.doc_camundaReference", template).getBinding())
          .isEqualTo(new ZeebeInput("nested.doc_camundaReference"));
      assertThat(getPropertyById("nested.doc_inline_content", template).getBinding())
          .isEqualTo(new ZeebeInput("nested.doc_inline_content"));
    }

    @Test
    void nestedField_subPropertyConditions_referencePrefixedSourceId() {
      var template = generator.generate(WithNestedDoc.class).getFirst();

      // The source dropdown id is rewritten to `nested.doc_documentSource`; sub-property
      // conditions must reference the rewritten id, otherwise the Modeler can't link them.
      var camundaRef = getPropertyById("nested.doc_camundaReference", template);
      assertThat(camundaRef.getCondition())
          .isEqualTo(new Equals("nested.doc_documentSource", "camunda"));

      var inlineContent = getPropertyById("nested.doc_inline_content", template);
      assertThat(inlineContent.getCondition())
          .isEqualTo(new Equals("nested.doc_documentSource", "inline"));

      var externalUrl = getPropertyById("nested.doc_external_url", template);
      assertThat(externalUrl.getCondition())
          .isEqualTo(new Equals("nested.doc_documentSource", "external"));
    }
  }

  @Nested
  class FieldVisibilitySettings {

    @Test
    void hidden_fileName_omitsInlineAndExternalFileNameSubProperties() {
      var template = generator.generate(WithHiddenVisibility.class).getFirst();

      assertThat(template.properties())
          .extracting("id")
          .doesNotContain("doc_inline_fileName", "doc_external_fileName");
    }

    @Test
    void hidden_contentType_omitsInlineContentTypeSubProperty() {
      var template = generator.generate(WithHiddenVisibility.class).getFirst();

      assertThat(template.properties()).extracting("id").doesNotContain("doc_inline_contentType");
    }

    @Test
    void hidden_doesNotRemoveCoreSubProperties() {
      var template = generator.generate(WithHiddenVisibility.class).getFirst();

      // The required core fields (camunda ref, inline content, external URL) must still be there
      assertThat(template.properties())
          .extracting("id")
          .contains("doc_camundaReference", "doc_inline_content", "doc_external_url");
    }

    @Test
    void required_fileName_addsNotEmptyConstraint() {
      var template = generator.generate(WithRequiredVisibility.class).getFirst();

      var inlineFileName = (StringProperty) getPropertyById("doc_inline_fileName", template);
      assertThat(inlineFileName.getConstraints().notEmpty()).isTrue();

      var externalFileName = (StringProperty) getPropertyById("doc_external_fileName", template);
      assertThat(externalFileName.getConstraints().notEmpty()).isTrue();
    }

    @Test
    void required_contentType_addsNotEmptyConstraint() {
      var template = generator.generate(WithRequiredVisibility.class).getFirst();

      var contentType = (StringProperty) getPropertyById("doc_inline_contentType", template);
      assertThat(contentType.getConstraints().notEmpty()).isTrue();
    }

    @Test
    void optional_fileName_omitsNotEmptyConstraint() {
      // Default visibility is OPTIONAL; uses the existing WithSingleDoc fixture
      var template = generator.generate(WithSingleDoc.class).getFirst();

      var inlineFileName = (StringProperty) getPropertyById("doc_inline_fileName", template);
      assertThat(inlineFileName.getConstraints()).isNull();
    }
  }

  @Nested
  class ConditionChaining {

    @Test
    void sourceDropdown_condition_isParentConditionOnly() {
      var template = generator.generate(WithConditionalDoc.class).getFirst();
      var sourceDropdown = getPropertyById("doc_documentSource", template);

      // Source dropdown sits directly under the parent condition — it IS the source selector,
      // it doesn't get combined with itself.
      assertThat(sourceDropdown.getCondition()).isEqualTo(new Equals("mode", "withDoc"));
    }

    @Test
    void subProperties_condition_combinesParentAndSourceViaAllMatch() {
      var template = generator.generate(WithConditionalDoc.class).getFirst();

      var camundaRef = getPropertyById("doc_camundaReference", template);
      assertThat(camundaRef.getCondition())
          .isEqualTo(
              new AllMatch(
                  List.of(
                      new Equals("mode", "withDoc"), new Equals("doc_documentSource", "camunda"))));

      var inlineContent = getPropertyById("doc_inline_content", template);
      assertThat(inlineContent.getCondition())
          .isEqualTo(
              new AllMatch(
                  List.of(
                      new Equals("mode", "withDoc"), new Equals("doc_documentSource", "inline"))));

      var externalUrl = getPropertyById("doc_external_url", template);
      assertThat(externalUrl.getCondition())
          .isEqualTo(
              new AllMatch(
                  List.of(
                      new Equals("mode", "withDoc"),
                      new Equals("doc_documentSource", "external"))));
    }

    @Test
    void composer_condition_isParentConditionOnly() {
      var template = generator.generate(WithConditionalDoc.class).getFirst();
      var composer = getPropertyById("doc__composer", template);

      // The composer carries the parent condition (so the field is only assembled when the
      // outer condition holds) but does NOT combine with any source-specific condition.
      assertThat(composer.getCondition()).isEqualTo(new Equals("mode", "withDoc"));
    }
  }

  @Nested
  class OptionalSingleDocument {

    @Test
    void modeDropdown_hasYesNoChoices_andLabel() {
      var template = generator.generate(WithOptionalSingleDoc.class).getFirst();
      var modeDropdown = (DropdownProperty) getPropertyById("doc_documentMode", template);

      assertThat(modeDropdown.getChoices())
          .containsExactly(new DropdownChoice("No", "no"), new DropdownChoice("Yes", "yes"));
      assertThat(modeDropdown.getLabel()).isEqualTo("Attach document?");
    }

    @Test
    void modeDropdown_defaultsToNo() {
      var template = generator.generate(WithOptionalSingleDoc.class).getFirst();
      var modeDropdown = getPropertyById("doc_documentMode", template);

      assertThat(modeDropdown.getValue()).isEqualTo("no");
    }

    @Test
    void sourceDropdown_gatedOnModeYes() {
      var template = generator.generate(WithOptionalSingleDoc.class).getFirst();
      var sourceDropdown = getPropertyById("doc_documentSource", template);

      assertThat(sourceDropdown.getCondition()).isEqualTo(new Equals("doc_documentMode", "yes"));
    }

    @Test
    void subProperties_gatedOnModeYesAndSource() {
      var template = generator.generate(WithOptionalSingleDoc.class).getFirst();

      assertThat(getPropertyById("doc_camundaReference", template).getCondition())
          .isEqualTo(
              new AllMatch(
                  List.of(
                      new Equals("doc_documentMode", "yes"),
                      new Equals("doc_documentSource", "camunda"))));
      assertThat(getPropertyById("doc_inline_content", template).getCondition())
          .isEqualTo(
              new AllMatch(
                  List.of(
                      new Equals("doc_documentMode", "yes"),
                      new Equals("doc_documentSource", "inline"))));
      assertThat(getPropertyById("doc_external_url", template).getCondition())
          .isEqualTo(
              new AllMatch(
                  List.of(
                      new Equals("doc_documentMode", "yes"),
                      new Equals("doc_documentSource", "external"))));
    }

    @Test
    void hiddenComposer_emitsNullWhenModeIsNo() {
      var template = generator.generate(WithOptionalSingleDoc.class).getFirst();
      var composer = (HiddenProperty) getPropertyById("doc__composer", template);
      var value = (String) composer.getValue();

      assertThat(value).startsWith("=");
      assertThat(value).contains("doc_documentMode = \"yes\"");
      assertThat(value).endsWith("else null");
    }
  }

  @Nested
  class OptionalListDocument {

    @Test
    void modeDropdown_includesNoneChoice() {
      var template = generator.generate(WithOptionalListDoc.class).getFirst();
      var modeDropdown = (DropdownProperty) getPropertyById("docs_documentMode", template);

      assertThat(modeDropdown.getChoices())
          .containsExactly(
              new DropdownChoice("None", "none"),
              new DropdownChoice("Single document", "single"),
              new DropdownChoice("Multiple documents", "multiple"));
    }

    @Test
    void modeDropdown_defaultsToNone() {
      var template = generator.generate(WithOptionalListDoc.class).getFirst();
      var modeDropdown = getPropertyById("docs_documentMode", template);

      assertThat(modeDropdown.getValue()).isEqualTo("none");
    }

    @Test
    void modeDropdown_labelIsNumberOfDocuments() {
      var template = generator.generate(WithOptionalListDoc.class).getFirst();
      var modeDropdown = getPropertyById("docs_documentMode", template);

      assertThat(modeDropdown.getLabel()).isEqualTo("Number of documents");
    }

    @Test
    void hiddenComposer_fallthroughEmitsNullNotEmptyList() {
      var template = generator.generate(WithOptionalListDoc.class).getFirst();
      var composer = (HiddenProperty) getPropertyById("docs__composer", template);
      var value = (String) composer.getValue();

      assertThat(value).startsWith("=");
      assertThat(value).endsWith("else null");
      assertThat(value).doesNotContain("else []");
    }
  }

  @Nested
  class MutualExclusionGuard {

    @Test
    void bothAnnotations_onRecordField_throwsIllegalStateException() {
      var exception =
          assertThrows(
              IllegalStateException.class,
              () -> generator.generate(WithBothAnnotationsOnField.class));
      assertThat(exception.getMessage())
          .contains("@TemplateProperty and @TemplateDocumentProperty are mutually exclusive");
      assertThat(exception.getMessage()).contains("doc");
    }
  }

  @Nested
  class ComposerIdOverride {

    @Test
    void annotationId_becomesComposerId_andKeepsBindingOnTargetPath() {
      var template = generator.generate(WithCustomComposerId.class).getFirst();
      var composer = (HiddenProperty) getPropertyById("legacyId", template);

      // The composer adopts the annotation's id so element templates retain a stable root id,
      // while its binding still targets the field's actual path.
      assertThat(composer).isNotNull();
      assertThat(composer.getBinding()).isEqualTo(new ZeebeInput("doc"));
      // The fallback id "doc__composer" should no longer be present.
      assertThat(template.properties()).extracting("id").doesNotContain("doc__composer");
    }
  }

  @Nested
  class MandatoryListComposer {

    @Test
    void hiddenComposer_fallthroughEmitsNullNotEmptyList() {
      // Even for mandatory lists, the fallthrough should be `null` (not `[]`) to keep the
      // empty-document contract uniform with main's behavior for blank optional fields.
      var template = generator.generate(WithListDoc.class).getFirst();
      var composer = (HiddenProperty) getPropertyById("docs__composer", template);
      var value = (String) composer.getValue();

      assertThat(value).endsWith("else null");
      assertThat(value).doesNotContain("else []");
    }
  }
}
