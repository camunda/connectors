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

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.generator.dsl.DropdownProperty.DropdownChoice;
import io.camunda.connector.generator.java.annotation.FeelMode;
import java.util.List;
import org.junit.jupiter.api.Test;

public class PropertyToBuilderTest {

  private StringProperty stringPropertyWithEveryFieldPopulated() {
    return (StringProperty)
        StringProperty.builder()
            .id("propA")
            .label("Label A")
            .description("Description A")
            .optional(true)
            .value("a value")
            .constraints(PropertyConstraints.builder().notEmpty(true).build())
            .feel(FeelMode.required)
            .group("groupA")
            .binding(new PropertyBinding.ZeebeTaskHeader("propA"))
            .condition(new PropertyCondition.Equals("other", "x"))
            .tooltip("a tooltip")
            .placeholder("a placeholder")
            .exampleValue("an example")
            .language("json")
            .secret(true)
            .build();
  }

  @Test
  void toBuilderRoundTripsEveryFieldUnchanged() {
    var original = stringPropertyWithEveryFieldPopulated();

    var rebuilt = original.toBuilder().build();

    assertThat(rebuilt).isEqualTo(original);
    assertThat(rebuilt.getTooltip()).isEqualTo(original.getTooltip());
    assertThat(rebuilt.getPlaceholder()).isEqualTo(original.getPlaceholder());
    assertThat(rebuilt.getExampleValue()).isEqualTo(original.getExampleValue());
    assertThat(rebuilt.getSecret()).isEqualTo(original.getSecret());
  }

  @Test
  void toBuilderOverridesOnlyTheNamedField() {
    var original = stringPropertyWithEveryFieldPopulated();

    var rebuilt = (StringProperty) original.toBuilder().label("New label").build();

    assertThat(rebuilt.getLabel()).isEqualTo("New label");
    assertThat(rebuilt.getId()).isEqualTo(original.getId());
    assertThat(rebuilt.getDescription()).isEqualTo(original.getDescription());
    assertThat(rebuilt.getGroup()).isEqualTo(original.getGroup());
    assertThat(rebuilt.getBinding()).isEqualTo(original.getBinding());
    assertThat(rebuilt.getTooltip()).isEqualTo(original.getTooltip());
    assertThat(rebuilt.getSecret()).isEqualTo(original.getSecret());
  }

  @Test
  void toBuilderPreservesDisabledFeelUnlikeGetFeel() {
    // getFeel() masks FeelMode.disabled to null (see Property#getFeel); toBuilder() must copy the
    // raw field so a property built with feel(disabled) doesn't silently become feel(null) --
    // i.e. system_default -- on a round trip.
    var original =
        (StringProperty)
            StringProperty.builder()
                .id("propA")
                .binding(new PropertyBinding.ZeebeTaskHeader("propA"))
                .feel(FeelMode.disabled)
                .build();
    assertThat(original.getFeel()).isNull();

    var rebuilt = (StringProperty) original.toBuilder().build();

    assertThat(rebuilt).isEqualTo(original);
  }

  @Test
  void toBuilderOnDropdownPropertyRoundTripsChoicesAndAllowsOverridingThem() {
    // .id()/.label()/.group()/.binding() are inherited PropertyBuilder setters that return the
    // raw PropertyBuilder type, which would lose access to .choices() (declared only on
    // DropdownPropertyBuilder) if chained afterwards -- call them as separate statements instead.
    var builder = DropdownProperty.builder();
    builder.id("propA");
    builder.label("Label A");
    builder.group("groupA");
    builder.binding(new PropertyBinding.ZeebeInput("propA"));
    builder.choices(
        List.of(
            new DropdownChoice("Choice 1", "choice1"), new DropdownChoice("Choice 2", "choice2")));
    DropdownProperty original = builder.build();

    var roundTripped = original.toBuilder().build();
    assertThat(roundTripped).isEqualTo(original);

    var pruned =
        original.toBuilder().choices(List.of(new DropdownChoice("Choice 1", "choice1"))).build();
    assertThat(pruned.getChoices()).containsExactly(new DropdownChoice("Choice 1", "choice1"));
    // Overriding choices doesn't disturb the other inherited fields.
    assertThat(pruned.getId()).isEqualTo(original.getId());
    assertThat(pruned.getLabel()).isEqualTo(original.getLabel());
    assertThat(pruned.getGroup()).isEqualTo(original.getGroup());
    assertThat(pruned.getBinding()).isEqualTo(original.getBinding());
  }
}
