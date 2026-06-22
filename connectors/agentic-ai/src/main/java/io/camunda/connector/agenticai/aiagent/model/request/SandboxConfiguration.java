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
import io.camunda.connector.generator.java.annotation.TemplateDiscriminatorProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
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
   * @param autoStopMinutes Idle auto-stop timeout in minutes; {@code 0} = never; default is 15.
   * @param autoArchiveMinutes Idle auto-archive timeout in minutes; provider default if null.
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
              label = "Auto-stop (minutes)",
              type = TemplateProperty.PropertyType.Number,
              optional = true)
          @Nullable Integer autoStopMinutes,
      @TemplateProperty(
              group = "sandbox",
              label = "Auto-archive (minutes)",
              type = TemplateProperty.PropertyType.Number,
              optional = true)
          @Nullable Integer autoArchiveMinutes)
      implements SandboxConfiguration {

    /** Jackson / registry discriminator value for this subtype. */
    public static final String TYPE = "daytona";

    @Override
    public String providerType() {
      return TYPE;
    }

    /** Redacts {@code apiKey} to avoid leaking credentials in logs. */
    @Override
    public String toString() {
      return "DaytonaSandboxConfiguration{apiKey=[REDACTED], apiUrl="
          + apiUrl
          + ", snapshot="
          + snapshot
          + ", autoStopMinutes="
          + autoStopMinutes
          + ", autoArchiveMinutes="
          + autoArchiveMinutes
          + "}";
    }
  }
}
