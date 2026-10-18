/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.skill;

import io.camunda.connector.agenticai.sandbox.skill.Skill.SkillFile;
import io.camunda.connector.agenticai.sandbox.skill.SkillMdParser.ParsedSkillMd;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jspecify.annotations.Nullable;

/**
 * Reads a skill bundle (a {@code .zip}) from raw bytes and produces a {@link Skill}.
 *
 * <h2>Zip layout</h2>
 *
 * <p>The zip may either have files at the root level, or may have a single common top-level
 * directory (e.g. {@code my-skill/...}). In the latter case the common prefix is stripped so that
 * all {@link SkillFile} paths are root-relative (e.g. {@code SKILL.md}, {@code scripts/run.sh}).
 *
 * <h2>Security guards</h2>
 *
 * <ul>
 *   <li><b>Zip-slip:</b> any entry whose normalized path contains {@code ..} segments or is
 *       absolute will cause an {@link InvalidSkillException}.
 *   <li><b>Entry count:</b> bundles with more than {@link #MAX_ENTRY_COUNT} file entries (after
 *       skipping directories) are rejected.
 *   <li><b>Total uncompressed size:</b> bundles whose aggregate uncompressed size exceeds {@link
 *       #MAX_TOTAL_BYTES} are rejected.
 * </ul>
 *
 * <h2>SKILL.md requirement</h2>
 *
 * <p>A {@code SKILL.md} file (case-sensitive) must be present at the bundle root. If it is absent
 * or its description is blank, {@link InvalidSkillException} is thrown.
 */
public class SkillBundleReader {

  /** Maximum number of file entries allowed in a skill bundle (zip-bomb guard). */
  public static final int MAX_ENTRY_COUNT = 1_000;

  /** Maximum aggregate uncompressed size in bytes (zip-bomb guard): 25 MB. */
  public static final long MAX_TOTAL_BYTES = 25L * 1024 * 1024;

  private static final String SKILL_MD = "SKILL.md";

  private final SkillMdParser parser;

  public SkillBundleReader() {
    this(new SkillMdParser());
  }

  public SkillBundleReader(SkillMdParser parser) {
    this.parser = parser;
  }

  /**
   * Reads the skill bundle zip and constructs a {@link Skill}. When the {@code SKILL.md}
   * frontmatter does not contain a {@code name}, an {@link InvalidSkillException} is thrown (use
   * {@link #read(byte[], String)} to supply a fallback name).
   *
   * @param zipBytes raw zip bytes
   * @return the parsed skill
   * @throws InvalidSkillException if the bundle is invalid, missing {@code SKILL.md}, or
   *     description is blank
   */
  public Skill read(byte[] zipBytes) {
    return read(zipBytes, null);
  }

  /**
   * Reads the skill bundle zip and constructs a {@link Skill}.
   *
   * <p>If the {@code SKILL.md} frontmatter has a blank {@code name}, {@code fallbackName} is used
   * instead. If {@code fallbackName} is also blank, an {@link InvalidSkillException} is thrown.
   *
   * @param zipBytes raw zip bytes
   * @param fallbackName optional fallback name (e.g. the document file name without extension)
   * @return the parsed skill
   * @throws InvalidSkillException if the bundle is invalid, missing {@code SKILL.md}, description
   *     is blank, or no name could be determined
   */
  public Skill read(byte[] zipBytes, @Nullable String fallbackName) {
    List<RawEntry> rawEntries = extractZip(zipBytes);

    // Detect and strip a single common top-level directory
    String commonPrefix = detectCommonPrefix(rawEntries);
    List<SkillFile> files = new ArrayList<>(rawEntries.size());
    for (RawEntry entry : rawEntries) {
      String relativePath =
          commonPrefix != null ? entry.path().substring(commonPrefix.length()) : entry.path();
      files.add(new SkillFile(relativePath, entry.content()));
    }

    // Locate SKILL.md
    byte[] skillMdBytes = null;
    for (SkillFile file : files) {
      if (SKILL_MD.equals(file.relativePath())) {
        skillMdBytes = file.content();
        break;
      }
    }
    if (skillMdBytes == null) {
      throw new InvalidSkillException(
          "Skill bundle does not contain a 'SKILL.md' file at the bundle root");
    }

    String skillMdText = new String(skillMdBytes, StandardCharsets.UTF_8);
    ParsedSkillMd parsed = parser.parse(skillMdText);

    // Resolve name: frontmatter → fallback → error
    String name = parsed.name();
    if (name == null || name.isBlank()) {
      if (fallbackName != null && !fallbackName.isBlank()) {
        name = fallbackName;
      } else {
        throw new InvalidSkillException(
            "Skill bundle SKILL.md has no 'name' in frontmatter and no fallback name was provided");
      }
    }

    return new Skill(name, parsed.description(), parsed.body(), List.copyOf(files));
  }

  // -------------------------------------------------------------------------
  // Private helpers
  // -------------------------------------------------------------------------

  /** Extracts all file entries (skipping directories) from the zip, applying security guards. */
  private static List<RawEntry> extractZip(byte[] zipBytes) {
    List<RawEntry> entries = new ArrayList<>();
    long totalBytes = 0L;
    int fileCount = 0;

    try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          zis.closeEntry();
          continue;
        }

        String rawName = entry.getName();
        String normalized = normalizePath(rawName);

        if (isZipSlip(normalized)) {
          throw new InvalidSkillException(
              "Skill bundle contains a zip-slip entry: '" + rawName + "'");
        }

        if (++fileCount > MAX_ENTRY_COUNT) {
          throw new InvalidSkillException(
              "Skill bundle exceeds maximum entry count of " + MAX_ENTRY_COUNT);
        }

        byte[] content = zis.readAllBytes();
        totalBytes += content.length;
        if (totalBytes > MAX_TOTAL_BYTES) {
          throw new InvalidSkillException(
              "Skill bundle exceeds maximum total uncompressed size of "
                  + (MAX_TOTAL_BYTES / (1024 * 1024))
                  + " MB");
        }

        entries.add(new RawEntry(normalized, content));
        zis.closeEntry();
      }
    } catch (InvalidSkillException e) {
      throw e;
    } catch (IOException e) {
      throw new InvalidSkillException("Failed to read skill bundle zip: " + e.getMessage(), e);
    }

    if (entries.isEmpty()) {
      throw new InvalidSkillException("Skill bundle zip contains no files");
    }

    return entries;
  }

  /**
   * Normalizes a zip entry path: replace backslashes with forward slashes, collapse repeated
   * slashes, and strip leading slashes.
   */
  private static String normalizePath(String rawName) {
    String normalized = rawName.replace('\\', '/');
    // Collapse repeated slashes
    while (normalized.contains("//")) {
      normalized = normalized.replace("//", "/");
    }
    // Strip leading slash
    if (normalized.startsWith("/")) {
      normalized = normalized.substring(1);
    }
    return normalized;
  }

  /**
   * Returns {@code true} if the normalized path is absolute or contains {@code ..} segments that
   * could escape the bundle root (zip-slip check).
   */
  private static boolean isZipSlip(String normalized) {
    if (normalized.startsWith("/")) {
      return true;
    }
    // Check for .. segments
    for (String segment : normalized.split("/", -1)) {
      if ("..".equals(segment)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Detects whether all entries share a single top-level directory. Returns the common prefix
   * (including the trailing {@code /}) if so, or {@code null} if files are already at the root.
   */
  @Nullable
  private static String detectCommonPrefix(List<RawEntry> entries) {
    if (entries.isEmpty()) {
      return null;
    }
    String first = entries.get(0).path();
    int slashIdx = first.indexOf('/');
    if (slashIdx <= 0) {
      // First entry has no directory component — files are at root
      return null;
    }
    String prefix = first.substring(0, slashIdx + 1); // e.g. "my-skill/"
    for (RawEntry entry : entries) {
      if (!entry.path().startsWith(prefix)) {
        return null;
      }
    }
    return prefix;
  }

  private record RawEntry(String path, byte[] content) {}
}
