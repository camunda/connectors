/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.PackageMatcher;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import com.tngtech.archunit.library.dependencies.SliceAssignment;
import com.tngtech.archunit.library.dependencies.SliceIdentifier;
import com.tngtech.archunit.library.dependencies.SliceRule;
import java.util.List;

/**
 * Enforces that sibling packages do not depend on each other cyclically.
 *
 * <p>The sub-packages of the chat model providers, the conversation stores, the A2A client and the
 * tools schema are free of cycles. The first-level packages of the AI Agent connector, the agent
 * core and the MCP client are free of cycles except for the known cycles in {@link #KNOWN_CYCLES}.
 * They are the cycles listed as a follow-up in ADR 015 and must only shrink: breaking a cycle means
 * deleting its entry.
 */
@AnalyzeClasses(packages = AgenticAiModules.ROOT, importOptions = DoNotIncludeTestsOrTestJars.class)
class PackageCyclesArchTest {

  private static final String BECAUSE =
      "cyclic package dependencies prevent extracting or changing a package on its own";

  @ArchTest
  static final ArchRule SIBLING_PACKAGES_ARE_FREE_OF_CYCLES =
      CompositeArchRule.of(freeOfCycles("aiagent.chatmodel.provider.(*).."))
          .and(freeOfCycles("aiagent.memory.conversation.(*).."))
          .and(freeOfCycles("a2a.client.(*).."))
          .and(freeOfCycles("tools.schema.(*).."));

  /**
   * The known cycles between first-level packages, one entry per cycle. An entry allows the
   * dependency that closes the cycle and should go away: from the package that the other one should
   * not know to the package that is allowed to know it. Breaking a cycle means deleting its entry.
   */
  private static final List<AllowedDependency> KNOWN_CYCLES =
      List.of(
          // mcp.client.framework and mcp.client.configuration
          allow("mcp.client.framework..", "mcp.client.configuration.."));

  @ArchTest
  static final ArchRule AI_AGENT_AND_MCP_CLIENT_PACKAGES_ARE_FREE_OF_CYCLES_BUT_THE_KNOWN_ONES =
      CompositeArchRule.of(
              freeOfCyclesBut(
                  KNOWN_CYCLES, firstLevelPackagesOf(AgenticAiModules.AI_AGENT, "aiagent.(*)..")))
          .and(
              freeOfCyclesBut(
                  KNOWN_CYCLES, firstLevelPackagesOf(AgenticAiModules.CORE, "aiagent.(*)..")))
          .and(
              freeOfCyclesBut(
                  KNOWN_CYCLES,
                  firstLevelPackagesOf(AgenticAiModules.MCP_CLIENT, "mcp.client.(*)..")));

  private static ArchRule freeOfCyclesBut(
      final List<AllowedDependency> knownCycles, final SliceAssignment assignment) {
    SliceRule rule = slices().assignedFrom(assignment).should().beFreeOfCycles().because(BECAUSE);
    for (final AllowedDependency dependency : knownCycles) {
      rule = rule.ignoreDependency(dependency.from(), dependency.to());
    }
    return rule;
  }

  private static AllowedDependency allow(final String from, final String to) {
    return new AllowedDependency(
        resideInAPackage(AgenticAiModules.pkg(from)), resideInAPackage(AgenticAiModules.pkg(to)));
  }

  private record AllowedDependency(
      DescribedPredicate<JavaClass> from, DescribedPredicate<JavaClass> to) {}

  private static ArchRule freeOfCycles(final String relativeSlicePattern) {
    return slices()
        .matching(AgenticAiModules.pkg(relativeSlicePattern))
        .should()
        .beFreeOfCycles()
        .because(BECAUSE);
  }

  /** Slices the classes of the module by the first-level package that matches the pattern. */
  private static SliceAssignment firstLevelPackagesOf(
      final AgenticAiModules module, final String relativeSlicePattern) {
    final PackageMatcher matcher = PackageMatcher.of(AgenticAiModules.pkg(relativeSlicePattern));
    final DescribedPredicate<JavaClass> inModule = module.root();
    return new SliceAssignment() {
      @Override
      public String getDescription() {
        return "first-level packages of " + module.artifactId();
      }

      @Override
      public SliceIdentifier getIdentifierOf(final JavaClass javaClass) {
        if (!inModule.test(javaClass)) {
          return SliceIdentifier.ignore();
        }
        return matcher
            .match(javaClass.getPackageName())
            .map(result -> SliceIdentifier.of(result.getGroup(1)))
            .orElse(SliceIdentifier.ignore());
      }
    };
  }
}
