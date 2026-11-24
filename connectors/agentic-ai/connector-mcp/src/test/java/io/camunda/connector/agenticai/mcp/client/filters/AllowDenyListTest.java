/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.mcp.client.filters;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AllowDenyListTest {

  @Test
  void passesOnlyAllowedNames_whenOnlyAllowedIsSet() {
    final var list = AllowDenyList.of(List.of("a"), List.of());

    assertThat(list.isPassing("a")).isTrue();
    assertThat(list.isPassing("b")).isFalse();
  }

  @Test
  void treatsMissingListsAsEmpty() {
    final var list = AllowDenyList.of(null, null);

    assertThat(list).isEqualTo(AllowDenyList.allowingEverything());
  }

  @Test
  void treatsOnlyTheMissingListAsEmpty() {
    assertThat(AllowDenyList.of(null, List.of("a")))
        .isEqualTo(AllowDenyList.of(List.of(), List.of("a")));
    assertThat(AllowDenyList.of(List.of("a"), null))
        .isEqualTo(AllowDenyList.of(List.of("a"), List.of()));
  }

  @Test
  void passesAllButDeniedNames_whenOnlyDeniedIsSet() {
    final var list = AllowDenyList.of(List.of(), List.of("a"));

    assertThat(list.isPassing("a")).isFalse();
    assertThat(list.isPassing("b")).isTrue();
  }

  @Test
  void passesEveryName_whenBothListsAreEmpty() {
    final var list = AllowDenyList.of(List.of(), List.of());

    assertThat(list.isPassing("a")).isTrue();
    assertThat(list.isPassing("b")).isTrue();
  }

  @Test
  void rejectsName_whenItIsAllowedAndDenied() {
    final var list = AllowDenyList.of(List.of("a", "b"), List.of("a"));

    assertThat(list.isPassing("a")).isFalse();
    assertThat(list.isPassing("b")).isTrue();
    assertThat(list.isPassing("c")).isFalse();
  }
}
