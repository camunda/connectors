/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.daytona;

import io.camunda.connector.agenticai.adhoctoolsschema.schema.GatewayToolDefinitionResolver;
import io.camunda.connector.agenticai.sandbox.daytona.DaytonaClient.DaytonaSandboxInfo;
import io.camunda.connector.agenticai.sandbox.daytona.DaytonaClient.ExecOutcome;
import io.camunda.connector.agenticai.sandbox.daytona.DaytonaClient.SandboxCreateSpec;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxCreateResult;
import io.camunda.connector.agenticai.sandbox.discovery.SandboxGatewayToolHandler;
import io.camunda.connector.agenticai.sandbox.discovery.SkillCatalogEntry;
import io.camunda.connector.agenticai.sandbox.skill.Skill;
import io.camunda.connector.agenticai.sandbox.skill.SkillMdParser;
import io.camunda.connector.agenticai.sandbox.skill.SkillMdParser.ParsedSkillMd;
import io.camunda.connector.agenticai.sandbox.skill.SkillResolver;
import io.camunda.connector.api.annotation.OutboundConnector;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.api.outbound.JobContext;
import io.camunda.connector.api.outbound.OutboundConnectorContext;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.generator.java.annotation.ElementTemplate;
import io.daytona.sdk.Daytona;
import io.daytona.sdk.Sandbox;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@OutboundConnector(
    name = "Daytona.io",
    inputVariables = {"data"},
    type = "io.camunda.agenticai:sandboxdaytona:1")
@ElementTemplate(
    id = "io.camunda.connectors.agenticai.sandbox.daytona.v0",
    name = "Daytona.io",
    description =
        "Provisions and manages a Daytona sandbox for AI agent tool execution (bash, file system, document and skill operations).",
    version = 1,
    icon = "daytona.svg",
    category = @ElementTemplate.Category(id = "aiTools", name = "AI Tools"),
    inputDataClass = SandboxDaytonaRequest.class,
    defaultResultVariable = "toolCallResult",
    propertyGroups = {
      @ElementTemplate.PropertyGroup(id = "sandbox", label = "Sandbox Configuration"),
      @ElementTemplate.PropertyGroup(id = "operation", label = "Operation"),
    },
    extensionProperties = {
      @ElementTemplate.ExtensionProperty(
          name = GatewayToolDefinitionResolver.GATEWAY_TYPE_EXTENSION,
          value = SandboxGatewayToolHandler.GATEWAY_TYPE)
      // NO condition — AI agent tool mode ONLY
    })
public class SandboxDaytonaFunction implements OutboundConnectorFunction {

  private static final Logger LOG = LoggerFactory.getLogger(SandboxDaytonaFunction.class);

  private static final int EXEC_TIMEOUT_SECONDS = OutputBounds.DEFAULT_TIMEOUT_SECONDS;
  private static final long MAX_OUTPUT_BYTES = OutputBounds.DEFAULT_MAX_OUTPUT_BYTES;
  private static final long DEFAULT_MAX_DOCUMENT_BYTES = 25L * 1024 * 1024;

  private final DaytonaClient daytonaClient;
  private final SkillResolver skillResolver;
  private final SkillMdParser skillMdParser;

  public SandboxDaytonaFunction() {
    this(new DaytonaClient());
  }

  public SandboxDaytonaFunction(DaytonaClient daytonaClient) {
    this(daytonaClient, new SkillResolver(), new SkillMdParser());
  }

  public SandboxDaytonaFunction(
      DaytonaClient daytonaClient, SkillResolver skillResolver, SkillMdParser skillMdParser) {
    this.daytonaClient = daytonaClient;
    this.skillResolver = skillResolver;
    this.skillMdParser = skillMdParser;
  }

  @Override
  public Object execute(OutboundConnectorContext context) {
    final SandboxDaytonaRequest request = context.bindVariables(SandboxDaytonaRequest.class);
    final SandboxDaytonaRequest.SandboxDaytonaRequestData data = request.data();
    final JobContext job = context.getJobContext();

    return switch (data.operation()) {
      case CREATE -> {
        Daytona daytona = DaytonaClient.buildClient(data.apiKey(), data.apiUrl());
        String processInstanceKey = String.valueOf(job.getProcessInstanceKey());
        String elementId = job.getElementId();
        var spec =
            new SandboxCreateSpec(
                data.snapshot(),
                data.autoStopMinutes(),
                data.autoArchiveMinutes(),
                data.autoDeleteMinutes());
        DaytonaSandboxInfo info =
            daytonaClient.create(daytona, spec, processInstanceKey, elementId);
        Sandbox sandbox = daytonaClient.connect(daytona, info.handle());
        String workDir = info.workDir();
        String skillsRoot = workDir + "/.agents/skills";
        daytonaClient.exec(sandbox, "mkdir -p " + skillsRoot, EXEC_TIMEOUT_SECONDS);
        List<Skill> skills = skillResolver.resolve(data.skills());
        for (Skill skill : skills) {
          for (Skill.SkillFile f : skill.files()) {
            daytonaClient.fsWrite(
                sandbox, skillsRoot + "/" + skill.name() + "/" + f.relativePath(), f.content());
          }
        }
        if (data.startupScript() != null && !data.startupScript().isBlank()) {
          ExecOutcome r = daytonaClient.exec(sandbox, data.startupScript(), EXEC_TIMEOUT_SECONDS);
          if (r.exitCode() != 0) {
            LOG.warn("Sandbox startup script exited with code {}: {}", r.exitCode(), r.stdout());
          }
        }
        List<SkillCatalogEntry> catalog = scanSkillCatalog(sandbox, skillsRoot);
        yield new SandboxCreateResult(info.handle(), workDir, catalog);
      }
      case BASH -> {
        String handle = requireHandle(data.handle(), "BASH");
        String command = requireArg(data.command(), "command", "BASH");
        Daytona daytona = DaytonaClient.buildClient(data.apiKey(), data.apiUrl());
        Sandbox sandbox = daytonaClient.connect(daytona, handle);
        ExecOutcome outcome = daytonaClient.exec(sandbox, command, EXEC_TIMEOUT_SECONDS);
        yield formatBashResult(outcome);
      }
      case FS_READ -> {
        String handle = requireHandle(data.handle(), "FS_READ");
        String path = requireArg(data.path(), "path", "FS_READ");
        Daytona daytona = DaytonaClient.buildClient(data.apiKey(), data.apiUrl());
        Sandbox sandbox = daytonaClient.connect(daytona, handle);
        byte[] bytes = daytonaClient.fsRead(sandbox, path);
        if (OutputBounds.isBinary(bytes)) {
          yield OutputBounds.binaryFileMarker(bytes.length, "application/octet-stream");
        }
        if (bytes.length > MAX_OUTPUT_BYTES) {
          yield OutputBounds.oversizedFileMarker(bytes.length, "text/plain");
        }
        yield new String(bytes, StandardCharsets.UTF_8);
      }
      case FS_WRITE -> {
        String handle = requireHandle(data.handle(), "FS_WRITE");
        String path = requireArg(data.path(), "path", "FS_WRITE");
        String content = data.content() != null ? data.content() : "";
        Daytona daytona = DaytonaClient.buildClient(data.apiKey(), data.apiUrl());
        Sandbox sandbox = daytonaClient.connect(daytona, handle);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        daytonaClient.fsWrite(sandbox, path, bytes);
        yield "Written " + bytes.length + " bytes to " + path;
      }
      case EXPORT_DOCUMENT -> {
        String handle = requireHandle(data.handle(), "EXPORT_DOCUMENT");
        String path = requireArg(data.path(), "path", "EXPORT_DOCUMENT");
        Daytona daytona = DaytonaClient.buildClient(data.apiKey(), data.apiUrl());
        Sandbox sandbox = daytonaClient.connect(daytona, handle);
        byte[] bytes = daytonaClient.fsRead(sandbox, path);
        if (bytes.length > DEFAULT_MAX_DOCUMENT_BYTES) {
          throw new ConnectorException(
              "SANDBOX_DOCUMENT_TOO_LARGE",
              "File '%s' is too large to export (%d bytes); maximum allowed is %d bytes."
                  .formatted(path, bytes.length, DEFAULT_MAX_DOCUMENT_BYTES));
        }
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        String contentType = "application/octet-stream";
        Document doc =
            context.create(
                DocumentCreationRequest.from(bytes)
                    .contentType(contentType)
                    .fileName(fileName)
                    .build());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(
            "summary", "Exported '%s' (%d bytes) as a document.".formatted(path, bytes.length));
        result.put("document", doc);
        yield result;
      }
      case IMPORT_DOCUMENT -> {
        Document document = data.document();
        if (document == null) {
          throw new ConnectorException(
              "SANDBOX_IMPORT_NO_DOCUMENT",
              "No document to import: the requested document id was not found in the conversation registry.");
        }
        String handle = requireHandle(data.handle(), "IMPORT_DOCUMENT");
        byte[] bytes = document.asByteArray();
        if (bytes.length > DEFAULT_MAX_DOCUMENT_BYTES) {
          throw new ConnectorException(
              "SANDBOX_DOCUMENT_TOO_LARGE",
              "Document is too large to import (%d bytes); maximum allowed is %d bytes."
                  .formatted(bytes.length, DEFAULT_MAX_DOCUMENT_BYTES));
        }
        String targetPath;
        if (data.path() != null && !data.path().isBlank()) {
          targetPath = data.path();
        } else if (document.metadata() != null
            && document.metadata().getFileName() != null
            && !document.metadata().getFileName().isBlank()) {
          targetPath = document.metadata().getFileName();
        } else {
          targetPath = "imported-file";
        }
        Daytona daytona = DaytonaClient.buildClient(data.apiKey(), data.apiUrl());
        Sandbox sandbox = daytonaClient.connect(daytona, handle);
        daytonaClient.fsWrite(sandbox, targetPath, bytes);
        String fileName =
            targetPath.contains("/")
                ? targetPath.substring(targetPath.lastIndexOf('/') + 1)
                : targetPath;
        yield "Imported '%s' (%d bytes) to %s.".formatted(fileName, bytes.length, targetPath);
      }
    };
  }

  private List<SkillCatalogEntry> scanSkillCatalog(Sandbox sandbox, String skillsRoot) {
    ExecOutcome find =
        daytonaClient.exec(
            sandbox,
            "find " + skillsRoot + " -maxdepth 2 -name SKILL.md -type f 2>/dev/null | sort",
            EXEC_TIMEOUT_SECONDS);
    if (find.exitCode() != 0 || find.stdout() == null || find.stdout().isBlank()) {
      return List.of();
    }
    List<SkillCatalogEntry> catalog = new ArrayList<>();
    for (String line : find.stdout().split("\\R")) {
      String location = line.trim();
      if (location.isBlank()) {
        continue;
      }
      try {
        byte[] bytes = daytonaClient.fsRead(sandbox, location);
        ParsedSkillMd parsed = skillMdParser.parse(new String(bytes, StandardCharsets.UTF_8));
        int lastSlash = location.lastIndexOf('/');
        String dirName =
            lastSlash > 0
                ? location.substring(location.lastIndexOf('/', lastSlash - 1) + 1, lastSlash)
                : location;
        String name = (parsed.name() != null && !parsed.name().isBlank()) ? parsed.name() : dirName;
        catalog.add(new SkillCatalogEntry(name, parsed.description(), location));
      } catch (Exception e) {
        LOG.warn("Skipping SKILL.md at '{}' due to parse error: {}", location, e.getMessage());
      }
    }
    return List.copyOf(catalog);
  }

  private String formatBashResult(ExecOutcome outcome) {
    String stdout = renderOutput(outcome.stdout());
    String stderr = renderOutput(outcome.stderr());

    StringBuilder sb = new StringBuilder();
    sb.append("exit_code: ").append(outcome.exitCode()).append("\n");
    if (!stdout.isEmpty()) {
      sb.append("stdout:\n").append(stdout);
      if (!stdout.endsWith("\n")) sb.append("\n");
    }
    if (!stderr.isEmpty()) {
      sb.append("stderr:\n").append(stderr);
      if (!stderr.endsWith("\n")) sb.append("\n");
    }
    return sb.toString().trim();
  }

  private String renderOutput(String text) {
    if (text == null || text.isEmpty()) return "";
    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
    if (OutputBounds.isBinary(bytes)) return OutputBounds.binaryOutputMarker(bytes.length);
    return OutputBounds.truncate(text, MAX_OUTPUT_BYTES, false);
  }

  private static String requireHandle(String handle, String op) {
    if (handle == null || handle.isBlank()) {
      throw new ConnectorException(
          "SANDBOX_MISSING_HANDLE", "handle is required for operation " + op);
    }
    return handle;
  }

  private static String requireArg(String value, String argName, String op) {
    if (value == null || value.isBlank()) {
      throw new ConnectorException(
          "SANDBOX_MISSING_ARG", argName + " is required for operation " + op);
    }
    return value;
  }
}
