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
package io.camunda.connector.validator.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemplateLoaderTest {

  @Test
  void validJson_loadsNode(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("ok.json");
    Files.writeString(file, "{ \"id\": \"foo\", \"version\": 1 }");
    TemplateLoader.Result result = TemplateLoader.load(file);
    assertThat(result.ok()).isTrue();
    assertThat(result.node().get("id").asText()).isEqualTo("foo");
  }

  @Test
  void duplicateKey_emitsDuplicateKeysFinding(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("dup.json");
    Files.writeString(file, "{\n  \"id\": \"foo\",\n  \"id\": \"bar\"\n}");
    TemplateLoader.Result result = TemplateLoader.load(file);
    assertThat(result.ok()).isFalse();
    Finding finding = result.finding();
    assertThat(finding.ruleId()).isEqualTo(TemplateLoader.DUPLICATE_KEYS_RULE);
    assertThat(finding.jsonPointer()).isEqualTo("/");
    assertThat(finding.message()).contains("Duplicate field").contains("id").contains("line");
  }

  @Test
  void duplicateNestedKey_emitsDuplicateKeysFinding(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("dupnested.json");
    Files.writeString(file, "{\n  \"properties\": [\n    { \"id\": \"a\", \"id\": \"b\" }\n  ]\n}");
    TemplateLoader.Result result = TemplateLoader.load(file);
    assertThat(result.ok()).isFalse();
    assertThat(result.finding().ruleId()).isEqualTo(TemplateLoader.DUPLICATE_KEYS_RULE);
  }

  @Test
  void malformedJson_emitsJsonParseFinding(@TempDir Path tmp) throws Exception {
    Path file = tmp.resolve("bad.json");
    Files.writeString(file, "{ \"id\": ");
    TemplateLoader.Result result = TemplateLoader.load(file);
    assertThat(result.ok()).isFalse();
    assertThat(result.finding().ruleId()).isEqualTo(TemplateLoader.JSON_PARSE_RULE);
  }

  @Test
  void missingFile_emitsJsonParseFinding(@TempDir Path tmp) {
    Path missing = tmp.resolve("does-not-exist.json");
    TemplateLoader.Result result = TemplateLoader.load(missing);
    assertThat(result.ok()).isFalse();
    assertThat(result.finding().ruleId()).isEqualTo(TemplateLoader.JSON_PARSE_RULE);
  }
}
