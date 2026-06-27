/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SkillResolverTest {

  private SkillResolver resolver;

  @Mock private Document docA;
  @Mock private Document docB;
  @Mock private Document docC;
  @Mock private DocumentMetadata metadataA;
  @Mock private DocumentMetadata metadataB;
  @Mock private DocumentMetadata metadataC;

  @BeforeEach
  void setUp() {
    resolver = new SkillResolver();
  }

  // -------------------------------------------------------------------------
  // Zip helpers
  // -------------------------------------------------------------------------

  private static byte[] makeSkillZip(String name, String description) throws IOException {
    String skillMd =
        "---\nname: " + name + "\ndescription: " + description + "\n---\nInstructions.\n";
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      ZipEntry entry = new ZipEntry("SKILL.md");
      byte[] content = skillMd.getBytes(StandardCharsets.UTF_8);
      entry.setSize(content.length);
      zos.putNextEntry(entry);
      zos.write(content);
      zos.closeEntry();
    }
    return baos.toByteArray();
  }

  private static byte[] makeBrokenZip() {
    return "not a zip".getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] makeSkillZipNoName(String description) throws IOException {
    String skillMd = "---\ndescription: " + description + "\n---\nInstructions.\n";
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      ZipEntry entry = new ZipEntry("SKILL.md");
      byte[] content = skillMd.getBytes(StandardCharsets.UTF_8);
      entry.setSize(content.length);
      zos.putNextEntry(entry);
      zos.write(content);
      zos.closeEntry();
    }
    return baos.toByteArray();
  }

  // -------------------------------------------------------------------------
  // Happy path: multiple docs resolve in order
  // -------------------------------------------------------------------------

  @Test
  void resolve_multipleDocuments_resolvedInOrder() throws IOException {
    when(docA.asByteArray()).thenReturn(makeSkillZip("skill-a", "Skill A."));
    when(docA.metadata()).thenReturn(metadataA);
    when(metadataA.getFileName()).thenReturn("skill-a.zip");

    when(docB.asByteArray()).thenReturn(makeSkillZip("skill-b", "Skill B."));
    when(docB.metadata()).thenReturn(metadataB);
    when(metadataB.getFileName()).thenReturn("skill-b.zip");

    List<Skill> skills = resolver.resolve(List.of(docA, docB));

    assertThat(skills).hasSize(2);
    assertThat(skills.get(0).name()).isEqualTo("skill-a");
    assertThat(skills.get(1).name()).isEqualTo("skill-b");
  }

  @Test
  void resolve_singleDocument_returnsOneSkill() throws IOException {
    when(docA.asByteArray()).thenReturn(makeSkillZip("single", "Single skill."));
    when(docA.metadata()).thenReturn(metadataA);
    when(metadataA.getFileName()).thenReturn("single.zip");

    List<Skill> skills = resolver.resolve(List.of(docA));

    assertThat(skills).hasSize(1);
    assertThat(skills.get(0).name()).isEqualTo("single");
    assertThat(skills.get(0).description()).isEqualTo("Single skill.");
  }

  // -------------------------------------------------------------------------
  // Bad bundle is skipped; others survive
  // -------------------------------------------------------------------------

  @Test
  void resolve_badBundleIsSkipped_othersSucceed() throws IOException {
    when(docA.asByteArray()).thenReturn(makeSkillZip("good-skill", "Good skill."));
    when(docA.metadata()).thenReturn(metadataA);
    when(metadataA.getFileName()).thenReturn("good-skill.zip");

    when(docB.asByteArray()).thenReturn(makeBrokenZip());
    when(docB.metadata()).thenReturn(metadataB);
    when(metadataB.getFileName()).thenReturn("broken.zip");

    when(docC.asByteArray()).thenReturn(makeSkillZip("another-good", "Another good skill."));
    when(docC.metadata()).thenReturn(metadataC);
    when(metadataC.getFileName()).thenReturn("another-good.zip");

    List<Skill> skills = resolver.resolve(List.of(docA, docB, docC));

    assertThat(skills).hasSize(2);
    assertThat(skills).extracting(Skill::name).containsExactly("good-skill", "another-good");
  }

  @Test
  void resolve_allBadBundles_returnsEmptyList() {
    when(docA.asByteArray()).thenReturn(makeBrokenZip());
    when(docA.metadata()).thenReturn(metadataA);
    when(metadataA.getFileName()).thenReturn("bad.zip");

    List<Skill> skills = resolver.resolve(List.of(docA));

    assertThat(skills).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Duplicate skill names: first wins, duplicate is skipped
  // -------------------------------------------------------------------------

  @Test
  void resolve_duplicateSkillName_firstWinsSecondSkipped() throws IOException {
    when(docA.asByteArray()).thenReturn(makeSkillZip("same-name", "First occurrence."));
    when(docA.metadata()).thenReturn(metadataA);
    when(metadataA.getFileName()).thenReturn("first.zip");

    when(docB.asByteArray()).thenReturn(makeSkillZip("same-name", "Second occurrence."));
    when(docB.metadata()).thenReturn(metadataB);
    when(metadataB.getFileName()).thenReturn("second.zip");

    List<Skill> skills = resolver.resolve(List.of(docA, docB));

    assertThat(skills).hasSize(1);
    assertThat(skills.get(0).description()).isEqualTo("First occurrence.");
  }

  // -------------------------------------------------------------------------
  // Empty / null input
  // -------------------------------------------------------------------------

  @Test
  void resolve_emptyList_returnsEmptyList() {
    assertThat(resolver.resolve(List.of())).isEmpty();
  }

  @Test
  void resolve_nullInput_returnsEmptyList() {
    assertThat(resolver.resolve(null)).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Fallback name from document metadata
  // -------------------------------------------------------------------------

  @Test
  void resolve_noFrontmatterName_usesDocumentFileNameAsName() throws IOException {
    when(docA.asByteArray()).thenReturn(makeSkillZipNoName("Skill with no name in frontmatter."));
    when(docA.metadata()).thenReturn(metadataA);
    when(metadataA.getFileName()).thenReturn("my-fallback.zip");

    List<Skill> skills = resolver.resolve(List.of(docA));

    assertThat(skills).hasSize(1);
    assertThat(skills.get(0).name()).isEqualTo("my-fallback");
  }

  @Test
  void resolve_noFrontmatterNameNoMetadata_skillIsSkipped() throws IOException {
    when(docA.asByteArray()).thenReturn(makeSkillZipNoName("No name anywhere."));
    when(docA.metadata()).thenReturn(metadataA);
    when(metadataA.getFileName()).thenReturn(null);
    when(docA.reference()).thenReturn(null);

    List<Skill> skills = resolver.resolve(List.of(docA));

    // No fallback available → skill is skipped (InvalidSkillException from reader)
    assertThat(skills).isEmpty();
  }

  // -------------------------------------------------------------------------
  // Result is unmodifiable
  // -------------------------------------------------------------------------

  @Test
  void resolve_returnedListIsUnmodifiable() throws IOException {
    when(docA.asByteArray()).thenReturn(makeSkillZip("s", "d."));
    when(docA.metadata()).thenReturn(metadataA);
    when(metadataA.getFileName()).thenReturn("s.zip");

    List<Skill> skills = resolver.resolve(List.of(docA));

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> skills.add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
