/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.daytona;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NestedPropertyCondition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Input record for the Sandbox (Daytona) outbound connector. */
public record SandboxDaytonaRequest(@Valid @NotNull SandboxDaytonaRequestData data) {

  /**
   * Request payload nested under the {@code data} key.
   *
   * @param apiKey Daytona API key (always redacted in toString).
   * @param apiUrl Optional base URL for self-hosted Daytona deployments.
   * @param snapshot Optional pre-loaded workspace image/snapshot reference.
   * @param autoStop Auto-stop lifecycle settings (mode + duration). Bound under {@code
   *     data.autoStop.*}.
   * @param autoArchive Auto-archive lifecycle settings (mode + duration). Bound under {@code
   *     data.autoArchive.*}.
   * @param autoDelete Auto-delete lifecycle settings (mode + duration). Bound under {@code
   *     data.autoDelete.*}.
   * @param skills Skill bundles materialized into the sandbox at creation.
   * @param startupScript Optional shell script run once at sandbox creation.
   * @param connectorMode How this connector is used (currently only AI Agent tool mode).
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SandboxDaytonaRequestData(
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
      @Valid @Nullable AutoStopConfiguration autoStop,
      @Valid @Nullable AutoArchiveConfiguration autoArchive,
      @Valid @Nullable AutoDeleteConfiguration autoDelete,
      @TemplateProperty(
              group = "sandbox",
              label = "Skills",
              description =
                  "Skill bundles (.zip documents) materialized into the sandbox workspace (.agents/skills/) at creation. Activated on demand by the agent via sandbox_fs_read of the skill's SKILL.md.",
              feel = FeelMode.required,
              optional = true)
          @Nullable List<Document> skills,
      @TemplateProperty(
              group = "sandbox",
              label = "Startup script",
              description =
                  "Optional shell script run once at sandbox creation (e.g. to install tools or add skills). Runs after skill bundles are materialized.",
              type = TemplateProperty.PropertyType.Text,
              feel = FeelMode.optional,
              optional = true)
          @Nullable String startupScript,
      @Valid @NotNull SandboxConnectorMode connectorMode) {

    /**
     * Returns the auto-stop interval in minutes for the Daytona API.
     *
     * <ul>
     *   <li>{@link AutoStopMode#DISABLED} → {@code 0} (Daytona: never auto-stop)
     *   <li>{@link AutoStopMode#DURATION} (or {@code null}/absent) → parsed from the configured
     *       duration, default {@code "PT15M"} (15 minutes)
     * </ul>
     */
    public Integer autoStopMinutes() {
      AutoStopMode mode = autoStop != null && autoStop.mode() != null ? autoStop.mode() : null;
      AutoStopMode effectiveMode = mode != null ? mode : AutoStopMode.DURATION;
      return switch (effectiveMode) {
        case DISABLED -> 0;
        case DURATION ->
            parseDurationToMinutes(
                durationOrDefault(autoStop != null ? autoStop.duration() : null, "PT15M"),
                "autoStop.duration");
      };
    }

    /**
     * Returns the auto-archive interval in minutes for the Daytona API, or {@code null} to use the
     * provider default.
     *
     * <ul>
     *   <li>{@link AutoArchiveMode#DEFAULT} (or {@code null}/absent) → {@code null} (use Daytona
     *       default)
     *   <li>{@link AutoArchiveMode#DURATION} → parsed from the configured duration, default {@code
     *       "P7D"} (7 days). Must not exceed 30 days.
     * </ul>
     */
    @Nullable
    public Integer autoArchiveMinutes() {
      AutoArchiveMode mode =
          autoArchive != null && autoArchive.mode() != null ? autoArchive.mode() : null;
      AutoArchiveMode effectiveMode = mode != null ? mode : AutoArchiveMode.DEFAULT;
      return switch (effectiveMode) {
        case DEFAULT -> null;
        case DURATION -> {
          Duration d =
              parseDuration(
                  durationOrDefault(autoArchive != null ? autoArchive.duration() : null, "P7D"),
                  "autoArchive.duration");
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
     *   <li>{@link AutoDeleteMode#DISABLED} (or {@code null}/absent) → {@code null}
     *   <li>{@link AutoDeleteMode#IMMEDIATELY} → {@code 0} (Daytona: delete immediately after stop)
     *   <li>{@link AutoDeleteMode#DURATION} → parsed from the configured duration, default {@code
     *       "PT5M"} (5 minutes)
     * </ul>
     */
    @Nullable
    public Integer autoDeleteMinutes() {
      AutoDeleteMode mode =
          autoDelete != null && autoDelete.mode() != null ? autoDelete.mode() : null;
      AutoDeleteMode effectiveMode = mode != null ? mode : AutoDeleteMode.DISABLED;
      return switch (effectiveMode) {
        case DISABLED -> null;
        case IMMEDIATELY -> 0;
        case DURATION ->
            parseDurationToMinutes(
                durationOrDefault(autoDelete != null ? autoDelete.duration() : null, "PT5M"),
                "autoDelete.duration");
      };
    }

    @Override
    public String toString() {
      return "SandboxDaytonaRequestData[apiKey=REDACTED, apiUrl="
          + apiUrl
          + ", snapshot="
          + snapshot
          + ", autoStop="
          + autoStop
          + ", autoArchive="
          + autoArchive
          + ", autoDelete="
          + autoDelete
          + ", skills.size="
          + (skills != null ? skills.size() : 0)
          + ", connectorMode="
          + connectorMode
          + "]";
    }

    private static String durationOrDefault(@Nullable String value, String defaultValue) {
      return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    private static int parseDurationToMinutes(String iso8601, String fieldName) {
      return (int) parseDuration(iso8601, fieldName).toMinutes();
    }

    private static Duration parseDuration(String iso8601, String fieldName) {
      try {
        return Duration.parse(iso8601);
      } catch (DateTimeParseException e) {
        throw new ConnectorException(
            "Invalid ISO-8601 duration for " + fieldName + ": " + iso8601, e);
      }
    }

    /**
     * Auto-stop lifecycle settings. Bound under {@code data.autoStop.*}.
     *
     * @param mode {@link AutoStopMode#DISABLED} (never auto-stop) or {@link AutoStopMode#DURATION}
     *     (stop after idle time).
     * @param duration ISO-8601 duration, e.g. {@code "PT15M"}. Used when {@code mode} is DURATION.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AutoStopConfiguration(
        @TemplateProperty(
                group = "sandbox",
                label = "Auto-stop",
                description =
                    "Controls how long a running sandbox stays up before stopping. DISABLED means it never auto-stops (Daytona default: 15 min).",
                type = TemplateProperty.PropertyType.Dropdown,
                defaultValue = "DURATION",
                choices = {
                  @TemplateProperty.DropdownPropertyChoice(value = "DISABLED", label = "Disabled"),
                  @TemplateProperty.DropdownPropertyChoice(
                      value = "DURATION",
                      label = "Custom Duration")
                })
            @Nullable AutoStopMode mode,
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
                              property = "data.autoStop.mode",
                              equals = "DURATION")
                        }))
            @Nullable String duration) {}

    /**
     * Auto-archive lifecycle settings. Bound under {@code data.autoArchive.*}.
     *
     * @param mode {@link AutoArchiveMode#DEFAULT} (Daytona default 7 days) or {@link
     *     AutoArchiveMode#DURATION}.
     * @param duration ISO-8601 duration. Max 30 days. Used when {@code mode} is DURATION.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AutoArchiveConfiguration(
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
                  @TemplateProperty.DropdownPropertyChoice(
                      value = "DURATION",
                      label = "Custom Duration")
                })
            @Nullable AutoArchiveMode mode,
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
                              property = "data.autoArchive.mode",
                              equals = "DURATION")
                        }))
            @Nullable String duration) {}

    /**
     * Auto-delete lifecycle settings. Bound under {@code data.autoDelete.*}.
     *
     * @param mode {@link AutoDeleteMode#DISABLED} (never), {@link AutoDeleteMode#IMMEDIATELY}
     *     (delete right after stopping), or {@link AutoDeleteMode#DURATION}.
     * @param duration ISO-8601 duration. Default {@code "PT5M"} (5 minutes). Used when {@code mode}
     *     is DURATION.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AutoDeleteConfiguration(
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
                  @TemplateProperty.DropdownPropertyChoice(
                      value = "DURATION",
                      label = "Custom Duration")
                })
            @Nullable AutoDeleteMode mode,
        @TemplateProperty(
                group = "sandbox",
                label = "Auto-delete duration",
                description =
                    "ISO-8601 duration after which a stopped sandbox is deleted. Default: PT5M (5 minutes).",
                optional = true,
                defaultValue = "PT5M",
                condition =
                    @TemplateProperty.PropertyCondition(
                        property = "",
                        allMatch = {
                          @NestedPropertyCondition(
                              property = "data.autoDelete.mode",
                              equals = "DURATION")
                        }))
            @Nullable String duration) {}

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
  }
}
