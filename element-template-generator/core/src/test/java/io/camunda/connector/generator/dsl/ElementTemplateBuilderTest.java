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
package io.camunda.connector.generator.dsl;

import static io.camunda.connector.generator.java.annotation.BpmnType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.generator.dsl.ElementTemplate.ElementTypeWrapper;
import io.camunda.connector.generator.java.annotation.BpmnType;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class ElementTemplateBuilderTest {

  /**
   * Populates every {@link ElementTemplate} field so {@link #fromRoundTripsEveryField()} fails the
   * moment a newly added field isn't wired into {@link
   * ElementTemplateBuilder#from(ElementTemplate)}.
   */
  private ElementTemplate templateWithEveryFieldPopulated() {
    return ElementTemplate.builderForOutbound()
        .id("io.camunda.connector.Template.v1")
        .type("io.camunda:template:1")
        .name("Template: Some Function")
        .version(3)
        .category(ElementTemplateCategory.CONNECTORS)
        .documentationRef("https://docs.camunda.io/docs/components/connectors/template/")
        .description("Some description")
        .keywords(new String[] {"foo", "bar"})
        .appliesTo(Set.of(SERVICE_TASK))
        .elementType(SERVICE_TASK)
        .engines(new Engines("^8.3"))
        .icon(new ElementTemplateIcon("data:image/svg+xml;base64,AAAA"))
        .propertyGroups(
            PropertyGroup.builder()
                .id("groupA")
                .label("Group A")
                .properties(
                    StringProperty.builder()
                        .id("propA1")
                        .group("groupA")
                        .binding(new PropertyBinding.ZeebeTaskHeader("propA1"))
                        .value("a1"))
                .build())
        .steps(List.of(new LeafStep("Step", "Step description", List.of("keyword"), "presetId")))
        .presets(List.of(new Preset("presetId", Map.of("propA1", "a1"))))
        .build();
  }

  @Test
  void fromRoundTripsEveryField() {
    var original = templateWithEveryFieldPopulated();

    var rebuilt = ElementTemplateBuilder.from(original).build();

    assertThat(rebuilt).isEqualTo(original);
  }

  private ElementTemplate templateWithElementType(BpmnType elementType) {
    return ElementTemplate.builderForOutbound()
        .id("io.camunda.connector.Template.v1")
        .type("io.camunda:template:1")
        .name("Template: Some Function")
        .appliesTo(Set.of(elementType))
        .elementType(elementType)
        .version(1)
        .build();
  }

  /**
   * Rebuilds the given template with an {@code elementType} whose {@code originalType} is {@code
   * null}, mirroring what Jackson produces when deserializing a template from JSON: {@link
   * ElementTypeWrapper#originalType()} is {@code @JsonIgnore}d and therefore never populated.
   */
  private ElementTemplate withoutOriginalType(ElementTemplate template) {
    var wrapper = template.elementType();
    return new ElementTemplate(
        template.id(),
        template.name(),
        template.version(),
        template.category(),
        template.documentationRef(),
        template.engines(),
        template.description(),
        template.keywords(),
        template.appliesTo(),
        new ElementTypeWrapper(wrapper.value(), wrapper.eventDefinition(), null),
        template.groups(),
        template.properties(),
        template.icon(),
        template.steps(),
        template.presets());
  }

  @Test
  void fromPreservesElementTypeWhenOriginalTypeAvailable() {
    var base = templateWithElementType(MESSAGE_START_EVENT);

    var rebuilt = ElementTemplateBuilder.from(base).build();

    assertThat(rebuilt.elementType().originalType()).isEqualTo(MESSAGE_START_EVENT);
  }

  @Test
  void fromReconstructsElementTypeWhenOriginalTypeIsMissing() {
    var deserializedLike = withoutOriginalType(templateWithElementType(MESSAGE_START_EVENT));

    var rebuilt = ElementTemplateBuilder.from(deserializedLike).build();

    assertThat(rebuilt.elementType().originalType()).isEqualTo(MESSAGE_START_EVENT);
  }

  @Test
  void fromReconstructsReceiveTaskWhenOriginalTypeIsMissing() {
    // RECEIVE_TASK.isMessage() is true, but a receive task has no eventDefinition of its own --
    // resolveType() must not equate "isMessage()" with "has an eventDefinition" or this type can
    // never be reconstructed once originalType is stripped (e.g. after deserializing from JSON).
    var deserializedLike = withoutOriginalType(templateWithElementType(RECEIVE_TASK));

    var rebuilt = ElementTemplateBuilder.from(deserializedLike).build();

    assertThat(rebuilt.elementType().originalType()).isEqualTo(RECEIVE_TASK);
  }

  @Test
  void fromDisambiguatesEventTypesSharingTheSameBpmnValue() {
    // START_EVENT and MESSAGE_START_EVENT both serialize to value "bpmn:StartEvent"; only
    // eventDefinition tells them apart, and that must still resolve correctly when originalType
    // is missing.
    var startEvent = withoutOriginalType(templateWithElementType(START_EVENT));
    var messageStartEvent = withoutOriginalType(templateWithElementType(MESSAGE_START_EVENT));

    var rebuiltStartEvent = ElementTemplateBuilder.from(startEvent).build();
    var rebuiltMessageStartEvent = ElementTemplateBuilder.from(messageStartEvent).build();

    assertThat(rebuiltStartEvent.elementType().originalType()).isEqualTo(START_EVENT);
    assertThat(rebuiltMessageStartEvent.elementType().originalType())
        .isEqualTo(MESSAGE_START_EVENT);
  }

  @Test
  void replacePropertyRejectsReplacementWithNullId() {
    // A non-configurable type's Hidden property has no id (see #type(String, boolean)), so two
    // of them landing in the same builder would otherwise be indistinguishable by id.
    var builder =
        ElementTemplate.builderForOutbound()
            .id("io.camunda.connector.Template.v1")
            .type("io.camunda:template:1")
            .name("Template: Some Function")
            .version(1);

    var replacementWithoutId =
        HiddenProperty.builder().binding(new PropertyBinding.ZeebeTaskHeader("someHeader")).build();

    assertThatThrownBy(() -> builder.replaceProperty(replacementWithoutId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  private ElementTemplate baseWithGroupsAndProperties() {
    return ElementTemplate.builderForOutbound()
        .id("io.camunda.connector.Template.v1")
        .type("io.camunda:template:1")
        .name("Template: Some Function")
        .appliesTo(Set.of(SERVICE_TASK))
        .elementType(SERVICE_TASK)
        .version(1)
        .propertyGroups(
            PropertyGroup.builder()
                .id("groupA")
                .label("Group A")
                .properties(
                    StringProperty.builder()
                        .id("propA1")
                        .group("groupA")
                        .binding(new PropertyBinding.ZeebeTaskHeader("propA1"))
                        .value("a1"),
                    StringProperty.builder()
                        .id("propA2")
                        .group("groupA")
                        .binding(new PropertyBinding.ZeebeTaskHeader("propA2"))
                        .value("a2"))
                .build(),
            PropertyGroup.builder()
                .id("groupB")
                .label("Group B")
                .properties(
                    StringProperty.builder()
                        .id("propB1")
                        .group("groupB")
                        .binding(new PropertyBinding.ZeebeTaskHeader("propB1"))
                        .value("b1"))
                .build())
        .build();
  }

  /**
   * {@code baseWithGroupsAndProperties()} builds via {@code .type(String)}, which prepends a
   * hidden, id-less {@code taskDefinitionType} property (see {@link
   * ElementTemplateBuilder#type(String, boolean)}) ahead of the named ones under test -- strip it
   * so assertions can focus on {@code propA1}/{@code propA2}/{@code propB1} ordering.
   */
  private List<String> namedPropertyIds(ElementTemplate template) {
    return template.properties().stream().map(p -> p.id).filter(Objects::nonNull).toList();
  }

  @Test
  void removePropertiesRemovesMatchingPropertiesAndLeavesOthersIntact() {
    var base = baseWithGroupsAndProperties();

    var rebuilt =
        ElementTemplateBuilder.from(base).removeProperties(p -> "propA1".equals(p.id)).build();

    assertThat(namedPropertyIds(rebuilt)).containsExactly("propA2", "propB1");
  }

  @Test
  void removePropertyGroupsRemovesMatchingGroupsAndLeavesOthersIntact() {
    var base = baseWithGroupsAndProperties();

    var rebuilt =
        ElementTemplateBuilder.from(base)
            .removePropertyGroups(g -> "groupA".equals(g.id()))
            .build();

    assertThat(rebuilt.groups()).extracting(PropertyGroup::id).containsExactly("groupB");
    // removePropertyGroups only prunes the groups list; properties are pruned separately via
    // removeProperties.
    assertThat(namedPropertyIds(rebuilt)).containsExactly("propA1", "propA2", "propB1");
  }

  @Test
  void replacePropertyReplacesMatchingPropertyInPlace() {
    var base = baseWithGroupsAndProperties();

    var replacement =
        StringProperty.builder()
            .id("propA2")
            .group("groupA")
            .binding(new PropertyBinding.ZeebeTaskHeader("propA2"))
            .value("replaced")
            .build();

    var rebuilt = ElementTemplateBuilder.from(base).replaceProperty(replacement).build();

    assertThat(namedPropertyIds(rebuilt)).containsExactly("propA1", "propA2", "propB1");
    assertThat(rebuilt.properties().get(2)).isSameAs(replacement);
  }

  @Test
  void replacePropertyPreservesOriginalPositionRatherThanAppending() {
    // The javadoc on replaceProperty guarantees position is preserved because a later property's
    // condition may reference an earlier one by id -- appending instead of replacing in place
    // would silently break that ordering guarantee.
    var base = baseWithGroupsAndProperties();

    var replacement =
        StringProperty.builder()
            .id("propA1")
            .group("groupA")
            .binding(new PropertyBinding.ZeebeTaskHeader("propA1"))
            .value("replaced")
            .build();

    var rebuilt = ElementTemplateBuilder.from(base).replaceProperty(replacement).build();

    assertThat(namedPropertyIds(rebuilt)).containsExactly("propA1", "propA2", "propB1");
    assertThat(rebuilt.properties().get(1)).isSameAs(replacement);
  }
}
