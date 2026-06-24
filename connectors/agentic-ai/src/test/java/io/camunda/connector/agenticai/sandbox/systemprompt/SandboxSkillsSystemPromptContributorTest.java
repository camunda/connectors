/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.systemprompt;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxToolDefinitions;
import io.camunda.connector.agenticai.sandbox.discovery.SkillCatalogEntry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SandboxSkillsSystemPromptContributorTest {

  private SandboxSkillsSystemPromptContributor contributor;

  @BeforeEach
  void setUp() {
    contributor = new SandboxSkillsSystemPromptContributor(new ObjectMapper());
  }

  // -------------------------------------------------------------------------
  // Happy path — catalog present as in-memory list
  // -------------------------------------------------------------------------

  @Test
  void contribute_catalogPresentAsObjects_rendersAvailableSkillsBlock() {
    List<SkillCatalogEntry> catalog =
        List.of(
            new SkillCatalogEntry(
                "pdf-tools",
                "Extract and merge PDF forms.",
                "/ws/.agents/skills/pdf-tools/SKILL.md"),
            new SkillCatalogEntry(
                "web-scraper", "Scrape web pages.", "/ws/.agents/skills/web-scraper/SKILL.md"));
    var toolDefs = SandboxToolDefinitions.sandboxToolDefinitions("Sandbox_1", "h1", "/ws", catalog);
    var agentContext =
        AgentContext.builder().state(AgentState.READY).toolDefinitions(toolDefs).build();

    String result = contributor.contribute(null, agentContext);

    assertThat(result).isNotNull();
    assertThat(result).contains("<available_skills>");
    assertThat(result).contains("</available_skills>");
    assertThat(result).contains("sandbox_fs_read");
    assertThat(result).contains("name=\"pdf-tools\"");
    assertThat(result).contains("location=\"/ws/.agents/skills/pdf-tools/SKILL.md\"");
    assertThat(result).contains("directory=\"/ws/.agents/skills/pdf-tools\"");
    assertThat(result).contains("Extract and merge PDF forms.");
    assertThat(result).contains("name=\"web-scraper\"");
    assertThat(result).contains("Scrape web pages.");
  }

  // -------------------------------------------------------------------------
  // Catalog present in serialized form (post-ObjectMapper round-trip)
  // -------------------------------------------------------------------------

  @Test
  void contribute_catalogPresentAsMaps_renderedIdenticallyToObjectForm() {
    // Simulate what happens after Zeebe round-trips the agentContext through JSON serialization:
    // the catalog becomes List<Map<String,Object>> rather than List<SkillCatalogEntry>.
    ObjectMapper om = new ObjectMapper();
    List<SkillCatalogEntry> nativeCatalog =
        List.of(
            new SkillCatalogEntry(
                "pdf-tools",
                "Extract and merge PDF forms.",
                "/ws/.agents/skills/pdf-tools/SKILL.md"));
    var nativeToolDefs =
        SandboxToolDefinitions.sandboxToolDefinitions("Sandbox_1", "h1", "/ws", nativeCatalog);

    var agentContextNative =
        AgentContext.builder().state(AgentState.READY).toolDefinitions(nativeToolDefs).build();
    // Simulate serialization round-trip through ObjectMapper
    AgentContext agentContextRoundTripped;
    try {
      String json = om.writeValueAsString(agentContextNative);
      agentContextRoundTripped = om.readValue(json, AgentContext.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    String nativeResult = contributor.contribute(null, agentContextNative);
    String roundTrippedResult = contributor.contribute(null, agentContextRoundTripped);

    assertThat(roundTrippedResult).isNotNull();
    assertThat(roundTrippedResult).contains("name=\"pdf-tools\"");
    assertThat(roundTrippedResult).contains("Extract and merge PDF forms.");
    // Both should produce equivalent output
    assertThat(roundTrippedResult).isEqualTo(nativeResult);
  }

  // -------------------------------------------------------------------------
  // No catalog metadata → returns null
  // -------------------------------------------------------------------------

  @Test
  void contribute_noCatalogMetadata_returnsNull() {
    var toolDefs = SandboxToolDefinitions.sandboxToolDefinitions("Sandbox_1", "h1", "/ws", null);
    var agentContext =
        AgentContext.builder().state(AgentState.READY).toolDefinitions(toolDefs).build();

    String result = contributor.contribute(null, agentContext);

    assertThat(result).isNull();
  }

  @Test
  void contribute_emptyToolDefinitions_returnsNull() {
    var agentContext = AgentContext.empty();

    String result = contributor.contribute(null, agentContext);

    assertThat(result).isNull();
  }

  // -------------------------------------------------------------------------
  // Empty catalog → returns null
  // -------------------------------------------------------------------------

  @Test
  void contribute_emptyCatalog_returnsNull() {
    var toolDefs =
        SandboxToolDefinitions.sandboxToolDefinitions("Sandbox_1", "h1", "/ws", List.of());
    var agentContext =
        AgentContext.builder().state(AgentState.READY).toolDefinitions(toolDefs).build();

    String result = contributor.contribute(null, agentContext);

    assertThat(result).isNull();
  }

  // -------------------------------------------------------------------------
  // Order
  // -------------------------------------------------------------------------

  @Test
  void getOrder_returns90() {
    assertThat(contributor.getOrder()).isEqualTo(90);
  }
}
