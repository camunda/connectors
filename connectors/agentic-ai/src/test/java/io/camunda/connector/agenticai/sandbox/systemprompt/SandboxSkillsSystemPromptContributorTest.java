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
import io.camunda.connector.agenticai.sandbox.discovery.SandboxGatewayToolHandler;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxState;
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
  // Happy path — catalog present in SandboxState (in-memory)
  // -------------------------------------------------------------------------

  @Test
  void contribute_catalogPresentInSandboxState_rendersAvailableSkillsBlock() {
    List<SkillCatalogEntry> catalog =
        List.of(
            new SkillCatalogEntry(
                "pdf-tools",
                "Extract and merge PDF forms.",
                "/ws/.agents/skills/pdf-tools/SKILL.md"),
            new SkillCatalogEntry(
                "web-scraper", "Scrape web pages.", "/ws/.agents/skills/web-scraper/SKILL.md"));
    var sandboxState =
        SandboxState.builder()
            .elementId("Sandbox_1")
            .handle("h1")
            .workDir("/ws")
            .catalog(catalog)
            .build();
    var agentContext =
        AgentContext.builder()
            .state(AgentState.READY)
            .properties(java.util.Map.of(SandboxGatewayToolHandler.PROPERTY_SANDBOX, sandboxState))
            .build();

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
    // the SandboxState is deserialized back from a generic Map.
    ObjectMapper om = new ObjectMapper();
    List<SkillCatalogEntry> nativeCatalog =
        List.of(
            new SkillCatalogEntry(
                "pdf-tools",
                "Extract and merge PDF forms.",
                "/ws/.agents/skills/pdf-tools/SKILL.md"));
    var nativeSandboxState =
        SandboxState.builder()
            .elementId("Sandbox_1")
            .handle("h1")
            .workDir("/ws")
            .catalog(nativeCatalog)
            .build();
    var agentContextNative =
        AgentContext.builder()
            .state(AgentState.READY)
            .properties(
                java.util.Map.of(SandboxGatewayToolHandler.PROPERTY_SANDBOX, nativeSandboxState))
            .build();

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
  // No sandbox state in properties → returns null
  // -------------------------------------------------------------------------

  @Test
  void contribute_noSandboxProperty_returnsNull() {
    var agentContext = AgentContext.builder().state(AgentState.READY).build();

    String result = contributor.contribute(null, agentContext);

    assertThat(result).isNull();
  }

  @Test
  void contribute_emptyProperties_returnsNull() {
    var agentContext = AgentContext.empty();

    String result = contributor.contribute(null, agentContext);

    assertThat(result).isNull();
  }

  // -------------------------------------------------------------------------
  // Sandbox state present but with null catalog → returns null
  // -------------------------------------------------------------------------

  @Test
  void contribute_sandboxStateWithNullCatalog_returnsNull() {
    var sandboxState =
        SandboxState.builder().elementId("Sandbox_1").handle("h1").workDir("/ws").build();
    var agentContext =
        AgentContext.builder()
            .state(AgentState.READY)
            .properties(java.util.Map.of(SandboxGatewayToolHandler.PROPERTY_SANDBOX, sandboxState))
            .build();

    String result = contributor.contribute(null, agentContext);

    assertThat(result).isNull();
  }

  // -------------------------------------------------------------------------
  // Empty catalog → returns null
  // -------------------------------------------------------------------------

  @Test
  void contribute_emptyCatalog_returnsNull() {
    var sandboxState =
        SandboxState.builder()
            .elementId("Sandbox_1")
            .handle("h1")
            .workDir("/ws")
            .catalog(List.of())
            .build();
    var agentContext =
        AgentContext.builder()
            .state(AgentState.READY)
            .properties(java.util.Map.of(SandboxGatewayToolHandler.PROPERTY_SANDBOX, sandboxState))
            .build();

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
