/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.fake;

import io.camunda.connector.agenticai.sandbox.spi.FileEntry;
import io.camunda.connector.agenticai.sandbox.spi.FileInfo;
import io.camunda.connector.agenticai.sandbox.spi.Match;
import io.camunda.connector.agenticai.sandbox.spi.SandboxException;
import io.camunda.connector.agenticai.sandbox.spi.SandboxFileSystem;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class InMemorySandboxFileSystem implements SandboxFileSystem {

  private final Map<String, byte[]> files;

  InMemorySandboxFileSystem(Map<String, byte[]> files) {
    this.files = files;
  }

  @Override
  public byte[] read(String path) {
    byte[] content = files.get(path);
    if (content == null) {
      throw new SandboxException("File not found: " + path);
    }
    return content.clone();
  }

  @Override
  public FileInfo stat(String path) {
    byte[] content = files.get(path);
    if (content == null) {
      throw new SandboxException("File not found: " + path);
    }
    boolean binary = isBinary(content);
    String contentType = detectContentType(path, binary);
    return new FileInfo(path, content.length, contentType, binary);
  }

  @Override
  public void write(String path, byte[] content) {
    files.put(path, content.clone());
  }

  @Override
  public void writeBatch(List<FileEntry> entries) {
    for (FileEntry entry : entries) {
      write(entry.path(), entry.content());
    }
  }

  @Override
  public List<FileInfo> list(String dir) {
    String prefix = normalizeDir(dir);
    return files.entrySet().stream()
        .filter(e -> prefix.isEmpty() || e.getKey().startsWith(prefix))
        .map(
            e -> {
              boolean binary = isBinary(e.getValue());
              return new FileInfo(
                  e.getKey(), e.getValue().length, detectContentType(e.getKey(), binary), binary);
            })
        .sorted((a, b) -> a.path().compareTo(b.path()))
        .collect(Collectors.toList());
  }

  @Override
  public void delete(String path, boolean recursive) {
    if (recursive) {
      String prefix = path.endsWith("/") ? path : path + "/";
      files.keySet().removeIf(k -> k.equals(path) || k.startsWith(prefix));
    } else {
      files.remove(path);
    }
  }

  @Override
  public List<Match> search(String dir, String pattern) {
    String prefix = normalizeDir(dir);
    Pattern regex = Pattern.compile(pattern);
    List<Match> results = new ArrayList<>();

    files.entrySet().stream()
        .filter(e -> prefix.isEmpty() || e.getKey().startsWith(prefix))
        .filter(e -> !isBinary(e.getValue()))
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            e -> {
              String text = new String(e.getValue(), StandardCharsets.UTF_8);
              String[] lines = text.split("\n", -1);
              for (int i = 0; i < lines.length; i++) {
                if (regex.matcher(lines[i]).find()) {
                  results.add(new Match(e.getKey(), i + 1, lines[i]));
                }
              }
            });

    return results;
  }

  private static String normalizeDir(String dir) {
    if (dir == null || dir.isEmpty() || dir.equals("/")) {
      return "";
    }
    return dir.endsWith("/") ? dir : dir + "/";
  }

  static boolean isBinary(byte[] content) {
    for (byte b : content) {
      if (b == 0) {
        return true;
      }
    }
    // Also check for invalid UTF-8
    try {
      new String(content, StandardCharsets.UTF_8).getBytes(StandardCharsets.UTF_8);
      // Simple check: if round-trip differs length, it's not clean UTF-8
      return false;
    } catch (Exception e) {
      return true;
    }
  }

  static String detectContentType(String path, boolean binary) {
    int dot = path.lastIndexOf('.');
    if (dot >= 0) {
      String ext = path.substring(dot + 1).toLowerCase();
      return switch (ext) {
        case "pdf" -> "application/pdf";
        case "txt" -> "text/plain";
        case "json" -> "application/json";
        case "md" -> "text/markdown";
        case "html", "htm" -> "text/html";
        case "xml" -> "application/xml";
        case "csv" -> "text/csv";
        case "zip" -> "application/zip";
        case "png" -> "image/png";
        case "jpg", "jpeg" -> "image/jpeg";
        default -> binary ? "application/octet-stream" : "text/plain";
      };
    }
    return binary ? "application/octet-stream" : "text/plain";
  }
}
