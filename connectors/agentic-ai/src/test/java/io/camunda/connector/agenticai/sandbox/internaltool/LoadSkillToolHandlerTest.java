/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.internaltool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.model.tool.ToolCall;
import io.camunda.connector.agenticai.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.sandbox.provider.fake.InMemorySandboxProvider;
import io.camunda.connector.agenticai.sandbox.skill.Skill;
import io.camunda.connector.agenticai.sandbox.spi.FileEntry;
import io.camunda.connector.agenticai.sandbox.spi.SandboxException;
import io.camunda.connector.agenticai.sandbox.spi.SandboxFileSystem;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSession;
import io.camunda.connector.agenticai.sandbox.spi.SandboxSpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LoadSkillToolHandlerTest {

  private InMemorySandboxProvider provider;
  private SandboxSession session;
  private LoadSkillToolHandler handler;

  @BeforeEach
  void setUp() {
    provider = new InMemorySandboxProvider();
    session = provider.create(SandboxSpec.defaults());
    handler = new LoadSkillToolHandler();
  }

  // ---------------------------------------------------------------------------
  // Test data helpers
  // ---------------------------------------------------------------------------

  private static Skill buildSkill(String name, String description, String body) {
    return new Skill(
        name,
        description,
        body,
        List.of(
            new Skill.SkillFile(
                "SKILL.md",
                ("---\nname: " + name + "\n---\n" + body).getBytes(StandardCharsets.UTF_8)),
            new Skill.SkillFile(
                "scripts/run.sh", "#!/bin/bash\necho hello\n".getBytes(StandardCharsets.UTF_8))));
  }

  private static ToolCall loadCall(String skillName) {
    return ToolCall.builder()
        .id("load-1")
        .name(InternalToolNames.LOAD_SKILL)
        .arguments(Map.of("name", skillName))
        .build();
  }

  private static ToolCall loadCallNoArgs() {
    return ToolCall.builder()
        .id("load-no-args")
        .name(InternalToolNames.LOAD_SKILL)
        .arguments(Map.of())
        .build();
  }

  // ---------------------------------------------------------------------------
  // Happy path — materialize a skill
  // ---------------------------------------------------------------------------

  @Test
  void execute_happyPath_materialisesFilesIntoFilesystem() {
    Skill skill = buildSkill("my-skill", "A test skill", "Do something useful.");
    InternalToolContext ctx = new InternalToolContext(List.of(skill));

    ToolCallResult result = handler.execute(loadCall("my-skill"), session, ctx);

    // Result must not be an error
    assertThat(result.content()).asString().doesNotContain("Error:");
    assertThat(result.id()).isEqualTo("load-1");
    assertThat(result.name()).isEqualTo(InternalToolNames.LOAD_SKILL);

    // SKILL.md must exist at the expected path
    byte[] skillMd = session.fs().read("/workspace/skills/my-skill/SKILL.md");
    assertThat(skillMd).isNotEmpty();

    // Script must also be materialized
    byte[] script = session.fs().read("/workspace/skills/my-skill/scripts/run.sh");
    assertThat(script).isNotEmpty();
  }

  @Test
  void execute_happyPath_resultContainsSkillContentTag() {
    Skill skill = buildSkill("demo", "A demo skill", "These are the instructions.");
    InternalToolContext ctx = new InternalToolContext(List.of(skill));

    ToolCallResult result = handler.execute(loadCall("demo"), session, ctx);

    String content = (String) result.content();
    assertThat(content).contains("<skill_content name=\"demo\"");
    assertThat(content).contains("These are the instructions.");
    assertThat(content).contains("</skill_content>");
  }

  @Test
  void execute_happyPath_resultContainsSkillResourcesTag() {
    Skill skill = buildSkill("demo", "A demo skill", "Instructions here.");
    InternalToolContext ctx = new InternalToolContext(List.of(skill));

    ToolCallResult result = handler.execute(loadCall("demo"), session, ctx);

    String content = (String) result.content();
    assertThat(content).contains("<skill_resources location=\"/workspace/skills/demo\">");
    assertThat(content).contains("- SKILL.md");
    assertThat(content).contains("- scripts/run.sh");
    assertThat(content).contains("</skill_resources>");
  }

  @Test
  void execute_happyPath_resultContainsBundledFilesGuidance() {
    Skill skill = buildSkill("demo", "A demo skill", "Instructions.");
    InternalToolContext ctx = new InternalToolContext(List.of(skill));

    ToolCallResult result = handler.execute(loadCall("demo"), session, ctx);

    assertThat(result.content())
        .asString()
        .contains("Bundled files live under /workspace/skills/demo");
  }

  @Test
  void execute_materialisesUnderSessionWorkDir() {
    // Regression: skill files must be written under the session's workDir, not a hardcoded
    // "/workspace" path. A real Daytona sandbox reports e.g. /home/daytona, and writing to
    // /workspace there fails with HTTP 400 because the path is not writable.
    SandboxFileSystem fs = mock(SandboxFileSystem.class);
    SandboxSession customSession = mock(SandboxSession.class);
    when(customSession.workDir()).thenReturn("/home/daytona");
    when(customSession.fs()).thenReturn(fs);
    // stat fails → not yet materialized, so the handler proceeds to write
    when(fs.stat(anyString())).thenThrow(new SandboxException("no such file"));

    Skill skill = buildSkill("my-skill", "A test skill", "Body.");
    InternalToolContext ctx = new InternalToolContext(List.of(skill));

    ToolCallResult result = handler.execute(loadCall("my-skill"), customSession, ctx);

    assertThat(result.content()).asString().doesNotContain("Error:");

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<FileEntry>> captor = ArgumentCaptor.forClass(List.class);
    verify(fs).writeBatch(captor.capture());
    assertThat(captor.getValue())
        .isNotEmpty()
        .allSatisfy(entry -> assertThat(entry.path()).startsWith("/home/daytona/skills/my-skill/"));

    // The result must advertise the resolved (non-/workspace) location.
    assertThat(result.content()).asString().contains("location=\"/home/daytona/skills/my-skill\"");
  }

  // ---------------------------------------------------------------------------
  // Idempotency — second call returns "already loaded" note
  // ---------------------------------------------------------------------------

  @Test
  void execute_secondCall_returnsAlreadyLoadedNote() {
    Skill skill = buildSkill("idem-skill", "Idempotent skill", "Body.");
    InternalToolContext ctx = new InternalToolContext(List.of(skill));

    // First call: loads the skill
    handler.execute(loadCall("idem-skill"), session, ctx);

    // Second call: must return an already-loaded note, not re-write
    ToolCallResult secondResult = handler.execute(loadCall("idem-skill"), session, ctx);
    String content = (String) secondResult.content();
    assertThat(content).contains("already loaded");
    assertThat(content).doesNotContain("Error:");
    // Must not contain skill_content tag (that is from the first load)
    assertThat(content).doesNotContain("<skill_content");
  }

  // ---------------------------------------------------------------------------
  // Error cases
  // ---------------------------------------------------------------------------

  @Test
  void execute_missingNameArg_returnsError() {
    Skill skill = buildSkill("s", "d", "b");
    InternalToolContext ctx = new InternalToolContext(List.of(skill));

    ToolCallResult result = handler.execute(loadCallNoArgs(), session, ctx);

    assertThat(result.content()).asString().contains("Error:");
    assertThat(result.content()).asString().containsIgnoringCase("name");
  }

  @Test
  void execute_emptySkillsContext_returnsError() {
    InternalToolContext emptyCtx = InternalToolContext.empty();

    ToolCallResult result = handler.execute(loadCall("any-skill"), session, emptyCtx);

    assertThat(result.content()).asString().contains("Error:");
    assertThat(result.content()).asString().contains("no skills are configured");
  }

  @Test
  void execute_unknownSkillName_returnsErrorListingAvailable() {
    Skill s1 = buildSkill("skill-a", "desc", "body");
    Skill s2 = buildSkill("skill-b", "desc", "body");
    InternalToolContext ctx = new InternalToolContext(List.of(s1, s2));

    ToolCallResult result = handler.execute(loadCall("nonexistent"), session, ctx);

    String content = (String) result.content();
    assertThat(content).contains("Error:");
    assertThat(content).contains("unknown skill 'nonexistent'");
    assertThat(content).contains("skill-a");
    assertThat(content).contains("skill-b");
  }

  // ---------------------------------------------------------------------------
  // executedBy tag
  // ---------------------------------------------------------------------------

  @Test
  void execute_resultAlwaysTaggedExecutedBySandbox() {
    Skill skill = buildSkill("tagged", "d", "b");
    InternalToolContext ctx = new InternalToolContext(List.of(skill));

    ToolCallResult result = handler.execute(loadCall("tagged"), session, ctx);

    assertThat(result.properties())
        .containsEntry(
            InternalToolExecutor.PROPERTY_EXECUTED_BY, InternalToolExecutor.EXECUTED_BY_SANDBOX);
  }

  // ---------------------------------------------------------------------------
  // Tool definition
  // ---------------------------------------------------------------------------

  @Test
  void definition_hasCorrectNameAndSchema() {
    assertThat(handler.name()).isEqualTo(InternalToolNames.LOAD_SKILL);
    assertThat(handler.definition().name()).isEqualTo(InternalToolNames.LOAD_SKILL);
    assertThat(handler.definition().description()).isNotBlank();

    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) handler.definition().inputSchema().get("properties");
    assertThat(props).containsKey("name");

    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) handler.definition().inputSchema().get("required");
    assertThat(required).contains("name");
  }

  @Test
  void definition_generic_hasNoNameEnum() {
    @SuppressWarnings("unchecked")
    Map<String, Object> props =
        (Map<String, Object>) handler.definition().inputSchema().get("properties");
    @SuppressWarnings("unchecked")
    Map<String, Object> nameProp = (Map<String, Object>) props.get("name");
    assertThat(nameProp).doesNotContainKey("enum");
  }

  @Test
  void definition_withSkillNames_constrainsNameToEnum() {
    var def = handler.definition(List.of("pdf-tools", "csv-parser"));

    @SuppressWarnings("unchecked")
    Map<String, Object> props = (Map<String, Object>) def.inputSchema().get("properties");
    @SuppressWarnings("unchecked")
    Map<String, Object> nameProp = (Map<String, Object>) props.get("name");

    @SuppressWarnings("unchecked")
    List<String> nameEnum = (List<String>) nameProp.get("enum");
    assertThat(nameEnum).containsExactly("pdf-tools", "csv-parser");
  }
}
