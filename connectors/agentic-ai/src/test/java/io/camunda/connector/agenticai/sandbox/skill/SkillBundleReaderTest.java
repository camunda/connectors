/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
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
  // Helpers
  // -------------------------------------------------------------------------

  private static byte[] buildZip(String... pathsAndContents) throws Exception {
    if (pathsAndContents.length % 2 != 0) {
      throw new IllegalArgumentException("pathsAndContents must be pairs of path, content");
    }
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      for (int i = 0; i < pathsAndContents.length; i += 2) {
        String path = pathsAndContents[i];
        byte[] content = pathsAndContents[i + 1].getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry(path);
        entry.setSize(content.length);
        zos.putNextEntry(entry);
        zos.write(content);
        zos.closeEntry();
      }
      zos.finish();
    }
    return baos.toByteArray();
  }

  private static String skillMd(String name, String description) {
    return "---\nname: " + name + "\ndescription: " + description + "\n---\nInstructions here.\n";
  }

  // -------------------------------------------------------------------------
  // Happy path — flat zip (files at root)
  // -------------------------------------------------------------------------

  @Test
  void read_flatZipWithSkillMd_returnsSkill() throws Exception {
    byte[] zip = buildZip("SKILL.md", skillMd("my-skill", "A useful skill."));

    Skill skill = reader.read(zip);

    assertThat(skill.name()).isEqualTo("my-skill");
    assertThat(skill.description()).isEqualTo("A useful skill.");
    assertThat(skill.skillMdBody()).contains("Instructions here.");
    assertThat(skill.files()).hasSize(1);
    assertThat(skill.files().get(0).relativePath()).isEqualTo("SKILL.md");
  }

  @Test
  void read_flatZipWithMultipleFiles_allFilesPresent() throws Exception {
    byte[] zip =
        buildZip(
            "SKILL.md",
            skillMd("my-skill", "A skill with extras."),
            "scripts/run.sh",
            "#!/bin/bash\necho hello",
            "data/config.json",
            "{\"key\": \"value\"}");

    Skill skill = reader.read(zip);

    assertThat(skill.files()).hasSize(3);
    assertThat(skill.resourceFileNames())
        .containsExactlyInAnyOrder("scripts/run.sh", "data/config.json");
  }

  // -------------------------------------------------------------------------
  // Happy path — zip with single top-level directory (common prefix stripped)
  // -------------------------------------------------------------------------

  @Test
  void read_zipWithTopLevelDirectory_prefixStripped() throws Exception {
    byte[] zip =
        buildZip(
            "my-skill/SKILL.md",
            skillMd("my-skill", "Desc."),
            "my-skill/scripts/run.sh",
            "echo hi");

    Skill skill = reader.read(zip);

    assertThat(skill.name()).isEqualTo("my-skill");
    assertThat(skill.files())
        .extracting(Skill.SkillFile::relativePath)
        .containsExactlyInAnyOrder("SKILL.md", "scripts/run.sh");
  }

  // -------------------------------------------------------------------------
  // Fallback name
  // -------------------------------------------------------------------------

  @Test
  void read_withFallbackName_usedWhenFrontmatterNameAbsent() throws Exception {
    String md = "---\ndescription: A skill.\n---\nBody.\n";
    byte[] zip = buildZip("SKILL.md", md);

    Skill skill = reader.read(zip, "fallback-skill");

    assertThat(skill.name()).isEqualTo("fallback-skill");
  }

  @Test
  void read_withoutFallbackName_throwsWhenFrontmatterNameAbsent() throws Exception {
    String md = "---\ndescription: A skill.\n---\nBody.\n";
    byte[] zip = buildZip("SKILL.md", md);

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("name");
  }

  // -------------------------------------------------------------------------
  // Error cases
  // -------------------------------------------------------------------------

  @Test
  void read_missingSkillMd_throwsInvalidSkillException() throws Exception {
    byte[] zip = buildZip("scripts/run.sh", "echo hello");

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("SKILL.md");
  }

  @Test
  void read_emptyZip_throwsInvalidSkillException() throws Exception {
    // Build a zip with only directory entries
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      zos.finish();
    }
    byte[] zip = baos.toByteArray();

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("no files");
  }

  @Test
  void read_invalidZipBytes_throwsInvalidSkillException() {
    byte[] notAZip = "not a zip file".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(() -> reader.read(notAZip)).isInstanceOf(InvalidSkillException.class);
  }

  @Test
  void read_zipSlipEntry_throwsInvalidSkillException() throws Exception {
    // Manually add a zip-slip entry
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      // Add SKILL.md first
      byte[] skillMdBytes = skillMd("my-skill", "Desc.").getBytes(StandardCharsets.UTF_8);
      ZipEntry skillMdEntry = new ZipEntry("SKILL.md");
      skillMdEntry.setSize(skillMdBytes.length);
      zos.putNextEntry(skillMdEntry);
      zos.write(skillMdBytes);
      zos.closeEntry();

      // Add zip-slip entry
      byte[] evilBytes = "evil".getBytes(StandardCharsets.UTF_8);
      ZipEntry evilEntry = new ZipEntry("../../../etc/passwd");
      evilEntry.setSize(evilBytes.length);
      zos.putNextEntry(evilEntry);
      zos.write(evilBytes);
      zos.closeEntry();

      zos.finish();
    }
    byte[] zip = baos.toByteArray();

    assertThatThrownBy(() -> reader.read(zip))
        .isInstanceOf(InvalidSkillException.class)
        .hasMessageContaining("zip-slip");
  }
}
