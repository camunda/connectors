/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.Citation;
import software.amazon.awssdk.services.bedrockruntime.model.CitationGeneratedContent;
import software.amazon.awssdk.services.bedrockruntime.model.CitationLocation;
import software.amazon.awssdk.services.bedrockruntime.model.CitationSourceContent;
import software.amazon.awssdk.services.bedrockruntime.model.CitationsContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ReasoningTextBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import software.amazon.awssdk.services.bedrockruntime.model.WebLocation;

/**
 * Round-trip property test for {@link BedrockSdkPojoCodec}: build a populated {@code ContentBlock},
 * {@link BedrockSdkPojoCodec#capture(software.amazon.awssdk.core.SdkPojo) capture} it, {@link
 * BedrockSdkPojoCodec#replay(Map, java.util.function.Supplier) replay} the captured map, and assert
 * the replayed instance is equal to the original per {@code ContentBlock}'s own {@code
 * equalsBySdkFields()}. Covers every {@code ContentBlock} member reachable without a capability
 * matrix (design spec &sect;5.4): {@code text}, {@code toolUse} (with a non-trivial {@link
 * Document} input exercising every {@code Document} value kind), {@code reasoningContent} with a
 * {@code reasoningText}, {@code reasoningContent} with {@code redactedContent}, and {@code
 * citationsContent} (the deepest nesting: recursive unions and lists).
 */
class BedrockSdkPojoCodecTest {

  @Test
  void roundTripsText() {
    final ContentBlock original = ContentBlock.fromText("Here is my response.");

    final Map<String, Object> captured = BedrockSdkPojoCodec.capture(original);
    assertThat(captured).containsOnlyKeys("text").containsEntry("text", "Here is my response.");

    final ContentBlock replayed = BedrockSdkPojoCodec.replay(captured, ContentBlock::builder);
    assertThat(replayed).isEqualTo(original);
  }

  @Test
  void roundTripsToolUseWithNonTrivialDocumentInput() {
    final Document input =
        Document.mapBuilder()
            .putString("city", "Zurich")
            .putNumber("population", 434008)
            .putNumber("temperatureCelsius", new BigDecimal("18.5"))
            .putBoolean("capital", false)
            .putNull("comment")
            .putList("districts", lb -> lb.addString("Altstadt").addString("Kreis 5"))
            .putMap(
                "coordinates",
                mb ->
                    mb.putNumber("lat", new BigDecimal("47.3769"))
                        .putNumber("lon", new BigDecimal("8.5417")))
            .build();

    final ContentBlock original =
        ContentBlock.fromToolUse(
            ToolUseBlock.builder().toolUseId("tooluse-1").name("getWeather").input(input).build());

    final Map<String, Object> captured = BedrockSdkPojoCodec.capture(original);
    assertThat(captured).containsOnlyKeys("toolUse");

    final ContentBlock replayed = BedrockSdkPojoCodec.replay(captured, ContentBlock::builder);
    assertThat(replayed).isEqualTo(original);
    assertThat(replayed.toolUse().input()).isEqualTo(input);
  }

  @Test
  void roundTripsReasoningContentWithSignedReasoningText() {
    final ContentBlock original =
        ContentBlock.fromReasoningContent(
            ReasoningContentBlock.fromReasoningText(
                ReasoningTextBlock.builder()
                    .text("The user is asking about the weather, so I should call getWeather.")
                    .signature("sig-9f3a7c21")
                    .build()));

    final Map<String, Object> captured = BedrockSdkPojoCodec.capture(original);
    assertThat(captured).containsOnlyKeys("reasoningContent");

    final ContentBlock replayed = BedrockSdkPojoCodec.replay(captured, ContentBlock::builder);
    assertThat(replayed).isEqualTo(original);
  }

  @Test
  void roundTripsReasoningContentWithRedactedContent() {
    final ContentBlock original =
        ContentBlock.fromReasoningContent(
            ReasoningContentBlock.fromRedactedContent(
                SdkBytes.fromUtf8String("opaque-encrypted-reasoning-payload")));

    final Map<String, Object> captured = BedrockSdkPojoCodec.capture(original);
    assertThat(captured).containsOnlyKeys("reasoningContent");

    final ContentBlock replayed = BedrockSdkPojoCodec.replay(captured, ContentBlock::builder);
    assertThat(replayed).isEqualTo(original);
  }

  @Test
  void roundTripsCitationsContent() {
    final CitationLocation location =
        CitationLocation.fromWeb(
            WebLocation.builder().url("https://example.com/article").domain("example.com").build());

    final Citation citation =
        Citation.builder()
            .title("Example Article")
            .source("source-doc-1")
            .sourceContent(
                List.of(CitationSourceContent.fromText("The relevant excerpt from the source.")))
            .location(location)
            .build();

    final CitationsContentBlock citationsContentBlock =
        CitationsContentBlock.builder()
            .content(
                List.of(
                    CitationGeneratedContent.fromText("Generated content backed by a citation.")))
            .citations(List.of(citation))
            .build();

    final ContentBlock original = ContentBlock.fromCitationsContent(citationsContentBlock);

    final Map<String, Object> captured = BedrockSdkPojoCodec.capture(original);
    assertThat(captured).containsOnlyKeys("citationsContent");

    final ContentBlock replayed = BedrockSdkPojoCodec.replay(captured, ContentBlock::builder);
    assertThat(replayed).isEqualTo(original);
    assertThat(replayed.citationsContent()).isEqualTo(citationsContentBlock);
  }
}
