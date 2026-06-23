/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.request.SandboxConfiguration.DaytonaSandboxConfiguration;
import io.camunda.connector.api.document.Document;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillsSystemPromptContributorTest {

  @Mock private SkillResolver skillResolver;
  @Mock private AgentExecutionContext executionContext;
  @Mock private AgentContext agentContext;
  @Mock private AgentConfiguration agentConfiguration;

  private SkillsSystemPromptContributor contributor;

  /** A non-disabled sandbox configuration used to signal "sandbox is present". */
  private static final DaytonaSandboxConfiguration SANDBOX =
      new DaytonaSandboxConfiguration("dummy-api-key", null, null, null, null);

  @BeforeEach
  void setUp() {
    contributor = new SkillsSystemPromptContributor(skillResolver);
    // executionContext.configuration() is stubbed per-test via givenConfiguration()
    // so it is not declared here — avoids UnnecessaryStubbingException for getOrder test
  }

  /** Stubs executionContext → agentConfiguration for tests that exercise contribute(). */
  private void givenConfiguration() {
    when(executionContext.configuration()).thenReturn(agentConfiguration);
  }

  // ---------------------------------------------------------------------------
  // Happy path
  // ---------------------------------------------------------------------------

  @Test
  void contribute_sandboxPresentAndSkillsResolved_returnsAvailableSkillsBlock() {
    givenConfiguration();
    var doc = mock(Document.class);
    var skill1 = new Skill("pdf-tools", "Extract, merge and fill PDF forms.", "body", List.of());
    var skill2 = new Skill("csv-parser", "Parse and transform CSV files.", "body2", List.of());

    when(agentConfiguration.sandboxConfiguration()).thenReturn(Optional.of(SANDBOX));
    when(agentConfiguration.skills()).thenReturn(List.of(doc));
    when(skillResolver.resolve(List.of(doc))).thenReturn(List.of(skill1, skill2));

    String result = contributor.contribute(executionContext, agentContext);

    assertThat(result).isNotNull();
    assertThat(result).contains("<available_skills>");
    assertThat(result).contains("</available_skills>");

    // skill1 assertions
    assertThat(result).contains("name=\"pdf-tools\"");
    assertThat(result).contains("Extract, merge and fill PDF forms.");

    // skill2 assertions
    assertThat(result).contains("name=\"csv-parser\"");
    assertThat(result).contains("Parse and transform CSV files.");

    // The absolute workspace location must NOT be advertised here — it is unknown before a sandbox
    // session exists and is reported by the sandbox_load_skill result instead.
    assertThat(result).doesNotContain("location=");
  }

  @Test
  void contribute_sandboxPresentAndSkillsResolved_doesNotContainSkillMdBody() {
    givenConfiguration();
    var doc = mock(Document.class);
    var skill = new Skill("pdf-tools", "Extract PDFs.", "FULL SKILL.MD BODY CONTENT", List.of());

    when(agentConfiguration.sandboxConfiguration()).thenReturn(Optional.of(SANDBOX));
    when(agentConfiguration.skills()).thenReturn(List.of(doc));
    when(skillResolver.resolve(List.of(doc))).thenReturn(List.of(skill));

    String result = contributor.contribute(executionContext, agentContext);

    assertThat(result).isNotNull();
    // The body should NOT appear — only name and description
    assertThat(result).doesNotContain("FULL SKILL.MD BODY CONTENT");
  }

  @Test
  void contribute_singleSkill_skillEntryFormatIsCorrect() {
    givenConfiguration();
    var doc = mock(Document.class);
    var skill = new Skill("my-skill", "Does something useful.", "body", List.of());

    when(agentConfiguration.sandboxConfiguration()).thenReturn(Optional.of(SANDBOX));
    when(agentConfiguration.skills()).thenReturn(List.of(doc));
    when(skillResolver.resolve(List.of(doc))).thenReturn(List.of(skill));

    String result = contributor.contribute(executionContext, agentContext);

    assertThat(result).contains("<skill name=\"my-skill\">");
    assertThat(result).contains("Does something useful.");
    assertThat(result).contains("</skill>");
  }

  // ---------------------------------------------------------------------------
  // No contribution cases
  // ---------------------------------------------------------------------------

  @Test
  void contribute_noSandbox_returnsNull() {
    givenConfiguration();
    when(agentConfiguration.sandboxConfiguration()).thenReturn(Optional.empty());

    String result = contributor.contribute(executionContext, agentContext);

    assertThat(result).isNull();
  }

  @Test
  void contribute_sandboxPresentButSkillsNull_returnsNull() {
    givenConfiguration();
    when(agentConfiguration.sandboxConfiguration()).thenReturn(Optional.of(SANDBOX));
    when(agentConfiguration.skills()).thenReturn(null);

    String result = contributor.contribute(executionContext, agentContext);

    assertThat(result).isNull();
  }

  @Test
  void contribute_sandboxPresentButSkillsEmpty_returnsNull() {
    givenConfiguration();
    when(agentConfiguration.sandboxConfiguration()).thenReturn(Optional.of(SANDBOX));
    when(agentConfiguration.skills()).thenReturn(List.of());

    String result = contributor.contribute(executionContext, agentContext);

    assertThat(result).isNull();
  }

  @Test
  void contribute_sandboxPresentSkillsConfiguredButResolverReturnsEmpty_returnsNull() {
    givenConfiguration();
    var doc = mock(Document.class);

    when(agentConfiguration.sandboxConfiguration()).thenReturn(Optional.of(SANDBOX));
    when(agentConfiguration.skills()).thenReturn(List.of(doc));
    when(skillResolver.resolve(List.of(doc))).thenReturn(List.of());

    String result = contributor.contribute(executionContext, agentContext);

    assertThat(result).isNull();
  }

  // ---------------------------------------------------------------------------
  // Resilience: resolver throws unexpectedly
  // ---------------------------------------------------------------------------

  @Test
  void contribute_resolverThrowsUnexpectedly_returnsNull() {
    givenConfiguration();
    var doc = mock(Document.class);

    when(agentConfiguration.sandboxConfiguration()).thenReturn(Optional.of(SANDBOX));
    when(agentConfiguration.skills()).thenReturn(List.of(doc));
    when(skillResolver.resolve(List.of(doc)))
        .thenThrow(new RuntimeException("unexpected resolver failure"));

    // Must not propagate — system-prompt composition must not break
    String result = contributor.contribute(executionContext, agentContext);

    assertThat(result).isNull();
  }

  // ---------------------------------------------------------------------------
  // Order value
  // ---------------------------------------------------------------------------

  @Test
  void getOrder_returnsExpectedValue() {
    assertThat(contributor.getOrder()).isEqualTo(SkillsSystemPromptContributor.ORDER);
    assertThat(contributor.getOrder()).isEqualTo(90);
  }
}
