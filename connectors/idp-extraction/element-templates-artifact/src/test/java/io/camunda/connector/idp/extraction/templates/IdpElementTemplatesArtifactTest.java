/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.templates;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IdpElementTemplatesArtifactTest {

  private static final String RESOURCE_ROOT = IdpElementTemplates.RESOURCE_ROOT;
  private static final Path SOURCE_ROOT = Path.of("..", "element-templates");

  private static final String BASE_EXTRACTION =
      "io.camunda.connector.IdpExtractionOutBoundTemplate.v1";
  private static final String UNSTRUCTURED =
      "io.camunda.connector.IdpUnstructuredExtractionOutBoundTemplate.v1";
  private static final String STRUCTURED =
      "io.camunda.connector.IdpStructuredExtractionOutBoundTemplate.v1";
  private static final String CLASSIFICATION =
      "io.camunda.connector.IdpClassificationOutBoundTemplate.v1";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static Path packagedRoot;
  private static Map<String, JsonNode> packaged;

  @BeforeAll
  static void loadPackagedTemplates() throws URISyntaxException {
    var url = IdpElementTemplatesArtifactTest.class.getClassLoader().getResource(RESOURCE_ROOT);
    assertThat(url).as("templates on the classpath under %s", RESOURCE_ROOT).isNotNull();
    packagedRoot = Path.of(url.toURI());
    packaged = new TreeMap<>();
    for (String file : jsonFiles(packagedRoot)) {
      packaged.put(file, read(packagedRoot.resolve(file)));
    }
  }

  @Test
  void containsEveryCurrentAndVersionedTemplateButNoHybrids() {
    var expected = new TreeSet<>(jsonFiles(SOURCE_ROOT));
    expected.removeIf(file -> file.startsWith("hybrid/"));

    assertThat(packaged.keySet()).containsExactlyInAnyOrderElementsOf(expected);
    assertThat(packaged.keySet()).noneMatch(file -> file.startsWith("hybrid/"));
  }

  @Test
  void everyTemplateHasIdVersionAndEngineRange() {
    packaged.forEach(
        (file, template) -> {
          assertThat(template.path("id").isTextual()).as("%s: id", file).isTrue();
          assertThat(template.path("version").isInt()).as("%s: version", file).isTrue();
          assertThat(template.path("engines").path("camunda").isTextual())
              .as("%s: engines.camunda", file)
              .isTrue();
        });
  }

  @Test
  void idAndVersionAreUnique() {
    var byIdAndVersion =
        packaged.entrySet().stream()
            .collect(
                Collectors.groupingBy(
                    e -> e.getValue().path("id").asText() + "@" + e.getValue().path("version"),
                    Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

    assertThat(byIdAndVersion).allSatisfy((key, files) -> assertThat(files).as(key).hasSize(1));
  }

  /**
   * The template a cluster gets is the highest {@code version} per id whose {@code engines.camunda}
   * range matches, the rule Hub and the modeler use. {@code 0} means no template for that cluster.
   */
  @ParameterizedTest(name = "{0}: base {1}, unstructured {2}, structured {3}, classification {4}")
  @CsvSource({
    "8.7.0,  0, 0, 0, 0",
    "8.8.0,  2, 0, 0, 0",
    "8.9.0,  0, 2, 2, 2",
    "8.10.0, 0, 3, 3, 3",
    "8.11.0, 0, 3, 3, 3",
  })
  void clusterVersionSelectsExpectedTemplateVersion(
      String cluster, int base, int unstructured, int structured, int classification) {
    assertThat(selectedVersion(BASE_EXTRACTION, cluster)).as("base extraction").isEqualTo(base);
    assertThat(selectedVersion(UNSTRUCTURED, cluster)).as("unstructured").isEqualTo(unstructured);
    assertThat(selectedVersion(STRUCTURED, cluster)).as("structured").isEqualTo(structured);
    assertThat(selectedVersion(CLASSIFICATION, cluster))
        .as("classification")
        .isEqualTo(classification);
  }

  private static int selectedVersion(String id, String cluster) {
    return packaged.values().stream()
        .filter(t -> id.equals(t.path("id").asText()))
        .filter(t -> EngineRange.parse(t.path("engines").path("camunda").asText()).matches(cluster))
        .mapToInt(t -> t.path("version").asInt())
        .max()
        .orElse(0);
  }

  private static List<String> jsonFiles(Path root) {
    try (Stream<Path> files = Files.walk(root)) {
      return files
          .filter(p -> p.getFileName().toString().endsWith(".json"))
          .map(p -> root.relativize(p).toString().replace('\\', '/'))
          .sorted()
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static JsonNode read(Path file) {
    try {
      return MAPPER.readTree(file.toFile());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The {@code ^x.y[.z]} and {@code ~x.y[.z]} ranges used by the IDP templates. */
  private record EngineRange(int[] lower, int[] upperExclusive) {

    private static final Pattern RANGE = Pattern.compile("([~^])(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    static EngineRange parse(String range) {
      Matcher m = RANGE.matcher(range.trim());
      assertThat(m.matches()).as("supported engines.camunda range: %s", range).isTrue();
      int major = Integer.parseInt(m.group(2));
      int minor = Integer.parseInt(m.group(3));
      int patch = Optional.ofNullable(m.group(4)).map(Integer::parseInt).orElse(0);
      int[] lower = {major, minor, patch};
      int[] upper =
          "^".equals(m.group(1)) ? new int[] {major + 1, 0, 0} : new int[] {major, minor + 1, 0};
      return new EngineRange(lower, upper);
    }

    boolean matches(String version) {
      int[] v = Stream.of(version.split("\\.")).mapToInt(Integer::parseInt).toArray();
      return compare(v, lower) >= 0 && compare(v, upperExclusive) < 0;
    }

    private static int compare(int[] a, int[] b) {
      for (int i = 0; i < 3; i++) {
        if (a[i] != b[i]) {
          return Integer.compare(a[i], b[i]);
        }
      }
      return 0;
    }
  }
}
