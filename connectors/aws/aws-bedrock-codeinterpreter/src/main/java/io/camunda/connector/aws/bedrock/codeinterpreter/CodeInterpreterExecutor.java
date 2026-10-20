/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.aws.bedrock.codeinterpreter;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterInput;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.CodeInterpreterRequest;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.request.FileUpload;
import io.camunda.connector.aws.bedrock.codeinterpreter.model.response.CodeInterpreterResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreAsyncClient;
import software.amazon.awssdk.services.bedrockagentcore.BedrockAgentCoreClient;
import software.amazon.awssdk.services.bedrockagentcore.model.BedrockAgentCoreException;
import software.amazon.awssdk.services.bedrockagentcore.model.CodeInterpreterResult;
import software.amazon.awssdk.services.bedrockagentcore.model.ContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InputContentBlock;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.InvokeCodeInterpreterResponseHandler;
import software.amazon.awssdk.services.bedrockagentcore.model.StartCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.StopCodeInterpreterSessionRequest;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolArguments;
import software.amazon.awssdk.services.bedrockagentcore.model.ToolName;

public class CodeInterpreterExecutor {

  private static final Logger LOG = LoggerFactory.getLogger(CodeInterpreterExecutor.class);
  private static final String DEFAULT_CODE_INTERPRETER_ID = "aws.codeinterpreter.v1";
  private static final String SESSION_NAME_PREFIX = "camunda-";
  private static final String ERROR_CODE_INTERPRETER_FAILED = "CODE_INTERPRETER_FAILED";
  private static final String ERROR_CODE_DOCUMENT_UPLOAD_FAILED = "DOCUMENT_UPLOAD_FAILED";
  private static final String DEFAULT_MIME_TYPE = "application/octet-stream";
  private static final String FALLBACK_DOCUMENT_NAME_PREFIX = "document-";
  static final String UPLOAD_DIRECTORY = "uploads";
  // Blobs travel base64-encoded inside a single synchronous tool call, so the raw payload is kept
  // well below typical request size limits instead of relying on the service to reject it.
  static final long MAX_TOTAL_UPLOAD_SIZE = 8L * 1024 * 1024;
  private static final Set<String> TEXT_APPLICATION_TYPES =
      Set.of(
          "application/json",
          "application/xml",
          "application/yaml",
          "application/x-yaml",
          "application/csv",
          "application/javascript",
          "application/sql");
  private static final int DEFAULT_MAX_FILES = 10;
  private static final long DEFAULT_MAX_TOTAL_FILE_SIZE = 10L * 1024 * 1024;
  private static final Duration DEFAULT_SESSION_TIMEOUT = Duration.ofMinutes(5);

  private final BedrockAgentCoreClient syncClient;
  private final BedrockAgentCoreAsyncClient asyncClient;
  private final Function<DocumentCreationRequest, Document> createDocument;

  public CodeInterpreterExecutor(
      BedrockAgentCoreClient syncClient,
      BedrockAgentCoreAsyncClient asyncClient,
      Function<DocumentCreationRequest, Document> createDocument) {
    this.syncClient = syncClient;
    this.asyncClient = asyncClient;
    this.createDocument = createDocument;
  }

  public CodeInterpreterResponse execute(CodeInterpreterRequest request, long elementInstanceKey) {
    var input = request.getInput();
    if (input == null || input.getCode() == null || input.getCode().isBlank()) {
      throw new ConnectorException(ERROR_CODE_INTERPRETER_FAILED, "Code must not be empty");
    }
    try (var session = startSession(input, elementInstanceKey)) {
      var uploadedFiles = uploadFiles(session, input.getFiles());
      return invokeCode(session, input, uploadedFiles);
    } catch (BedrockAgentCoreException e) {
      var msg = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED, "Code Interpreter error: " + msg, e);
    } catch (ConnectorException e) {
      throw e;
    } catch (Exception e) {
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED, "Code Interpreter error: " + e.getMessage(), e);
    }
  }

  // --- Session management ---

  private CodeInterpreterSession startSession(CodeInterpreterInput input, long elementInstanceKey) {
    var ciId = resolveCodeInterpreterIdentifier(input);
    var timeout =
        input.getSessionTimeout() != null ? input.getSessionTimeout() : DEFAULT_SESSION_TIMEOUT;

    var builder =
        StartCodeInterpreterSessionRequest.builder()
            .codeInterpreterIdentifier(ciId)
            .name(SESSION_NAME_PREFIX + elementInstanceKey)
            .sessionTimeoutSeconds((int) timeout.getSeconds());

    var sessionId = syncClient.startCodeInterpreterSession(builder.build()).sessionId();
    return new CodeInterpreterSession(sessionId, ciId, timeout);
  }

  // --- Document upload ---

  /**
   * Writes all documents into the {@value #UPLOAD_DIRECTORY} directory of the session workspace
   * with a single {@code writeFiles} call and returns the paths they were stored under, in document
   * order.
   */
  private List<String> uploadFiles(CodeInterpreterSession session, List<FileUpload> files) {
    if (files == null || files.isEmpty()) {
      return List.of();
    }

    var usedNames = new HashSet<String>();
    var fileNames = new ArrayList<String>();
    var blocks = new ArrayList<InputContentBlock>();
    long totalBytes = 0;
    for (int i = 0; i < files.size(); i++) {
      var file = files.get(i);
      var document = file != null ? file.document() : null;
      if (document == null) {
        throw new ConnectorException(
            ERROR_CODE_DOCUMENT_UPLOAD_FAILED, "Document at index " + i + " must not be null");
      }
      var fileName = uniqueFileName(file.fileName(), document, i, usedNames);
      byte[] content;
      try {
        content = document.asByteArray();
      } catch (Exception e) {
        throw new ConnectorException(
            ERROR_CODE_DOCUMENT_UPLOAD_FAILED,
            "Failed to read document '" + fileName + "': " + e.getMessage(),
            e);
      }
      totalBytes += content.length;
      if (totalBytes > MAX_TOTAL_UPLOAD_SIZE) {
        throw new ConnectorException(
            ERROR_CODE_DOCUMENT_UPLOAD_FAILED,
            "Documents exceed the maximum total upload size of "
                + MAX_TOTAL_UPLOAD_SIZE
                + " bytes");
      }
      var path = UPLOAD_DIRECTORY + "/" + fileName;
      fileNames.add(path);
      blocks.add(toInputContentBlock(path, document, content));
    }

    try {
      var results =
          invokeTool(
              session, ToolName.WRITE_FILES, ToolArguments.builder().content(blocks).build());
      for (var result : results) {
        if (Boolean.TRUE.equals(result.isError())) {
          throw new ConnectorException(
              ERROR_CODE_DOCUMENT_UPLOAD_FAILED,
              "Failed to upload documents to the Code Interpreter session: "
                  + describeResult(result));
        }
      }
    } catch (ConnectorException e) {
      if (ERROR_CODE_DOCUMENT_UPLOAD_FAILED.equals(e.getErrorCode())) {
        throw e;
      }
      throw new ConnectorException(
          ERROR_CODE_DOCUMENT_UPLOAD_FAILED,
          "Failed to upload documents to the Code Interpreter session: " + e.getMessage(),
          e);
    }
    LOG.debug("Uploaded documents: {}", fileNames);
    return List.copyOf(fileNames);
  }

  private InputContentBlock toInputContentBlock(
      String fileName, Document document, byte[] content) {
    var builder = InputContentBlock.builder().path(fileName);
    var text = isTextContentType(document.metadata()) ? decodeUtf8(content) : null;
    return text != null
        ? builder.text(text).build()
        : builder.blob(SdkBytes.fromByteArray(content)).build();
  }

  private boolean isTextContentType(DocumentMetadata metadata) {
    var contentType = metadata != null ? metadata.getContentType() : null;
    if (contentType == null) {
      return false;
    }
    var mimeType = contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
    return mimeType.startsWith("text/")
        || mimeType.endsWith("+json")
        || mimeType.endsWith("+xml")
        || TEXT_APPLICATION_TYPES.contains(mimeType);
  }

  /** Returns the decoded text, or {@code null} when the content is not valid UTF-8. */
  private String decodeUtf8(byte[] content) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(content))
          .toString();
    } catch (CharacterCodingException e) {
      return null;
    }
  }

  private String describeResult(CodeInterpreterResult result) {
    if (result.hasContent()) {
      var message =
          result.content().stream()
              .map(ContentBlock::text)
              .filter(Objects::nonNull)
              .reduce((a, b) -> a + " " + b);
      if (message.isPresent()) {
        return message.get();
      }
    }
    return "the tool reported an error";
  }

  /**
   * Derives a plain file name from the requested name, or else from the document metadata, and
   * makes it unique among {@code usedNames}: a numeric suffix is added before the extension ({@code
   * report.pdf}, {@code report-2.pdf}, ...).
   */
  private String uniqueFileName(
      String requestedName, Document document, int index, Set<String> usedNames) {
    var base = sanitizeFileName(requestedName);
    if (base == null && document.metadata() != null) {
      base = sanitizeFileName(document.metadata().getFileName());
    }
    if (base == null) {
      base = FALLBACK_DOCUMENT_NAME_PREFIX + (index + 1);
    }
    var candidate = base;
    var dot = base.lastIndexOf('.');
    var stem = dot > 0 ? base.substring(0, dot) : base;
    var extension = dot > 0 ? base.substring(dot) : "";
    for (int counter = 2; !usedNames.add(candidate); counter++) {
      candidate = stem + "-" + counter + extension;
    }
    return candidate;
  }

  /**
   * Reduces the given name to its last path segment without control characters. Returns {@code
   * null} if nothing usable remains.
   */
  private String sanitizeFileName(String name) {
    if (name == null) {
      return null;
    }
    var segment = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
    var cleaned = segment.replaceAll("\\p{Cntrl}", "").trim();
    return cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..") ? null : cleaned;
  }

  // --- Code execution ---

  private CodeInterpreterResponse invokeCode(
      CodeInterpreterSession session, CodeInterpreterInput input, List<String> uploadedFiles) {
    var existingFiles = listGeneratedFiles(session);
    var language = input.getLanguage().getValue();

    var execResult =
        invokeTool(
            session,
            ToolName.EXECUTE_CODE,
            ToolArguments.builder().language(language).code(input.getCode()).build());

    var stdoutBuilder = new StringBuilder();
    var stderrBuilder = new StringBuilder();
    int exitCode = 0;
    double executionTime = 0;

    for (var result : execResult) {
      if (result.structuredContent() != null) {
        var sc = result.structuredContent();
        if (sc.stdout() != null) {
          stdoutBuilder.append(sc.stdout());
        }
        if (sc.stderr() != null) {
          stderrBuilder.append(sc.stderr());
        }
        if (sc.exitCode() != null) {
          exitCode = sc.exitCode();
        }
        if (sc.executionTime() != null) {
          executionTime = sc.executionTime();
        }
      }
    }

    int maxFiles =
        Math.max(1, input.getMaxFiles() != null ? input.getMaxFiles() : DEFAULT_MAX_FILES);
    long maxFileSize =
        Math.max(
            0,
            input.getMaxTotalFileSize() != null
                ? input.getMaxTotalFileSize()
                : DEFAULT_MAX_TOTAL_FILE_SIZE);
    var files = retrieveNewFiles(session, existingFiles, maxFiles, maxFileSize);

    return new CodeInterpreterResponse(
        stdoutBuilder.toString(),
        stderrBuilder.toString(),
        exitCode,
        executionTime,
        files,
        uploadedFiles);
  }

  // --- File retrieval ---

  private List<Document> retrieveNewFiles(
      CodeInterpreterSession session, List<String> existingFiles, int maxFiles, long maxFileSize) {
    var newFiles = filterNewFiles(listGeneratedFiles(session), existingFiles, maxFiles);
    if (newFiles.isEmpty()) {
      return List.of();
    }
    LOG.debug("New files generated: {}", newFiles);
    return readFilesWithSizeLimit(session, newFiles, maxFileSize);
  }

  private List<String> filterNewFiles(
      List<String> allFiles, List<String> existingFiles, int maxFiles) {
    var newFiles = new ArrayList<>(allFiles);
    newFiles.removeAll(existingFiles);
    newFiles.removeIf(f -> f.startsWith("."));
    if (newFiles.size() > maxFiles) {
      LOG.warn("Found {} new files, limiting retrieval to {}", newFiles.size(), maxFiles);
      return new ArrayList<>(newFiles.subList(0, maxFiles));
    }
    return newFiles;
  }

  private List<Document> readFilesWithSizeLimit(
      CodeInterpreterSession session, List<String> files, long maxFileSize) {
    var documents = new ArrayList<Document>();
    long totalBytes = 0;
    for (var file : files) {
      try {
        var blocks = readFileBlocks(session, file);
        var blocksToConvert = new ArrayList<ContentBlock>();
        for (var block : blocks) {
          var blockSize = getBlockSize(block);
          if (totalBytes + blockSize > maxFileSize) {
            LOG.warn("Total file size exceeds {} bytes, stopping retrieval", maxFileSize);
            documents.addAll(convertToDocuments(blocksToConvert, file));
            return documents;
          }
          totalBytes += blockSize;
          blocksToConvert.add(block);
        }
        documents.addAll(convertToDocuments(blocksToConvert, file));
      } catch (Exception e) {
        LOG.warn("Failed to read file {}: {}", file, e.getMessage());
      }
    }
    return documents;
  }

  /** Gets the size of a content block. */
  private long getBlockSize(ContentBlock block) {
    if (block.size() != null) {
      return block.size();
    }
    if (block.data() != null) {
      return block.data().asByteArray().length;
    }
    if (block.resource() != null) {
      if (block.resource().blob() != null) {
        return block.resource().blob().asByteArray().length;
      }
      if (block.resource().text() != null) {
        return block.resource().text().getBytes(StandardCharsets.UTF_8).length;
      }
    }
    return 0L;
  }

  /** Lists files in the sandbox working directory. */
  private List<String> listGeneratedFiles(CodeInterpreterSession session) {
    var listResult =
        invokeTool(session, ToolName.LIST_FILES, ToolArguments.builder().directoryPath("").build());

    return listResult.stream()
        .filter(CodeInterpreterResult::hasContent)
        .flatMap(r -> r.content().stream())
        .map(this::extractFileNameFromListResult)
        .filter(Objects::nonNull)
        .toList();
  }

  private List<ContentBlock> readFileBlocks(CodeInterpreterSession session, String path) {
    var readResult =
        invokeTool(
            session, ToolName.READ_FILES, ToolArguments.builder().paths(List.of(path)).build());

    return readResult.stream()
        .filter(CodeInterpreterResult::hasContent)
        .flatMap(r -> r.content().stream())
        .toList();
  }

  /** Converts content blocks to documents. */
  private List<Document> convertToDocuments(List<ContentBlock> blocks, String fileName) {
    var documents = new ArrayList<Document>();
    for (var block : blocks) {
      var data = extractData(block);
      if (data != null) {
        var mimeType = extractMimeType(block);
        documents.add(
            createDocument.apply(
                DocumentCreationRequest.from(data)
                    .contentType(mimeType)
                    .fileName(fileName)
                    .build()));
      }
    }
    return documents;
  }

  // --- Tool invocation ---

  private List<CodeInterpreterResult> invokeTool(
      CodeInterpreterSession session, ToolName toolName, ToolArguments arguments) {
    var request =
        InvokeCodeInterpreterRequest.builder()
            .codeInterpreterIdentifier(session.codeInterpreterIdentifier())
            .sessionId(session.id())
            .name(toolName)
            .arguments(arguments)
            .build();

    var results = new ArrayList<CodeInterpreterResult>();
    var handler =
        InvokeCodeInterpreterResponseHandler.builder()
            .onEventStream(
                publisher ->
                    publisher.subscribe(
                        event ->
                            event.accept(
                                InvokeCodeInterpreterResponseHandler.Visitor.builder()
                                    .onResult(results::add)
                                    .build())))
            .onResponse(ignored -> {})
            .build();

    try {
      asyncClient
          .invokeCodeInterpreter(request, handler)
          .get(session.timeout().toMinutes(), TimeUnit.MINUTES);
    } catch (TimeoutException e) {
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED, "Code Interpreter invocation timed out", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED, "Code Interpreter invocation interrupted", e);
    } catch (ExecutionException e) {
      throw new ConnectorException(
          ERROR_CODE_INTERPRETER_FAILED,
          "Code Interpreter error: " + e.getCause().getMessage(),
          e.getCause());
    }
    return results;
  }

  // --- Helpers ---

  private String resolveCodeInterpreterIdentifier(CodeInterpreterInput input) {
    return input.getCodeInterpreterIdentifier() != null
        ? input.getCodeInterpreterIdentifier()
        : DEFAULT_CODE_INTERPRETER_ID;
  }

  // --- Content block extraction helpers ---

  /**
   * Extracts a filename from a listFiles result block. Uses block.name() if available, otherwise
   * falls back to block.text() which contains the filename in listFiles responses.
   */
  private String extractFileNameFromListResult(ContentBlock block) {
    if (block.name() != null) {
      return block.name();
    }
    if (block.text() != null && !block.text().isBlank()) {
      return block.text().trim();
    }
    return null;
  }

  /** Extracts binary data from a content block. */
  private byte[] extractData(ContentBlock block) {
    if (block.data() != null) {
      return block.data().asByteArray();
    }
    if (block.resource() != null) {
      if (block.resource().blob() != null) {
        return block.resource().blob().asByteArray();
      }
      if (block.resource().text() != null) {
        return block.resource().text().getBytes(StandardCharsets.UTF_8);
      }
    }
    return null;
  }

  /** Extracts the MIME type from a content block. */
  private String extractMimeType(ContentBlock block) {
    if (block.mimeType() != null) {
      return block.mimeType();
    }
    if (block.resource() != null && block.resource().mimeType() != null) {
      return block.resource().mimeType();
    }
    if (block.resource() != null && block.resource().text() != null) {
      return "text/plain";
    }
    return DEFAULT_MIME_TYPE;
  }

  // --- Session wrapper ---

  /** AutoCloseable wrapper for Code Interpreter sessions. */
  private class CodeInterpreterSession implements AutoCloseable {
    private final String sessionId;
    private final String ciIdentifier;
    private final Duration sessionTimeout;

    CodeInterpreterSession(String sessionId, String ciIdentifier, Duration sessionTimeout) {
      this.sessionId = sessionId;
      this.ciIdentifier = ciIdentifier;
      this.sessionTimeout = sessionTimeout;
    }

    String id() {
      return sessionId;
    }

    String codeInterpreterIdentifier() {
      return ciIdentifier;
    }

    Duration timeout() {
      return sessionTimeout;
    }

    @Override
    public void close() {
      try {
        syncClient.stopCodeInterpreterSession(
            StopCodeInterpreterSessionRequest.builder()
                .codeInterpreterIdentifier(ciIdentifier)
                .sessionId(sessionId)
                .build());
      } catch (Exception e) {
        LOG.warn("Failed to stop Code Interpreter session {}: {}", sessionId, e.getMessage());
      }
    }
  }
}
