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
package io.camunda.connector.runtime.core.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MimeTypeResolverTest {

  @Test
  void explicitContentTypeWins() {
    assertThat(MimeTypeResolver.resolveContentType("application/x-custom", "data.json"))
        .isEqualTo("application/x-custom");
  }

  @Test
  void explicitBlankFallsThroughToInference() {
    assertThat(MimeTypeResolver.resolveContentType("  ", "data.json"))
        .isEqualTo("application/json");
  }

  @Test
  void inferenceFromJsonExtension() {
    assertThat(MimeTypeResolver.resolveContentType(null, "report.json"))
        .isEqualTo("application/json");
  }

  @Test
  void inferenceFromCsvExtension() {
    assertThat(MimeTypeResolver.resolveContentType(null, "data.csv")).isEqualTo("text/csv");
  }

  @Test
  void inferenceFromTextExtension() {
    assertThat(MimeTypeResolver.resolveContentType(null, "notes.txt")).isEqualTo("text/plain");
  }

  @Test
  void inferenceFromUnknownExtension_fallsBackToOctetStream() {
    assertThat(MimeTypeResolver.resolveContentType(null, "thing.zzzunknown"))
        .isEqualTo("application/octet-stream");
  }

  @Test
  void noExtension_fallsBackToOctetStream() {
    assertThat(MimeTypeResolver.resolveContentType(null, "noextension"))
        .isEqualTo("application/octet-stream");
  }

  @Test
  void nullEverywhere_fallsBackToOctetStream() {
    assertThat(MimeTypeResolver.resolveContentType(null, null))
        .isEqualTo("application/octet-stream");
  }

  @Test
  void blankFileName_fallsBackToOctetStream() {
    assertThat(MimeTypeResolver.resolveContentType(null, "  "))
        .isEqualTo("application/octet-stream");
  }

  @Test
  void trailingDotFileName_fallsBackToOctetStream() {
    // URLConnection.guessContentTypeFromName returns null for "name." (empty extension)
    assertThat(MimeTypeResolver.resolveContentType(null, "name."))
        .isEqualTo("application/octet-stream");
  }

  @Test
  void dotfileWithoutExtension_fallsBackToOctetStream() {
    // URLConnection.guessContentTypeFromName returns null for ".gitignore"
    assertThat(MimeTypeResolver.resolveContentType(null, ".gitignore"))
        .isEqualTo("application/octet-stream");
  }

  @Test
  void multiDotFileName_lastExtensionWins() {
    // For "archive.tar.gz" only the trailing extension is consulted -> .gz
    assertThat(MimeTypeResolver.resolveContentType(null, "archive.tar.gz"))
        .isEqualTo("application/gzip");
  }

  @Test
  void multiDotFileName_unknownLastExtension_fallsBackToOctetStream() {
    // "file.json.bak" -> only ".bak" is checked, which is unknown -> null -> octet-stream
    assertThat(MimeTypeResolver.resolveContentType(null, "file.json.bak"))
        .isEqualTo("application/octet-stream");
  }

  @Test
  void inferenceIsCaseInsensitive() {
    assertThat(MimeTypeResolver.resolveContentType(null, "DATA.JSON"))
        .isEqualTo("application/json");
    assertThat(MimeTypeResolver.resolveContentType(null, "Data.Csv")).isEqualTo("text/csv");
  }
}
