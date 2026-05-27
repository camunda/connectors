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
package io.camunda.connector.optimizer.pass;

import static io.camunda.connector.optimizer.TestTemplates.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.StringProperty;
import org.junit.jupiter.api.Test;

class MergeByIdentityPassTest {

  private final MergeByIdentityPass pass = new MergeByIdentityPass();

  @Test
  void shouldMergeSimilarPropertiesWithDifferentConditions() {
    ElementTemplate input =
        template(
            hiddenProperty(
                "search_query_locale",
                "en-US",
                zeebeInput("locale"),
                equalsCondition("operationId", "search")),
            hiddenProperty(
                "autocomplete_query_locale",
                "en-US",
                zeebeInput("locale"),
                equalsCondition("operationId", "autocomplete")),
            hiddenProperty(
                "feed_query_locale",
                "en-US",
                zeebeInput("locale"),
                equalsCondition("operationId", "feed")));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(1);
    var merged = result.properties().get(0);
    assertThat(merged.getId()).isEqualTo("query_locale");
    assertThat(merged.getCondition()).isInstanceOf(PropertyCondition.OneOf.class);
    var condition = (PropertyCondition.OneOf) merged.getCondition();
    assertThat(condition.property()).isEqualTo("operationId");
    assertThat(condition.oneOf()).containsExactlyInAnyOrder("search", "autocomplete", "feed");
  }

  @Test
  void shouldNotMergeDifferentBindings() {
    ElementTemplate input =
        template(
            hiddenProperty("prop1", "val", zeebeInput("field1"), equalsCondition("op", "a")),
            hiddenProperty("prop2", "val", zeebeInput("field2"), equalsCondition("op", "b")));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(2);
  }

  @Test
  void shouldNotMergeDifferentValues() {
    ElementTemplate input =
        template(
            hiddenProperty("prop1", "val1", zeebeInput("field"), equalsCondition("op", "a")),
            hiddenProperty("prop2", "val2", zeebeInput("field"), equalsCondition("op", "b")));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(2);
  }

  @Test
  void shouldNotMergeDifferentDiscriminators() {
    ElementTemplate input =
        template(
            hiddenProperty("prop1", "val", zeebeInput("field"), equalsCondition("op1", "a")),
            hiddenProperty("prop2", "val", zeebeInput("field"), equalsCondition("op2", "b")));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(2);
  }

  @Test
  void shouldHandlePropertiesWithoutConditions() {
    ElementTemplate input =
        template(
            hiddenProperty("prop1", "val", zeebeInput("field")),
            hiddenProperty("prop2", "val", zeebeInput("field")));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(2);
  }

  @Test
  void shouldMergePropertiesWithOneOfConditions() {
    ElementTemplate input =
        template(
            hiddenProperty("prop1", "val", zeebeInput("field"), oneOf("op", "a", "b")),
            hiddenProperty("prop2", "val", zeebeInput("field"), equalsCondition("op", "c")));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(1);
    var merged = result.properties().get(0);
    assertThat(merged.getCondition()).isInstanceOf(PropertyCondition.OneOf.class);
    var condition = (PropertyCondition.OneOf) merged.getCondition();
    assertThat(condition.oneOf()).containsExactlyInAnyOrder("a", "b", "c");
  }

  @Test
  void shouldDisambiguateWhenMergedIdCollidesWithAnotherProperty() {
    // An unconditional property already owns "query_locale" — the suffix-strip of the merge
    // group would otherwise reuse the same id.
    ElementTemplate input =
        template(
            hiddenProperty("query_locale", "static", zeebeInput("locale_static")),
            hiddenProperty(
                "search_query_locale",
                "en-US",
                zeebeInput("locale"),
                equalsCondition("op", "search")),
            hiddenProperty(
                "feed_query_locale", "en-US", zeebeInput("locale"), equalsCondition("op", "feed")));

    ElementTemplate result = pass.apply(input);

    var ids = result.properties().stream().map(p -> p.getId()).toList();
    assertThat(ids).doesNotHaveDuplicates();
    assertThat(ids).contains("query_locale");
  }

  @Test
  void shouldDisambiguateWhenMergedIdCollidesWithAnotherMerge() {
    // Two distinct merge groups whose suffix-strip would land on the same id.
    ElementTemplate input =
        template(
            hiddenProperty(
                "search_locale", "en-US", zeebeInput("a"), equalsCondition("op", "search")),
            hiddenProperty("feed_locale", "en-US", zeebeInput("a"), equalsCondition("op", "feed")),
            hiddenProperty(
                "search_locale_b", "fr-FR", zeebeInput("b"), equalsCondition("op", "search")),
            hiddenProperty(
                "feed_locale_b", "fr-FR", zeebeInput("b"), equalsCondition("op", "feed")));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(2);
    var ids = result.properties().stream().map(p -> p.getId()).toList();
    assertThat(ids).doesNotHaveDuplicates();
  }

  @Test
  void shouldNotMergePropertiesDifferingInGeneratedValue() {
    // Two String properties with the same binding and presentation, but one uses
    // generatedValue:uuid and the other uses an explicit value. Pre-fix, PropertyIdentity ignored
    // generatedValue and the two merged into one, silently losing the generated-value source.
    var generated =
        (StringProperty)
            StringProperty.builder()
                .id("a_uuid")
                .binding(zeebeInput("token"))
                .generatedValue()
                .condition(equalsCondition("op", "a"))
                .build();
    var explicit =
        (StringProperty)
            StringProperty.builder()
                .id("b_uuid")
                .binding(zeebeInput("token"))
                .value("hardcoded")
                .condition(equalsCondition("op", "b"))
                .build();

    ElementTemplate result = pass.apply(template(generated, explicit));

    assertThat(result.properties()).hasSize(2);
  }

  @Test
  void shouldNotMergeNumberValuedEqualsCondition() {
    // Equals(value=42) is not a string; the merger must refuse rather than coerce via
    // String.valueOf, which would conflate values like null or Boolean.TRUE with their text form.
    ElementTemplate input =
        template(
            hiddenProperty("a_x", "v", zeebeInput("x"), new PropertyCondition.Equals("op", 1)),
            hiddenProperty("b_x", "v", zeebeInput("x"), new PropertyCondition.Equals("op", 2)));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(2);
  }

  @Test
  void shouldPreserveNullFeelOnStringPropertyRoundTrip() {
    // StringPropertyBuilder.build() defaults a null feel to FeelMode.optional, so going through
    // the builder would silently stamp feel="optional" onto every untouched String property.
    // PropertyUtils sidesteps the builder for exactly this reason; pin the behaviour.
    StringProperty leftPin =
        (StringProperty)
            StringProperty.builder()
                .id("a")
                .binding(zeebeInput("x"))
                .value("v")
                .condition(equalsCondition("op", "a"))
                .build();
    StringProperty rightPin =
        (StringProperty)
            StringProperty.builder()
                .id("b")
                .binding(zeebeInput("x"))
                .value("v")
                .condition(equalsCondition("op", "b"))
                .build();
    // Builders populate feel=optional; null out the field by hand via constructor to model a
    // property that genuinely had no feel set.
    StringProperty leftNoFeel = withNullFeel(leftPin);
    StringProperty rightNoFeel = withNullFeel(rightPin);

    ElementTemplate result = pass.apply(template(leftNoFeel, rightNoFeel));

    assertThat(result.properties()).hasSize(1);
    assertThat(result.properties().get(0).getFeel()).isNull();
  }

  @Test
  void shouldNotMergeDropdownsWithDifferentChoices() {
    // Two dropdowns identical in binding/value/group/etc. but with different choices used to
    // hash equal under PropertyIdentity and merge, silently dropping one set of choices.
    ElementTemplate input =
        template(
            dropdownProperty("left", "a", zeebeInput("op"), choice("a", "a"), choice("b", "b")),
            dropdownProperty("right", "a", zeebeInput("op"), choice("a", "a"), choice("c", "c")));

    ElementTemplate result = pass.apply(input);

    assertThat(result.properties()).hasSize(2);
  }

  private static StringProperty withNullFeel(StringProperty source) {
    return new StringProperty(
        source.getId(),
        source.getLabel(),
        source.getDescription(),
        source.isOptional(),
        (String) source.getValue(),
        source.getGeneratedValue(),
        source.getConstraints(),
        null,
        source.getGroup(),
        source.getBinding(),
        source.getCondition(),
        source.getTooltip(),
        source.getPlaceholder(),
        source.getExampleValue());
  }
}
