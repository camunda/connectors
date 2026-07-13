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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.validator.core.Finding;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperationDescriptionConnectorNameRuleTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Path FILE = Path.of("my-connector/element-templates/foo.json");
  private final OperationDescriptionConnectorNameRule rule =
      new OperationDescriptionConnectorNameRule();

  @Test
  void descriptionReferencesConnectorName_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "CSV Connector",
          "steps": [
            { "name": "Read CSV", "presetId": "p",
              "description": "Read a CSV document and return its records" } ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void descriptionMissingConnectorName_flagged() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "GitHub Outbound Connector",
          "steps": [
            { "name": "Issues", "presetId": "p",
              "description": "Create, read, update and search issues and comments" } ] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps/0/description");
    assertThat(findings.get(0).message()).contains("GitHub");
  }

  @Test
  void noDescription_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "GitHub Outbound Connector",
          "steps": [ { "name": "Issues", "presetId": "p", "keywords": ["x"] } ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void blankDescription_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "GitHub Outbound Connector",
          "steps": [ { "name": "Issues", "presetId": "p", "description": "   " } ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void multiWordName_matchesAnySignificantWord_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "WhatsApp Business Outbound Connector",
          "steps": [
            { "name": "Send plain text message", "presetId": "p",
              "description": "Send a plain text message to a recipient via WhatsApp" } ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void parentheticalSuffixStripped() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "Foo Connector (Send Task)",
          "steps": [
            { "name": "op", "presetId": "p", "description": "does nothing useful" } ] }
        """);
    assertThat(rule.apply(FILE, template)).hasSize(1);
  }

  @Test
  void nestedLeafChecked() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "GitHub Outbound Connector",
          "steps": [
            { "name": "g", "steps": [
              { "name": "leaf", "presetId": "p", "description": "manage things" } ] } ] }
        """);
    List<Finding> findings = rule.apply(FILE, template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).jsonPointer()).isEqualTo("/steps/0/steps/0/description");
  }

  @Test
  void groupNodes_notFlagged() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "GitHub Outbound Connector",
          "steps": [
            { "name": "g", "description": "a group without connector name",
              "steps": [
                { "name": "leaf", "presetId": "p",
                  "description": "manage GitHub repositories" } ] } ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  @Test
  void ignoredConnector_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "Agentic AI Outbound Connector",
          "steps": [
            { "name": "op", "presetId": "p", "description": "unrelated description" } ] }
        """);
    assertThat(rule.apply(Path.of("connectors/agentic-ai/element-templates/aws.json"), template))
        .isEmpty();
  }

  @Test
  void connectorNameOverride_flagsDescriptionMissingOverrideName() throws Exception {
    // camunda-message is mapped to "Camunda"; its template name derives to "Send Message".
    JsonNode template =
        read(
            """
        { "name": "Send Message Connector (Send Task)",
          "steps": [
            { "name": "Correlate message", "presetId": "p",
              "description": "Correlate a message to a running process instance" } ] }
        """);
    List<Finding> findings =
        rule.apply(
            Path.of("connectors/camunda-message/element-templates/send-task.json"), template);
    assertThat(findings).hasSize(1);
    assertThat(findings.get(0).message()).contains("Camunda");
  }

  @Test
  void connectorNameOverride_passesWhenOverrideNamePresent() throws Exception {
    JsonNode template =
        read(
            """
        { "name": "Send Message Connector (Send Task)",
          "steps": [
            { "name": "Publish message", "presetId": "p",
              "description": "Publish a message to Camunda with buffering support" } ] }
        """);
    assertThat(
            rule.apply(
                Path.of("connectors/camunda-message/element-templates/send-task.json"), template))
        .isEmpty();
  }

  @Test
  void templateWithoutName_noFindings() throws Exception {
    JsonNode template =
        read(
            """
        { "steps": [ { "name": "op", "presetId": "p", "description": "anything" } ] }
        """);
    assertThat(rule.apply(FILE, template)).isEmpty();
  }

  private static JsonNode read(String json) throws Exception {
    return MAPPER.readTree(json);
  }
}
