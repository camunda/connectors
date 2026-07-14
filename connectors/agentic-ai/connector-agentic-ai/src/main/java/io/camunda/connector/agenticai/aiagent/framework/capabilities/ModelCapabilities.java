/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.capabilities;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Provider-neutral capability contract that framework-generic code depends on. Today the only
 * cross-provider consumer is {@code CapabilityAwareToolCallResultStrategy}, which reads modalities
 * only — so this interface is deliberately minimal (three modality lists + the {@link Modality}
 * vocabulary). Provider-specific capability data lives on the provider's own implementing record
 * (e.g. {@code AnthropicModelCapabilities}), never here: every method added here is a tax on every
 * custom provider that must implement it. New methods added in later chunks must be {@code default}
 * so existing custom implementations keep compiling.
 */
public interface ModelCapabilities {

  List<Modality> userMessageModalities();

  List<Modality> toolResultModalities();

  List<Modality> assistantMessageModalities();

  /**
   * Modality vocabulary shared across user-message, tool-result and assistant-message locations.
   * Keeps the lowercase {@link JsonProperty} annotations so the resolver's {@code JsonNode} ->
   * sparse DTO round-trip (used to project the merged capability matrix layers) can deserialise the
   * lowercase modality values used throughout the bundled YAML.
   *
   * <p>A {@code Modality} is the CONTENT-TYPE CLASS a provider natively ingests at a given
   * location, derived from a content's MIME type (the MIME -&gt; {@code Modality} mapping itself
   * lands in a later chunk, alongside the multimodal content converters). It is a resolver/SPI
   * concept, distinct from the Camunda {@code Document} abstraction: a Camunda {@code Document} can
   * carry any MIME type, and its MIME type decides which {@code Modality} it maps to; a {@code
   * Modality} is not a document, it's the bucket a document's (or any content's) MIME type falls
   * into.
   *
   * <ul>
   *   <li>{@link #TEXT}: text payloads, including text-family files distinguished from binary
   *       documents only by MIME (e.g. {@code text/csv}, {@code application/json} -> {@code TEXT},
   *       not {@code DOCUMENT}).
   *   <li>{@link #IMAGE}: {@code image/*} content the model accepts inline.
   *   <li>{@link #DOCUMENT}: a non-text file the model ingests as a document — today primarily
   *       {@code application/pdf}.
   *   <li>{@link #AUDIO} / {@link #VIDEO}: native audio/video input modalities. Kept distinct from
   *       {@code DOCUMENT} because providers expose them via separate input channels (dedicated
   *       audio/video content blocks), not as document uploads.
   * </ul>
   */
  enum Modality {
    @JsonProperty("text")
    TEXT,
    @JsonProperty("image")
    IMAGE,
    @JsonProperty("document")
    DOCUMENT,
    @JsonProperty("audio")
    AUDIO,
    @JsonProperty("video")
    VIDEO
  }
}
