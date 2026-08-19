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
package io.camunda.connector.e2e.agenticai.aiagent.wiremock.spi;

import java.util.Set;

/**
 * A single content part of a {@link RecordedMessage}, in a provider-agnostic shape. {@code kind} is
 * the provider's own raw wire discriminator value (e.g. {@code "text"}, {@code "input_text"},
 * {@code "image"}/{@code "image_url"}, {@code "document"}) — not normalized across providers, since
 * the point of this suite is to see those differences, not paper over them; a fixture keeping the
 * raw kind is also what lets a role/type mismatch (e.g. an {@code input_text} part on an
 * assistant-role message) surface as an assertion failure instead of being erased. {@code text} is
 * non-null only for text-carrying parts.
 */
public record RecordedContentPart(String kind, String text) {

  /**
   * Recognizes every wire vocabulary this suite's fixtures use for a plain-text part, so {@link
   * RecordedMessage#textContent()} joins correctly regardless of provider, even though {@link
   * #kind()} itself stays unnormalized.
   */
  private static final Set<String> TEXT_KINDS = Set.of("text", "input_text", "output_text");

  public boolean isText() {
    return TEXT_KINDS.contains(kind);
  }
}
