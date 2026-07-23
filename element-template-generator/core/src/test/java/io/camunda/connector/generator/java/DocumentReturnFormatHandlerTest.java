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

import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.BaseTest;
import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyCondition.AllMatch;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.DocumentReturnFormat;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import org.junit.jupiter.api.Test;

class DocumentReturnFormatHandlerTest extends BaseTest {

  private final ClassBasedTemplateGenerator generator = new ClassBasedTemplateGenerator();

  // --- Top-level class fixture (mirrors HTTP) ---

  @DocumentReturnFormat
  record FlatInput() {}

  @OutboundConnector(name = "doc-out-test", type = "doc-out-test-type")
  @ElementTemplate(
      engineVersion = "^8.10",
      id = "doc-out-test",
      name = "Doc Out Test",
      inputDataClass = FlatInput.class)
  static class FlatConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  // --- Sealed sub-type fixture (mirrors S3 / Box / GCS / Azure / GDrive) ---

  @com.fasterxml.jackson.annotation.JsonTypeInfo(
      use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
      property = "type")
  @com.fasterxml.jackson.annotation.JsonSubTypes({
    @com.fasterxml.jackson.annotation.JsonSubTypes.Type(
        value = Op.Download.class,
        name = "download")
  })
  @io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty(
      label = "Op",
      group = "operation",
      name = "type")
  sealed interface Op permits Op.Download {

    @TemplateSubType(id = "download", label = "Download")
    @DocumentReturnFormat
    record Download() implements Op {}
  }

  record NestedInput(Op operation) {}

  @OutboundConnector(name = "doc-out-test-nested", type = "doc-out-test-nested-type")
  @ElementTemplate(
      engineVersion = "^8.10",
      id = "doc-out-test-nested",
      name = "Doc Out Test Nested",
      inputDataClass = NestedInput.class)
  static class NestedConnector implements OutboundConnectorFunction {
    @Override
    public Object execute(OutboundConnectorContext context) {
      return null;
    }
  }

  @Test
  void flat_dropdownHasAllThreeChoicesAndDefaultDocument() {
    var template = generator.generate(FlatConnector.class).getFirst();
    var dropdown = (DropdownProperty) getPropertyById("documentReturnFormat", template);

    assertThat(dropdown.getChoices())
        .containsExactly(
            new DropdownChoice("Document reference", "DOCUMENT"),
            new DropdownChoice("as text", "TEXT"),
            new DropdownChoice("as JSON", "JSON"));
    assertThat(dropdown.getValue()).isEqualTo(DocumentReturnChoice.DOCUMENT.name());
    // Binding is always root-level so the runtime can read it from context without per-connector
    // path configuration.
    assertThat(dropdown.getBinding()).isEqualTo(new ZeebeInput("documentReturnFormat.choice"));
  }

  @Test
  void flat_encodingShownOnlyWhenTextSelected() {
    var template = generator.generate(FlatConnector.class).getFirst();
    var encoding = (StringProperty) getPropertyById("documentReturnFormatEncoding", template);

    assertThat(encoding.getBinding()).isEqualTo(new ZeebeInput("documentReturnFormat.encoding"));
    assertThat(encoding.getCondition()).isEqualTo(new Equals("documentReturnFormat", "TEXT"));
    assertThat(encoding.getValue()).isEqualTo("UTF-8");
  }

  @Test
  void nested_bindingStaysRootLevelEvenInsideSealedSubtype() {
    // Even when @DocumentReturnFormat sits on a sealed sub-type record, the binding must remain
    // root-level (documentReturnFormat.choice), not nested under the discriminator path. The
    // runtime reads from this fixed location via context.readDocumentReturnFormat().
    var template = generator.generate(NestedConnector.class).getFirst();
    var dropdown = (DropdownProperty) getPropertyById("documentReturnFormat", template);

    assertThat(dropdown.getBinding()).isEqualTo(new ZeebeInput("documentReturnFormat.choice"));
  }

  @Test
  void nested_dropdownIsConditionedOnDiscriminator() {
    // The dropdown is only shown when the user picked the sub-type that has @DocumentReturnFormat.
    // For the sealed Op with one Download subtype, that's a condition on type == "download".
    var template = generator.generate(NestedConnector.class).getFirst();
    var dropdown = (DropdownProperty) getPropertyById("documentReturnFormat", template);

    // The discriminator property itself lives at "operation.type" (nested under the field name),
    // so the dropdown's condition referencing it must use the same prefixed path.
    assertThat(dropdown.getCondition()).isEqualTo(new Equals("operation.type", "download"));
  }

  @Test
  void nested_encodingConditionCombinesDiscriminatorAndTextChoice() {
    var template = generator.generate(NestedConnector.class).getFirst();
    var encoding = (StringProperty) getPropertyById("documentReturnFormatEncoding", template);

    assertThat(encoding.getBinding()).isEqualTo(new ZeebeInput("documentReturnFormat.encoding"));

    // Condition: discriminator(type=download) AND dropdown(=TEXT).
    assertThat(encoding.getCondition()).isInstanceOf(AllMatch.class);
    AllMatch allMatch = (AllMatch) encoding.getCondition();
    assertThat(allMatch.allMatch())
        .anyMatch(
            cond ->
                cond instanceof Equals eq
                    && "operation.type".equals(eq.property())
                    && "download".equals(eq.equals()));
    assertThat(allMatch.allMatch())
        .anyMatch(
            cond ->
                cond instanceof Equals eq
                    && "documentReturnFormat".equals(eq.property())
                    && "TEXT".equals(eq.equals()));
  }
}
