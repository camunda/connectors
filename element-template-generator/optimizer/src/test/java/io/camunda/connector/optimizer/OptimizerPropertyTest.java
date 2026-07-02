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
package io.camunda.connector.optimizer;

import static io.camunda.connector.optimizer.TestTemplates.*;

import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.optimizer.core.Optimizer;
import io.camunda.connector.optimizer.core.PropertyUtils;
import io.camunda.connector.optimizer.core.TemplateLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.diff.DefaultNodeMatcher;
import org.xmlunit.diff.Diff;
import org.xmlunit.diff.ElementSelectors;

/**
 * End-to-end equivalence check for the optimizer pipeline.
 *
 * <p>For every generated multi-operation template, and for every value of its discriminator,
 * applying the optimized template via {@code element-templates-cli} must produce a BPMN diagram
 * identical (modulo whitespace and attribute order) to applying the unoptimized template. This pins
 * the behavioural guarantee the optimizer is meant to provide.
 *
 * <p>The test depends on the npm-published {@code @camunda/element-templates-cli} binary being on
 * {@code $PATH}. When it is missing the entire class is reported as <em>skipped</em> — not silently
 * passed — so CI must install the CLI to enforce the equivalence gate.
 */
@EnabledIf("io.camunda.connector.optimizer.OptimizerPropertyTest#elementTemplatesCliAvailable")
class OptimizerPropertyTest {

  @TempDir Path tempDir;

  /**
   * Gate for {@code @EnabledIf}. Locally a missing CLI means "skip"; under CI a missing CLI is a
   * hard failure so the equivalence gate can't accidentally vanish behind a green build. CI is
   * detected via the {@code CI} env var (set by GitHub Actions, GitLab CI, CircleCI, etc.) or via
   * {@code OPTIMIZER_REQUIRE_CLI=true}.
   */
  static boolean elementTemplatesCliAvailable() {
    boolean available = ElementTemplatesCli.available();
    if (!available && requireCliIsSet()) {
      throw new AssertionError(
          "element-templates-cli is required in this environment but not on PATH — install"
              + " @camunda/element-templates-cli (npm i -g @camunda/element-templates-cli)");
    }
    return available;
  }

  private static boolean requireCliIsSet() {
    return "true".equalsIgnoreCase(System.getenv("CI"))
        || "true".equalsIgnoreCase(System.getenv("OPTIMIZER_REQUIRE_CLI"));
  }

  static Stream<Arguments> generatedTemplates() {
    List<Arguments> args = new ArrayList<>();
    int caseId = 0;
    for (int numOps : new int[] {2, 3, 5}) {
      for (int shared : new int[] {1, 2, 3}) {
        for (int unshared : new int[] {0, 1}) {
          for (boolean unconditional : new boolean[] {false, true}) {
            String name =
                "ops="
                    + numOps
                    + " shared="
                    + shared
                    + " unshared="
                    + unshared
                    + (unconditional ? " +unconditional" : "");
            args.add(
                Arguments.of(
                    name, buildTemplate(caseId++, numOps, shared, unshared, unconditional)));
          }
        }
      }
    }
    return args.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("generatedTemplates")
  void optimizedTemplateAppliesIdenticallyForEveryOperation(String label, ElementTemplate template)
      throws Exception {
    String discriminatorId = "operationId";
    List<String> discriminatorValues = discriminatorValues(template, discriminatorId);

    ElementTemplate optimized = Optimizer.defaultPipeline().optimize(template);

    for (String value : discriminatorValues) {
      String originalBpmn = applyTemplate(template, discriminatorId, value, "orig-" + value);
      String optimizedBpmn = applyTemplate(optimized, discriminatorId, value, "opt-" + value);

      Diff diff =
          DiffBuilder.compare(originalBpmn)
              .withTest(optimizedBpmn)
              .ignoreWhitespace()
              .ignoreComments()
              .withNodeMatcher(new DefaultNodeMatcher(ElementSelectors.byNameAndAllAttributes))
              .checkForSimilar()
              .build();

      if (diff.hasDifferences()) {
        throw new AssertionError(
            "Optimized template produced different BPMN for "
                + discriminatorId
                + "="
                + value
                + " ("
                + label
                + "):\n"
                + diff);
      }
    }
  }

  private static ElementTemplate buildTemplate(
      int caseId, int numOps, int sharedParamCount, int unsharedParamCount, boolean unconditional) {
    List<String> ops = IntStream.range(0, numOps).mapToObj(i -> "op" + i).toList();
    DropdownProperty.DropdownChoice[] choices =
        ops.stream().map(o -> choice(o, o)).toArray(DropdownProperty.DropdownChoice[]::new);

    List<Property> props = new ArrayList<>();
    props.add(dropdownProperty("operationId", ops.get(0), zeebeInput("operationId"), choices));

    for (int s = 0; s < sharedParamCount; s++) {
      String binding = "shared_" + s;
      String sharedValue = "shared_value_" + s;
      for (String op : ops) {
        props.add(
            hiddenProperty(
                op + "_shared_" + s,
                sharedValue,
                zeebeInput(binding),
                equalsCondition("operationId", op)));
      }
    }

    for (int u = 0; u < unsharedParamCount; u++) {
      String binding = "unshared_" + u;
      for (String op : ops) {
        props.add(
            hiddenProperty(
                op + "_unshared_" + u,
                op + "_value_" + u,
                zeebeInput(binding),
                equalsCondition("operationId", op)));
      }
    }

    if (unconditional) {
      props.add(hiddenProperty("always_present_" + caseId, "constant", zeebeInput("always")));
    }

    return template(props.toArray(Property[]::new));
  }

  private List<String> discriminatorValues(ElementTemplate t, String discriminatorId) {
    return t.properties().stream()
        .filter(p -> discriminatorId.equals(p.getId()))
        .filter(DropdownProperty.class::isInstance)
        .map(DropdownProperty.class::cast)
        .flatMap(d -> d.getChoices().stream())
        .map(DropdownProperty.DropdownChoice::value)
        .filter(Objects::nonNull)
        .toList();
  }

  private String applyTemplate(
      ElementTemplate template, String discriminatorId, String discriminatorValue, String tag)
      throws IOException, InterruptedException {
    Path bpmnFile = tempDir.resolve("diagram-" + tag + ".bpmn");
    Files.writeString(bpmnFile, MINIMAL_BPMN);

    ElementTemplate withDiscriminator =
        withProperty(
            template, discriminatorId, p -> PropertyUtils.withValue(p, discriminatorValue));
    Path templateFile = tempDir.resolve("template-" + tag + ".json");
    TemplateLoader.save(withDiscriminator, templateFile);

    Path outputFile = tempDir.resolve("output-" + tag + ".bpmn");
    ElementTemplatesCli.applyTemplate(bpmnFile, templateFile, "ServiceTask_1", outputFile);
    return Files.readString(outputFile);
  }

  private static ElementTemplate withProperty(
      ElementTemplate template, String propertyId, UnaryOperator<Property> transform) {
    List<Property> updated =
        template.properties().stream()
            .map(p -> propertyId.equals(p.getId()) ? transform.apply(p) : p)
            .toList();
    return new ElementTemplate(
        template.id(),
        template.name(),
        template.version(),
        template.category(),
        template.documentationRef(),
        template.engines(),
        template.description(),
        template.keywords(),
        template.appliesTo(),
        template.elementType(),
        template.groups(),
        updated,
        template.icon(),
        template.steps(),
        template.presets());
  }

  private static final String MINIMAL_BPMN =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                        xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                        xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                        id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
        <bpmn:process id="Process_1" isExecutable="true">
          <bpmn:serviceTask id="ServiceTask_1" name="Test Task" />
        </bpmn:process>
        <bpmndi:BPMNDiagram id="BPMNDiagram_1">
          <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
            <bpmndi:BPMNShape id="ServiceTask_1_di" bpmnElement="ServiceTask_1">
              <dc:Bounds x="160" y="80" width="100" height="80" />
            </bpmndi:BPMNShape>
          </bpmndi:BPMNPlane>
        </bpmndi:BPMNDiagram>
      </bpmn:definitions>
      """;

  /** Thin wrapper around the npm-published element-templates-cli. */
  private static final class ElementTemplatesCli {

    static boolean available() {
      try {
        // element-templates-cli (npm-published) has no --help/--version flag; running it bare
        // exits non-zero with "Missing option". We only care that the binary is on PATH —
        // launchable without IOException — not its exit code for a bad invocation.
        new ProcessBuilder("element-templates-cli")
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor();
        return true;
      } catch (IOException e) {
        return false;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }

    private static final long TIMEOUT_SECONDS = 60;

    static void applyTemplate(Path bpmnFile, Path templateFile, String elementId, Path outputFile)
        throws IOException, InterruptedException {
      Process process =
          new ProcessBuilder(
                  "element-templates-cli",
                  "--diagram",
                  bpmnFile.toString(),
                  "--template",
                  templateFile.toString(),
                  "--element",
                  elementId,
                  "--output",
                  outputFile.toString())
              .redirectErrorStream(true)
              .start();

      // Drain the pipe on a background thread so a chatty CLI can't fill the OS-level
      // stdout buffer and block its own exit while we sit in waitFor().
      var output = new ByteArrayOutputStream();
      Thread drain =
          new Thread(
              () -> {
                try {
                  process.getInputStream().transferTo(output);
                } catch (IOException ignored) {
                  // Process exit will surface the real failure below.
                }
              },
              "element-templates-cli-drain");
      drain.setDaemon(true);
      drain.start();

      try {
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          process.destroyForcibly();
          drain.join();
          throw new UncheckedIOException(
              new IOException(
                  "element-templates-cli timed out after "
                      + TIMEOUT_SECONDS
                      + "s; partial output: "
                      + output.toString(StandardCharsets.UTF_8)));
        }
        drain.join();
      } catch (InterruptedException e) {
        process.destroyForcibly();
        Thread.currentThread().interrupt();
        throw e;
      }

      int exitCode = process.exitValue();
      if (exitCode != 0) {
        throw new UncheckedIOException(
            new IOException(
                "element-templates-cli exit "
                    + exitCode
                    + ": "
                    + output.toString(StandardCharsets.UTF_8)));
      }
    }
  }
}
