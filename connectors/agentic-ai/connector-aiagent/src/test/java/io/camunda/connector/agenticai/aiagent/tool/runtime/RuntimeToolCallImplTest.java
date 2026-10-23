/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class RuntimeToolCallImplTest {

  private final ObjectMapper argumentsMapper =
      RuntimeToolCallImpl.argumentsMapper(new ObjectMapper());

  record NoteArguments(String title, @Nullable String note) {}

  record MixedArguments(
      String text, int count, boolean flag, List<String> tags, Map<String, Object> extra) {}

  record CountArguments(int count) {}

  record DocumentArguments(DocumentId document, @Nullable String note) {}

  record TextArguments(@Nullable String body) {}

  record DocumentsArguments(List<DocumentId> documents) {}

  private static final DocumentId DOC = DocumentId.of("doc-1");

  private RuntimeToolCallImpl call(Map<String, Object> arguments) {
    return new RuntimeToolCallImpl("call-1", "tool", arguments, argumentsMapper);
  }

  @Test
  void exposesTheIdAndTheNameOfTheCall() {
    final var call = call(Map.of());

    assertThat(call.id()).isEqualTo("call-1");
    assertThat(call.name()).isEqualTo("tool");
  }

  @Test
  void bindsTheJsonTypesOfTheArgumentsToTheProperties() {
    final var arguments =
        call(Map.of(
                "text",
                "hello",
                "count",
                3,
                "flag",
                true,
                "tags",
                List.of("a", "b"),
                "extra",
                Map.of("k", "v")))
            .argumentsAs(MixedArguments.class);

    assertThat(arguments)
        .isEqualTo(new MixedArguments("hello", 3, true, List.of("a", "b"), Map.of("k", "v")));
  }

  @Test
  void aDocumentParameterBindsToADocumentId() {
    final var arguments =
        call(Map.of("document", DOC, "note", "hello")).argumentsAs(DocumentArguments.class);

    assertThat(arguments).isEqualTo(new DocumentArguments(DOC, "hello"));
  }

  @Test
  void aDocumentBindsToTheTextOfItsIdForAStringProperty() {
    assertThat(call(Map.of("body", DOC)).argumentsAs(TextArguments.class).body())
        .isEqualTo("doc-1");
  }

  @Test
  void documentsInAListBindToDocumentIds() {
    final var other = DocumentId.of("doc-2");

    final var arguments =
        call(Map.of("documents", List.of(DOC, other))).argumentsAs(DocumentsArguments.class);

    assertThat(arguments.documents()).containsExactly(DOC, other);
  }

  @Test
  void missingArgumentsAreNullAndUnknownArgumentsAreIgnored() {
    final var arguments = call(Map.of("title", "t", "other", "x")).argumentsAs(NoteArguments.class);

    assertThat(arguments.title()).isEqualTo("t");
    assertThat(arguments.note()).isNull();
  }

  @Test
  void anExplicitNullArgumentIsKept() {
    final var withNull = new LinkedHashMap<String, Object>();
    withNull.put("title", "t");
    withNull.put("note", null);

    final var call = call(withNull);

    assertThat(call.arguments()).containsEntry("note", null);
    assertThat(call.argumentsAs(NoteArguments.class).note()).isNull();
  }

  @Test
  void keepsTheOrderOfTheArgumentsAndIsUnmodifiable() {
    final var ordered = new LinkedHashMap<String, Object>();
    ordered.put("zulu", 1);
    ordered.put("alpha", 2);

    final var call = call(ordered);
    ordered.put("late", 3);

    assertThat(call.arguments().keySet()).containsExactly("zulu", "alpha");
    assertThatThrownBy(() -> call.arguments().put("x", 1))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void argumentsThatDoNotFitTheTypeFailWithAToolExceptionWithoutTechnicalDetail() {
    final var call = call(Map.of("count", "many"));

    assertThatThrownBy(() -> call.argumentsAs(CountArguments.class))
        .isInstanceOf(RuntimeToolException.class)
        .hasMessage("Invalid arguments for tool 'tool'.");
  }
}
