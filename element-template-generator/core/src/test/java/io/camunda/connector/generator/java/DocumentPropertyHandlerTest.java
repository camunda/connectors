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
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateDocumentProperty;
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
}
