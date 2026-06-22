/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.sandbox.skill.Skill.SkillFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillBundleReaderTest {

  private SkillBundleReader reader;

  @BeforeEach
  void setUp() {
    reader = new SkillBundleReader();
  }

  // -------------------------------------------------------------------------
  // Zip builder helpers
  // -------------------------------------------------------------------------

  private static byte[] buildZip(ZipEntry... entries) throws IOException {
    // entries must be pre-populated with content; use addEntry helper
    throw new UnsupportedOperationException("use buildZip(EntrySpec...)");
  }

  private record EntrySpec(String name, byte[] content) {
    static EntrySpec of(String name, String text) {
      return new EntrySpec(name, text.getBytes(StandardCharsets.UTF_8));
    }

    static EntrySpec dir(String name) {
      return new EntrySpec(name.endsWith("/") ? name : name + "/", new byte[0]);
    }
  }

  private static byte[] buildZip(EntrySpec... specs) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (EntrySpec spec : specs) {
        ZipEntry entry = new ZipEntry(spec.name());
        if (spec.name().endsWith("/")) {
          // directory entry
          zos.putNextEntry(entry);
          zos.closeEntry();
        } else {
          entry.setSize(spec.content().length);
          zos.putNextEntry(entry);
          zos.write(spec.content());
          zos.closeEntry();
        }
      }
    }
    return baos.toByteArray();
  }

  private static String skillMd(String name, String description) {
    return "---\nname: " + name + "\ndescription: " + description + "\n---\nBody text.\n";
  }

  // -------------------------------------------------------------------------
  // Happy path: files at root
  // -------------------------------------------------------------------------

  @Test
  void read_filesAtRoot_parsesCorrectly() throws IOException {
    byte[] zip =
        buildZip(
            EntrySpec.of("SKILL.md", skillMd("root-skill", "A root skill.")),
            EntrySpec.of("scripts/run.sh", "#!/bin/bash\necho hello"));

    Skill skill = reader.read(zip);

    assertThat(skill.name()).isEqualTo("root-skill");
    assertThat(skill.description()).isEqualTo("A root skill.");
    assertThat(skill.skillMdBody()).contains("Body text.");
  }

  @Test
  void read_filesAtRoot_skillMdInFiles() throws IOException {
    byte[] zip = buildZip(EntrySpec.of("SKILL.md", skillMd("s", "d.")));

    Skill skill = reader.read(zip);

    assertThat(skill.files()).extracting(SkillFile::relativePath).contains("SKILL.md");
  }

  // -------------------------------------------------------------------------
  // Single top-level directory is stripped
  // -------------------------------------------------------------------------

  @Test
  void read_singleTopLevelDir_isStripped() throws IOException {
    byte[] zip =
        buildZip(
            EntrySpec.of("my-skill/SKILL.md", skillMd("my-skill", "Bundled in a dir.")),
            EntrySpec.of("my-skill/scripts/run.sh", "echo hi"),
            EntrySpec.of("my-skill/resources/ref.md", "Reference."));

    Skill skill = reader.read(zip);

    assertThat(skill.name()).isEqualTo("my-skill");
    assertThat(skill.files())
        .extracting(SkillFile::relativePath)
        .containsExactlyInAnyOrder("SKILL.md", "scripts/run.sh", "resources/ref.md");
  }

  @Test
  void read_singleTopLevelDir_noLeadingDirInPaths() throws IOException {
    byte[] zip =
        buildZip(
            EntrySpec.of("my-skill/SKILL.md", skillMd("s", "d.")),
            EntrySpec.of("my-skill/scripts/run.sh", "#!/bin/bash"));

    Skill skill = reader.read(zip);

    for (SkillFile file : skill.files()) {
      assertThat(file.relativePath()).doesNotStartWith("my-skill/");
    }
  }

  // -------------------------------------------------------------------------
  // Bundled scripts/resources are included with correct relative paths
  // -------------------------------------------------------------------------

  @Test
  void read_bundledScriptsIncluded() throws IOException {
    byte[] zip =
        buildZip(
            EntrySpec.of("SKILL.md", skillMd("scripted", "Has scripts.")),
            EntrySpec.of("scripts/extract.sh", "#!/bin/bash\npdftotext $1"),
            EntrySpec.of("scripts/merge.sh", "#!/bin/bash\npdfunite"),
            EntrySpec.of("resources/REFERENCE.md", "# Reference"));

    Skill skill = reader.read(zip);

    assertThat(skill.files())
        .extracting(SkillFile::relativePath)
        .containsExactlyInAnyOrder(
            "SKILL.md", "scripts/extract.sh", "scripts/merge.sh", "resources/REFERENCE.md");
  }

  @Test
  void read_resourceFileNamesExcludesSkillMd() throws IOException {
    byte[] zip =
        buildZip(
            EntrySpec.of("SKILL.md", skillMd("s", "d.")),
            EntrySpec.of("scripts/run.sh", "echo"),
            EntrySpec.of("resources/doc.md", "doc"));

    Skill skill = reader.read(zip);

    assertThat(skill.resourceFileNames())
        .containsExactlyInAnyOrder("scripts/run.sh", "resources/doc.md")
        .doesNotContain("SKILL.md");
  }

  @Test
  void read_fileContentsArePreserved() throws IOException {
    byte[] scriptContent = "#!/bin/bash\necho 'hello world'\n".getBytes(StandardCharsets.UTF_8);
    byte[] zip =
        buildZip(
            EntrySpec.of("SKILL.md", skillMd("s", "d.")),
            new EntrySpec("scripts/run.sh", scriptContent));

    Skill skill = reader.read(zip);

    SkillFile script =
        skill.files().stream()
            .filter(f -> f.relativePath().equals("scripts/run.sh"))
            .findFirst()
            .orElseThrow();
    assertThat(script.content()).isEqualTo(scriptContent);
  }

  // -------------------------------------------------------------------------
  // Missing SKILL.md → throw
  // -------------------------------------------------------------------------

  @Test
  void read_missingSkillMd_throwsInvalidSkillException() throws IOException {
    byte[] zip = buildZip(EntrySpec.of("README.md", "Just a readme, no SKILL.md."));

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("SKILL.md");
  }

  @Test
  void read_skillMdInSubdirNotRoot_throwsWhenNoRootSkillMd() throws IOException {
    // Two top-level dirs → no common prefix strip; SKILL.md ends up as "a/SKILL.md"
    byte[] zip =
        buildZip(
            EntrySpec.of("a/SKILL.md", skillMd("s", "d.")), EntrySpec.of("b/other.txt", "other"));

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("SKILL.md");
  }

  // -------------------------------------------------------------------------
  // Zip-slip guard
  // -------------------------------------------------------------------------

  @Test
  void read_zipSlipEntry_throwsInvalidSkillException() throws IOException {
    byte[] zip =
        buildZip(
            EntrySpec.of("SKILL.md", skillMd("s", "d.")),
            EntrySpec.of("../evil.sh", "malicious content"));

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("zip-slip");
  }

  @Test
  void read_zipSlipNestedEntry_throwsInvalidSkillException() throws IOException {
    byte[] zip =
        buildZip(
            EntrySpec.of("SKILL.md", skillMd("s", "d.")),
            EntrySpec.of("scripts/../../evil.sh", "malicious"));

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("zip-slip");
  }

  // -------------------------------------------------------------------------
  // Entry count guard
  // -------------------------------------------------------------------------

  @Test
  void read_tooManyEntries_throwsInvalidSkillException() throws IOException {
    EntrySpec[] specs = new EntrySpec[SkillBundleReader.MAX_ENTRY_COUNT + 2];
    specs[0] = EntrySpec.of("SKILL.md", skillMd("s", "d."));
    for (int i = 1; i < specs.length; i++) {
      specs[i] = EntrySpec.of("file" + i + ".txt", "content");
    }
    byte[] zip = buildZip(specs);

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("entry count");
  }

  // -------------------------------------------------------------------------
  // Size guard
  // -------------------------------------------------------------------------

  @Test
  void read_totalSizeExceeded_throwsInvalidSkillException() throws IOException {
    // Create a zip with a single file that exceeds MAX_TOTAL_BYTES
    byte[] hugeContent = new byte[(int) (SkillBundleReader.MAX_TOTAL_BYTES + 1)];
    byte[] zip =
        buildZip(
            EntrySpec.of("SKILL.md", skillMd("s", "d.")), new EntrySpec("big.bin", hugeContent));

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("size");
  }

  // -------------------------------------------------------------------------
  // Name fallback
  // -------------------------------------------------------------------------

  @Test
  void read_frontmatterNameBlank_usesFallbackName() throws IOException {
    String skillMdNoName = "---\ndescription: A skill with no name.\n---\nBody.\n";
    byte[] zip = buildZip(EntrySpec.of("SKILL.md", skillMdNoName));

    Skill skill = reader.read(zip, "fallback-skill");

    assertThat(skill.name()).isEqualTo("fallback-skill");
    assertThat(skill.description()).isEqualTo("A skill with no name.");
  }

  @Test
  void read_frontmatterNameBlankNoFallback_throwsInvalidSkillException() throws IOException {
    String skillMdNoName = "---\ndescription: No name here.\n---\nBody.\n";
    byte[] zip = buildZip(EntrySpec.of("SKILL.md", skillMdNoName));

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("name");
  }

  @Test
  void read_frontmatterNamePresent_fallbackIsIgnored() throws IOException {
    byte[] zip = buildZip(EntrySpec.of("SKILL.md", skillMd("real-name", "A real skill.")));

    Skill skill = reader.read(zip, "ignored-fallback");

    assertThat(skill.name()).isEqualTo("real-name");
  }

  // -------------------------------------------------------------------------
  // Directory entries are skipped
  // -------------------------------------------------------------------------

  @Test
  void read_directoryEntriesSkipped_notInFiles() throws IOException {
    byte[] zip =
        buildZip(
            EntrySpec.of("SKILL.md", skillMd("s", "d.")),
            EntrySpec.dir("scripts/"),
            EntrySpec.of("scripts/run.sh", "echo hi"));

    Skill skill = reader.read(zip);

    assertThat(skill.files())
        .extracting(SkillFile::relativePath)
        .doesNotContain("scripts/")
        .contains("SKILL.md", "scripts/run.sh");
  }
}
