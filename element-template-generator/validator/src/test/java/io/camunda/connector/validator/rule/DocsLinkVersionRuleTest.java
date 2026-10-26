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
package io.camunda.connector.validator.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.validator.core.Finding;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocsLinkVersionRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("connectors/http/element-templates/t.json");
  private final DocsLinkVersionRule rule = new DocsLinkVersionRule();

  @Test
  void flagsUnversionedAndNextLinksAnywhereInTheTemplate() throws Exception {
    var template =
        MAPPER.readTree(
            """
            {
              "documentationRef": "https://docs.camunda.io/docs/components/connectors/",
              "properties": [
                {"tooltip": "See <a href='https://docs.camunda.io/docs/8.10/components/'>docs</a>."},
                {"tooltip": "See <a href='https://docs.camunda.io/docs/next/components/'>docs</a>."}
              ]
            }
            """);

    List<Finding> findings = rule.apply(FILE, template);

    assertThat(findings)
        .extracting(Finding::jsonPointer)
        .containsExactly("/documentationRef", "/properties/1/tooltip");
    assertThat(findings.get(1).message()).contains("https://docs.camunda.io/docs/next/components/");
  }
}
