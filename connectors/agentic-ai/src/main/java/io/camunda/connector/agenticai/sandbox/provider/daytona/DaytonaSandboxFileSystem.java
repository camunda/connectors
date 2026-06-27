/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.provider.daytona;

import io.camunda.connector.agenticai.sandbox.spi.FileEntry;
import io.camunda.connector.agenticai.sandbox.spi.FileInfo;
import io.camunda.connector.agenticai.sandbox.spi.Match;
import io.camunda.connector.agenticai.sandbox.spi.SandboxException;
import io.camunda.connector.agenticai.sandbox.spi.SandboxFileSystem;
import io.daytona.sdk.FileSystem;
import io.daytona.sdk.exception.DaytonaException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * {@link SandboxFileSystem} implementation backed by the Daytona toolbox {@link FileSystem} API.
 *
 * <p><strong>Content-type / isBinary heuristic:</strong> The Daytona toolbox API does not return a
 * MIME content type for files. Both {@code contentType} and {@code isBinary} are derived from the
 * file extension using a simple lookup table. This is intentionally best-effort.
 *
 * <p><strong>delete recursion:</strong> The {@link FileSystem#deleteFile(String)} API does not
 * expose a separate {@code recursive} flag at the HTTP level. The {@code recursive} parameter is
 * accepted by the SPI but is not separately honored here — the caller is expected to pass a path
 * that Daytona can handle (e.g., a directory path for recursive deletion if the server supports
 * it).
 */
class DaytonaSandboxFileSystem implements SandboxFileSystem {

  private final FileSystem fs;

  DaytonaSandboxFileSystem(FileSystem fs) {
    this.fs = fs;
  }

  @Override
  public byte[] read(String path) {
    try {
      return fs.downloadFile(path);
    } catch (DaytonaException e) {
      throw new SandboxException("Failed to read file '" + path + "': " + e.getMessage(), e);
    }
  }

  @Override
  public FileInfo stat(String path) {
    try {
      io.daytona.sdk.model.FileInfo fi = fs.getFileDetails(path);
      return mapFileInfo(path, fi);
    } catch (DaytonaException e) {
      throw new SandboxException("Failed to stat file '" + path + "': " + e.getMessage(), e);
    }
  }

  @Override
  public void write(String path, byte[] content) {
    // Ensure the parent directory exists. createFolder (os.MkdirAll server-side) is idempotent for
    // existing directories, so a failure here is usually benign (already exists). We do NOT throw
    // immediately: instead we keep the cause and only surface it if the upload also fails — the
    // upload outcome is the real signal, and the create error explains an otherwise-opaque 400
    // (e.g. parent could not be created because the base directory is not writable).
    String parent = parentDir(path);
    DaytonaException createFolderError = null;
    if (parent != null && !parent.isEmpty()) {
      try {
        fs.createFolder(parent, "755");
      } catch (DaytonaException e) {
        createFolderError = e;
      }
    }
    try {
      fs.uploadFile(content, path);
    } catch (DaytonaException e) {
      String message = "Failed to write file '" + path + "': " + e.getMessage();
      if (createFolderError != null) {
        message +=
            " (creating parent directory '"
                + parent
                + "' also failed: "
                + createFolderError.getMessage()
                + ")";
      }
      throw new SandboxException(message, e);
    }
  }

  @Override
  public void writeBatch(List<FileEntry> entries) {
    for (FileEntry entry : entries) {
      write(entry.path(), entry.content());
    }
  }

  @Override
  public List<FileInfo> list(String path) {
    try {
      List<io.daytona.sdk.model.FileInfo> raw = fs.listFiles(path);
      List<FileInfo> result = new ArrayList<>(raw.size());
      for (io.daytona.sdk.model.FileInfo fi : raw) {
        // Daytona returns the file name (not the full path) in getName(); reconstruct the full
        // path by joining the requested directory with the returned name.
        String name = fi.getName();
        String fullPath = name != null && name.startsWith("/") ? name : joinPath(path, name);
        result.add(mapFileInfo(fullPath, fi));
      }
      return result;
    } catch (DaytonaException e) {
      throw new SandboxException("Failed to list directory '" + path + "': " + e.getMessage(), e);
    }
  }

  @Override
  public void delete(String path, boolean recursive) {
    // The Daytona toolbox deleteFile endpoint does not expose a separate recursive flag at the
    // API level. Pass the path directly; server-side behaviour for directories depends on the
    // Daytona deployment configuration.
    try {
      fs.deleteFile(path);
    } catch (DaytonaException e) {
      throw new SandboxException("Failed to delete '" + path + "': " + e.getMessage(), e);
    }
  }

  @Override
  public List<Match> search(String dir, String pattern) {
    try {
      List<Map<String, Object>> raw = fs.findFiles(dir, pattern);
      List<Match> results = new ArrayList<>();
      for (Map<String, Object> entry : raw) {
        String filePath = getString(entry, "file", "path", "name");
        int line = getInt(entry, "line", "lineNumber", "line_number");
        String text = getString(entry, "line_content", "content", "text", "match");
        if (filePath == null) {
          filePath = dir;
        }
        if (text == null) {
          text = "";
        }
        results.add(new Match(filePath, line, text));
      }
      return results;
    } catch (DaytonaException e) {
      throw new SandboxException(
          "Failed to search in '" + dir + "' for pattern '" + pattern + "': " + e.getMessage(), e);
    }
  }

  // ---------------------------------------------------------------------------
  // Mapping helpers
  // ---------------------------------------------------------------------------

  private static FileInfo mapFileInfo(String path, io.daytona.sdk.model.FileInfo fi) {
    // getSize() returns Integer (may be null for directories or on error)
    Integer rawSize = fi.getSize();
    long size = rawSize != null ? rawSize.longValue() : 0L;
    boolean isDir = Boolean.TRUE.equals(fi.getIsDir());
    // Content-type and isBinary are derived heuristically from the file extension since the
    // Daytona toolbox API does not return MIME types.
    boolean binary = !isDir && isBinaryByExtension(path);
    String contentType = isDir ? null : detectContentType(path, binary);
    return new FileInfo(path, size, contentType, binary);
  }

  /**
   * Heuristic: returns {@code true} for file extensions that are not known plain-text formats. This
   * is intentionally conservative — false positives (calling a text file binary) are preferable to
   * false negatives when the agent tries to display binary data as text.
   */
  static boolean isBinaryByExtension(String path) {
    String ext = extension(path);
    if (ext == null) {
      return false;
    }
    return switch (ext) {
      case "txt",
          "md",
          "json",
          "csv",
          "log",
          "xml",
          "yaml",
          "yml",
          "sh",
          "py",
          "js",
          "ts",
          "html",
          "htm",
          "css",
          "java",
          "kt",
          "go",
          "rs",
          "c",
          "cpp",
          "h",
          "hpp",
          "rb",
          "php",
          "sql",
          "toml",
          "ini",
          "cfg",
          "conf",
          "env",
          "properties",
          "gradle",
          "pom",
          "tf",
          "hcl",
          "dockerfile",
          "makefile",
          "r",
          "scala",
          "swift",
          "dart" ->
          false;
      default -> true;
    };
  }

  /**
   * Maps common file extensions to MIME types. Returns {@code application/octet-stream} for unknown
   * binary extensions and {@code text/plain} for unknown text-like extensions.
   */
  static String detectContentType(String path, boolean binary) {
    String ext = extension(path);
    if (ext != null) {
      return switch (ext) {
        case "pdf" -> "application/pdf";
        case "txt" -> "text/plain";
        case "json" -> "application/json";
        case "md" -> "text/markdown";
        case "html", "htm" -> "text/html";
        case "xml" -> "application/xml";
        case "csv" -> "text/csv";
        case "yaml", "yml" -> "application/x-yaml";
        case "zip" -> "application/zip";
        case "tar" -> "application/x-tar";
        case "gz" -> "application/gzip";
        case "png" -> "image/png";
        case "jpg", "jpeg" -> "image/jpeg";
        case "gif" -> "image/gif";
        case "svg" -> "image/svg+xml";
        case "sh", "bash" -> "application/x-sh";
        case "py" -> "text/x-python";
        case "js" -> "text/javascript";
        case "ts" -> "text/typescript";
        case "java" -> "text/x-java-source";
        case "css" -> "text/css";
        default -> binary ? "application/octet-stream" : "text/plain";
      };
    }
    return binary ? "application/octet-stream" : "text/plain";
  }

  @Nullable
  private static String extension(String path) {
    if (path == null || path.isEmpty()) {
      return null;
    }
    // Extract just the filename portion to avoid mismatching on directory components
    int lastSlash = path.lastIndexOf('/');
    String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    int dot = name.lastIndexOf('.');
    if (dot >= 0 && dot < name.length() - 1) {
      return name.substring(dot + 1).toLowerCase();
    }
    return null;
  }

  @Nullable
  private static String parentDir(String path) {
    if (path == null || path.isEmpty()) {
      return null;
    }
    int lastSlash = path.lastIndexOf('/');
    if (lastSlash <= 0) {
      return lastSlash == 0 ? "/" : null;
    }
    return path.substring(0, lastSlash);
  }

  private static String joinPath(String dir, @Nullable String name) {
    if (name == null || name.isEmpty()) {
      return dir;
    }
    if (dir.endsWith("/")) {
      return dir + name;
    }
    return dir + "/" + name;
  }

  // ---------------------------------------------------------------------------
  // findFiles response map helpers
  // ---------------------------------------------------------------------------

  @Nullable
  private static String getString(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object val = map.get(key);
      if (val instanceof String s) {
        return s;
      }
    }
    return null;
  }

  private static int getInt(Map<String, Object> map, String... keys) {
    for (String key : keys) {
      Object val = map.get(key);
      if (val instanceof Number n) {
        return n.intValue();
      }
    }
    return 0;
  }
}
