/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NestedPropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.Nullable;

/**
 * Discriminated configuration for the sandbox provider. Modeled as a discriminated group in the
 * element templates (identical pattern to {@link
 * io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration} and {@link
 * MemoryStorageConfiguration}).
 *
 * <p>When the selected subtype is {@link DisabledSandboxConfiguration} (the default), internal
 * tools ({@code bash}, {@code fs_read}, {@code fs_write}) are NOT registered and agent behaviour is
 * byte-for-byte unchanged.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(
      value = SandboxConfiguration.DisabledSandboxConfiguration.class,
      name = SandboxConfiguration.DisabledSandboxConfiguration.TYPE),
  @JsonSubTypes.Type(
      value = SandboxConfiguration.DaytonaSandboxConfiguration.class,
      name = SandboxConfiguration.DaytonaSandboxConfiguration.TYPE)
})
@TemplateDiscriminatorProperty(
    label = "Sandbox",
    group = "sandbox",
    name = "type",
    defaultValue = SandboxConfiguration.DisabledSandboxConfiguration.TYPE,
    description =
        "Optional sandbox for in-process tools (bash, file I/O). When disabled, the agent behaves exactly as without a sandbox.")
public sealed interface SandboxConfiguration
    permits SandboxConfiguration.DisabledSandboxConfiguration,
        SandboxConfiguration.DaytonaSandboxConfiguration {

  /**
   * Returns the provider type discriminator string, e.g. {@code "disabled"} or {@code "daytona"}.
   */
  String providerType();

  /** Disabled sandbox — the default. Internal tools are not registered when this is active. */
  @TemplateSubType(id = DisabledSandboxConfiguration.TYPE, label = "Disabled")
  @JsonIgnoreProperties(ignoreUnknown = true)
  record DisabledSandboxConfiguration() implements SandboxConfiguration {

    public static final String TYPE = "disabled";

    @Override
    public String providerType() {
      return TYPE;
    }
  }

  /**
   * Configuration for the Daytona sandbox provider — the primary PoC target.
   *
   * @param apiKey Daytona API key (always redacted in toString).
   * @param apiUrl Optional base URL for self-hosted Daytona deployments.
   * @param snapshot Optional pre-loaded workspace image/snapshot reference.
   * @param autoStop Auto-stop mode: DISABLED (never auto-stop) or DURATION (stop after idle time).
   * @param autoStopDuration ISO-8601 duration, e.g. {@code "PT15M"}. Used when autoStop is
   *     DURATION.
   * @param autoArchive Auto-archive mode: DEFAULT (Daytona default 7 days) or DURATION.
   * @param autoArchiveDuration ISO-8601 duration. Max 30 days. Used when autoArchive is DURATION.
   * @param autoDelete Auto-delete mode: DISABLED (never), IMMEDIATELY (delete right after
   *     stopping), or DURATION.
   * @param autoDeleteDuration ISO-8601 duration. Used when autoDelete is DURATION.
   */
  @TemplateSubType(id = DaytonaSandboxConfiguration.TYPE, label = "Daytona")
  @JsonIgnoreProperties(ignoreUnknown = true)
  record DaytonaSandboxConfiguration(
      @TemplateProperty(
              group = "sandbox",
              label = "API key",
              constraints = @TemplateProperty.PropertyConstraints(notEmpty = true))
          @NotBlank
          String apiKey,
      @TemplateProperty(
              group = "sandbox",
              label = "API URL",
              description = "Base URL for self-hosted Daytona deployments.",
              optional = true)
          @Nullable String apiUrl,
      @TemplateProperty(group = "sandbox", label = "Snapshot", optional = true)
          @Nullable String snapshot,
      @TemplateProperty(
              group = "sandbox",
              label = "Auto-stop",
              description =
                  "Controls how long a running sandbox stays up before stopping. DISABLED means it never auto-stops (Daytona default: 15 min).",
              type = TemplateProperty.PropertyType.Dropdown,
              defaultValue = "DURATION",
              choices = {
                @TemplateProperty.DropdownPropertyChoice(value = "DISABLED", label = "Disabled"),
                @TemplateProperty.DropdownPropertyChoice(value = "DURATION", label = "Duration")
              })
          @Nullable AutoStopMode autoStop,
      @TemplateProperty(
              group = "sandbox",
              label = "Auto-stop duration",
              description =
                  "ISO-8601 duration after which an idle running sandbox is stopped. Default: PT15M (15 minutes).",
              optional = true,
              defaultValue = "PT15M",
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "",
                      allMatch = {
                        @NestedPropertyCondition(
                            property = "data.sandbox.type",
                            equals = "daytona"),
                        @NestedPropertyCondition(
                            property = "data.sandbox.autoStop",
                            equals = "DURATION")
                      }))
          @Nullable String autoStopDuration,
      @TemplateProperty(
              group = "sandbox",
              label = "Auto-archive",
              description =
                  "Controls when a stopped sandbox is archived. DEFAULT uses Daytona's default (7 days). Max duration: 30 days.",
              type = TemplateProperty.PropertyType.Dropdown,
              defaultValue = "DEFAULT",
              choices = {
                @TemplateProperty.DropdownPropertyChoice(
                    value = "DEFAULT",
                    label = "Default (7 days)"),
                @TemplateProperty.DropdownPropertyChoice(value = "DURATION", label = "Duration")
              })
          @Nullable AutoArchiveMode autoArchive,
      @TemplateProperty(
              group = "sandbox",
              label = "Auto-archive duration",
              description =
                  "ISO-8601 duration after which a stopped sandbox is archived. Must not exceed 30 days (P30D).",
              optional = true,
              defaultValue = "P7D",
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "",
                      allMatch = {
                        @NestedPropertyCondition(
                            property = "data.sandbox.type",
                            equals = "daytona"),
                        @NestedPropertyCondition(
                            property = "data.sandbox.autoArchive",
                            equals = "DURATION")
                      }))
          @Nullable String autoArchiveDuration,
      @TemplateProperty(
              group = "sandbox",
              label = "Auto-delete",
              description =
                  "Controls when a stopped sandbox is deleted. DISABLED means never delete. IMMEDIATELY deletes the sandbox right after it stops (0 minutes).",
              type = TemplateProperty.PropertyType.Dropdown,
              defaultValue = "DISABLED",
              choices = {
                @TemplateProperty.DropdownPropertyChoice(value = "DISABLED", label = "Disabled"),
                @TemplateProperty.DropdownPropertyChoice(
                    value = "IMMEDIATELY",
                    label = "Immediately after stopping"),
                @TemplateProperty.DropdownPropertyChoice(value = "DURATION", label = "Duration")
              })
          @Nullable AutoDeleteMode autoDelete,
      @TemplateProperty(
              group = "sandbox",
              label = "Auto-delete duration",
              description = "ISO-8601 duration after which a stopped sandbox is deleted.",
              optional = true,
              condition =
                  @TemplateProperty.PropertyCondition(
                      property = "",
                      allMatch = {
                        @NestedPropertyCondition(
                            property = "data.sandbox.type",
                            equals = "daytona"),
                        @NestedPropertyCondition(
                            property = "data.sandbox.autoDelete",
                            equals = "DURATION")
                      }))
          @Nullable String autoDeleteDuration)
      implements SandboxConfiguration {

    /** Jackson / registry discriminator value for this subtype. */
    public static final String TYPE = "daytona";

    /** Auto-stop mode for the Daytona sandbox. */
    public enum AutoStopMode {
      /** Never auto-stop (sets interval to 0). */
      DISABLED,
      /** Stop after the configured duration of idle time. */
      DURATION
    }

    /** Auto-archive mode for the Daytona sandbox. */
    public enum AutoArchiveMode {
      /** Use Daytona's default (7 days). */
      DEFAULT,
      /** Archive after the configured duration. Max 30 days. */
      DURATION
    }

    /** Auto-delete mode for the Daytona sandbox. */
    public enum AutoDeleteMode {
      /** Never delete the sandbox automatically. */
      DISABLED,
      /** Delete immediately after the sandbox stops (0 minutes). */
      IMMEDIATELY,
      /** Delete after the configured duration. */
      DURATION
    }

    @Override
    public String providerType() {
      return TYPE;
    }

    /**
     * Returns the auto-stop interval in minutes for the Daytona API.
     *
     * <ul>
     *   <li>{@link AutoStopMode#DISABLED} → {@code 0} (Daytona: never auto-stop)
     *   <li>{@link AutoStopMode#DURATION} (or {@code null}) → parsed from {@code autoStopDuration},
     *       default {@code "PT15M"} (15 minutes)
     * </ul>
     */
    public Integer autoStopMinutes() {
      AutoStopMode effectiveMode = autoStop != null ? autoStop : AutoStopMode.DURATION;
      return switch (effectiveMode) {
        case DISABLED -> 0;
        case DURATION -> {
          String duration =
              (autoStopDuration != null && !autoStopDuration.isBlank())
                  ? autoStopDuration
                  : "PT15M";
          yield parseDurationToMinutes(duration, "autoStopDuration");
        }
      };
    }

    /**
     * Returns the auto-archive interval in minutes for the Daytona API, or {@code null} to use the
     * provider default.
     *
     * <ul>
     *   <li>{@link AutoArchiveMode#DEFAULT} (or {@code null}) → {@code null} (use Daytona default)
     *   <li>{@link AutoArchiveMode#DURATION} → parsed from {@code autoArchiveDuration}, default
     *       {@code "P7D"} (7 days). Must not exceed 30 days.
     * </ul>
     */
    @Nullable
    public Integer autoArchiveMinutes() {
      AutoArchiveMode effectiveMode = autoArchive != null ? autoArchive : AutoArchiveMode.DEFAULT;
      return switch (effectiveMode) {
        case DEFAULT -> null;
        case DURATION -> {
          String duration =
              (autoArchiveDuration != null && !autoArchiveDuration.isBlank())
                  ? autoArchiveDuration
                  : "P7D";
          Duration d = parseDuration(duration, "autoArchiveDuration");
          long maxMinutes = 30L * 24 * 60; // 30 days in minutes
          if (d.toMinutes() > maxMinutes) {
            throw new ConnectorException("Daytona auto-archive interval must not exceed 30 days");
          }
          yield (int) d.toMinutes();
        }
      };
    }

    /**
     * Returns the auto-delete interval in minutes for the Daytona API, or {@code null} if
     * auto-delete is disabled.
     *
     * <ul>
     *   <li>{@link AutoDeleteMode#DISABLED} (or {@code null}) → {@code null}
     *   <li>{@link AutoDeleteMode#IMMEDIATELY} → {@code 0} (Daytona: delete immediately after stop)
     *   <li>{@link AutoDeleteMode#DURATION} → parsed from {@code autoDeleteDuration} (required)
     * </ul>
     */
    @Nullable
    public Integer autoDeleteMinutes() {
      AutoDeleteMode effectiveMode = autoDelete != null ? autoDelete : AutoDeleteMode.DISABLED;
      return switch (effectiveMode) {
        case DISABLED -> null;
        case IMMEDIATELY -> 0;
        case DURATION -> {
          if (autoDeleteDuration == null || autoDeleteDuration.isBlank()) {
            throw new ConnectorException(
                "Daytona auto-delete duration is required when mode is DURATION");
          }
          yield (int) parseDuration(autoDeleteDuration, "autoDeleteDuration").toMinutes();
        }
      };
    }

    private static Duration parseDuration(String value, String fieldName) {
      try {
        Duration d = Duration.parse(value);
        if (d.isNegative()) {
          throw new ConnectorException(
              "Daytona " + fieldName + " must not be negative, got: " + value);
        }
        return d;
      } catch (DateTimeParseException e) {
        String msg = "Invalid ISO-8601 duration for " + fieldName + ": " + value;
        throw new ConnectorException(null, msg, e);
      }
    }

    private static int parseDurationToMinutes(String value, String fieldName) {
      return (int) parseDuration(value, fieldName).toMinutes();
    }

    /** Redacts {@code apiKey} to avoid leaking credentials in logs. */
    @Override
    public String toString() {
      return "DaytonaSandboxConfiguration{apiKey=[REDACTED], apiUrl="
          + apiUrl
          + ", snapshot="
          + snapshot
          + ", autoStop="
          + autoStop
          + ", autoStopDuration="
          + autoStopDuration
          + ", autoArchive="
          + autoArchive
          + ", autoArchiveDuration="
          + autoArchiveDuration
          + ", autoDelete="
          + autoDelete
          + ", autoDeleteDuration="
          + autoDeleteDuration
          + "}";
    }
  }
}
