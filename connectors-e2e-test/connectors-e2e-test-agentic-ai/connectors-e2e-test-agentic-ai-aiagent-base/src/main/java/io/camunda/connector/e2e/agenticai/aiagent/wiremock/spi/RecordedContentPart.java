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

/**
 * A single content part of a {@link RecordedMessage}, in a provider-agnostic shape. {@code kind} is
 * the provider's own discriminator value (e.g. {@code "text"}, {@code "image"}/{@code "image_url"},
 * {@code "document"}) — not normalized across providers, since the point of this suite is to see
 * those differences, not paper over them. {@code text} is non-null only for {@code "text"} parts.
 */
public record RecordedContentPart(String kind, String text) {

  public boolean isText() {
    return "text".equals(kind);
  }
}
