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

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SkillResolverTest {

  private SkillResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new SkillResolver();
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static byte[] buildZip(String skillName, String description) throws Exception {
    String skillMd =
        "---\nname: " + skillName + "\ndescription: " + description + "\n---\nInstructions.\n";
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      byte[] content = skillMd.getBytes(StandardCharsets.UTF_8);
      ZipEntry entry = new ZipEntry("SKILL.md");
      entry.setSize(content.length);
      zos.putNextEntry(entry);
      zos.write(content);
      zos.closeEntry();
      zos.finish();
    }
    return baos.toByteArray();
  }

  private static Document mockDocument(byte[] zipBytes, String fileName) {
    Document doc = mock(Document.class);
    DocumentMetadata meta = mock(DocumentMetadata.class);
    when(doc.asByteArray()).thenReturn(zipBytes);
    when(doc.metadata()).thenReturn(meta);
    when(meta.getFileName()).thenReturn(fileName);
    return doc;
  }

  // -------------------------------------------------------------------------
  // Happy path
  // -------------------------------------------------------------------------

  @Test
  void resolve_singleDocument_returnsSkill() throws Exception {
    byte[] zip = buildZip("my-skill", "A useful skill.");
    Document doc = mockDocument(zip, "my-skill.zip");

    List<Skill> result = resolver.resolve(List.of(doc));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("my-skill");
    assertThat(result.get(0).description()).isEqualTo("A useful skill.");
  }

  @Test
  void resolve_multipleDocuments_returnsAllSkillsInOrder() throws Exception {
    byte[] zip1 = buildZip("skill-a", "Skill A.");
    byte[] zip2 = buildZip("skill-b", "Skill B.");
    Document doc1 = mockDocument(zip1, "skill-a.zip");
    Document doc2 = mockDocument(zip2, "skill-b.zip");

    List<Skill> result = resolver.resolve(List.of(doc1, doc2));

    assertThat(result).hasSize(2);
    assertThat(result.get(0).name()).isEqualTo("skill-a");
    assertThat(result.get(1).name()).isEqualTo("skill-b");
  }

  @Test
  void resolve_nullList_returnsEmptyList() {
    List<Skill> result = resolver.resolve(null);

    assertThat(result).isEmpty();
  }

  @Test
  void resolve_emptyList_returnsEmptyList() {
    List<Skill> result = resolver.resolve(List.of());

    assertThat(result).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Error handling — invalid document skipped, others unaffected
  // -------------------------------------------------------------------------

  @Test
  void resolve_invalidDocument_skippedOtherSkillsReturned() throws Exception {
    Document badDoc = mock(Document.class);
    when(badDoc.asByteArray()).thenReturn("not-a-zip".getBytes(StandardCharsets.UTF_8));
    when(badDoc.metadata()).thenReturn(null);

    byte[] goodZip = buildZip("good-skill", "The good one.");
    Document goodDoc = mockDocument(goodZip, "good-skill.zip");

    List<Skill> result = resolver.resolve(List.of(badDoc, goodDoc));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("good-skill");
  }

  @Test
  void resolve_documentThrowsOnRead_skipped() {
    Document errorDoc = mock(Document.class);
    when(errorDoc.asByteArray()).thenThrow(new RuntimeException("Storage unavailable"));
    when(errorDoc.metadata()).thenReturn(null);

    List<Skill> result = resolver.resolve(List.of(errorDoc));

    assertThat(result).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Duplicate name handling
  // -------------------------------------------------------------------------

  @Test
  void resolve_duplicateSkillName_firstWinsSecondSkipped() throws Exception {
    byte[] zip1 = buildZip("my-skill", "First version.");
    byte[] zip2 = buildZip("my-skill", "Second version (duplicate).");
    Document doc1 = mockDocument(zip1, "my-skill-v1.zip");
    Document doc2 = mockDocument(zip2, "my-skill-v2.zip");

    List<Skill> result = resolver.resolve(List.of(doc1, doc2));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).description()).isEqualTo("First version.");
  }

  // -------------------------------------------------------------------------
  // Fallback name from file name
  // -------------------------------------------------------------------------

  @Test
  void resolve_frontmatterNameAbsent_fallbackFromFileName() throws Exception {
    // Build a zip with SKILL.md that has no 'name' field
    String skillMd = "---\ndescription: Uses fallback.\n---\nBody.\n";
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      byte[] content = skillMd.getBytes(StandardCharsets.UTF_8);
      ZipEntry entry = new ZipEntry("SKILL.md");
      entry.setSize(content.length);
      zos.putNextEntry(entry);
      zos.write(content);
      zos.closeEntry();
      zos.finish();
    }
    byte[] zip = baos.toByteArray();
    Document doc = mockDocument(zip, "fallback-skill.zip");

    List<Skill> result = resolver.resolve(List.of(doc));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name()).isEqualTo("fallback-skill");
  }
}
