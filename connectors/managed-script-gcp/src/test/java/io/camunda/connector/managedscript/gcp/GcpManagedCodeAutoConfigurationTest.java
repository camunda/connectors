/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.managedscript.gcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.IdTokenProvider;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class GcpManagedCodeAutoConfigurationTest {

  private static final String FUNCTION_SA = "executor@camunda-p.iam.gserviceaccount.com";
  private static final String BUILD_SA = "builder@camunda-p.iam.gserviceaccount.com";
  private static final String DEPLOYER_SA = "deployer@camunda-p.iam.gserviceaccount.com";

  /**
   * A workload identity federation configuration that reads its token from a file: loading it needs
   * neither the network nor the metadata server.
   */
  private static final String WORKLOAD_IDENTITY_FEDERATION =
      """
      {
        "type": "external_account",
        "audience": "//iam.googleapis.com/projects/123/locations/global/workloadIdentityPools/pool/providers/provider",
        "subject_token_type": "urn:ietf:params:oauth:token-type:jwt",
        "token_url": "https://sts.googleapis.com/v1/token",
        "credential_source": {"file": "/var/run/secrets/tokens/gcp-token"}
      }
      """;

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(GcpManagedCodeAutoConfiguration.class));

  private final ApplicationContextRunner configured =
      contextRunner.withPropertyValues(
          "camunda.connector.managed-code.enabled=true",
          "camunda.connector.managed-code.provider=gcp",
          "camunda.connector.managed-code.gcp.project-id=camunda-p",
          "camunda.connector.managed-code.gcp.region=europe-west1",
          "camunda.connector.managed-code.gcp.function-service-account=" + FUNCTION_SA,
          "camunda.connector.managed-code.gcp.build-service-account=" + BUILD_SA);

  private final ApplicationContextRunner gcp =
      configured.withBean(
          GcpClients.ApplicationDefault.class,
          () -> GcpManagedCodeAutoConfigurationTest::serviceAccount);

  @Test
  void registersTheProviderOnlyWhenManagedCodeIsEnabledWithIt() {
    gcp.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(GcpCloudRunManagedCodeProvider.class);
      assertThat(context.getBean(GcpCloudRunManagedCodeProvider.class).name()).isEqualTo("gcp");
    });
    gcp.withPropertyValues("camunda.connector.managed-code.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(GcpCloudRunManagedCodeProvider.class));
    gcp.withPropertyValues("camunda.connector.managed-code.provider=aws")
        .run(context -> assertThat(context).doesNotHaveBean(GcpCloudRunManagedCodeProvider.class));
  }

  @Test
  void hasDefaultsForTheMemoryAndTheMaxInstanceCount() {
    gcp.run(context -> {
      final var properties = context.getBean(GcpManagedCodeProperties.class);
      assertThat(properties.memoryMb()).isEqualTo(512);
      assertThat(properties.availableMemory()).isEqualTo("512Mi");
      assertThat(properties.maxInstanceCount()).isEqualTo(1000);
      assertThat(properties.deployerServiceAccount()).isNull();
      assertThat(properties.location()).isEqualTo("projects/camunda-p/locations/europe-west1");
    });
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"project-id", "region", "function-service-account", "build-service-account"})
  void failsStartupWithoutARequiredSetting(String setting) {
    gcp.withPropertyValues("camunda.connector.managed-code.gcp." + setting + "=")
        .run(context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining(setting + " must be set"));
  }

  @Test
  void failsStartupForAnExecutionTimeoutLongerThanACloudRunFunctionRuns() {
    gcp.withPropertyValues("camunda.connector.managed-code.execution-timeout=3596s")
        .run(context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("at most 3595 seconds"));
  }

  @Test
  void failsStartupWhenTheCredentialsCannotMintIdTokens() {
    configured
        .withBean(
            GcpClients.ApplicationDefault.class,
            () -> GcpManagedCodeAutoConfigurationTest::workloadIdentityFederation)
        .run(context ->
            assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessageContaining("deployer-service-account"));
  }

  @Test
  void impersonatesTheDeployerOverAWorkloadIdentityFederationFile() throws IOException {
    final var credentials = GcpClients.deployer(workloadIdentityFederation(), DEPLOYER_SA);

    assertThat(credentials)
        .isInstanceOfSatisfying(
            ImpersonatedCredentials.class,
            impersonated -> assertThat(impersonated.getAccount()).isEqualTo(DEPLOYER_SA))
        .isInstanceOf(IdTokenProvider.class);
  }

  @Test
  void refusesAUserLoginWithoutADeployer() {
    final var login =
        UserCredentials.newBuilder()
            .setClientId("client")
            .setClientSecret("secret")
            .setRefreshToken("refresh")
            .build();

    assertThatThrownBy(() -> GcpClients.deployer(login, null))
        .hasMessageContaining("deployer-service-account");
    assertThat(GcpClients.deployer(login, DEPLOYER_SA)).isInstanceOf(ImpersonatedCredentials.class);
  }

  @Test
  void usesTheApplicationDefaultCredentialsAsTheyAreWithoutADeployer() throws IOException {
    assertThat(GcpClients.deployer(serviceAccount(), null))
        .isInstanceOf(ServiceAccountCredentials.class);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 127, 32769})
  void rejectsAMemoryOutsideTheRange(int memoryMb) {
    assertThatThrownBy(() ->
            new GcpManagedCodeProperties("p", "r", FUNCTION_SA, BUILD_SA, null, memoryMb, null))
        .hasMessageContaining("memory-mb " + memoryMb + " must be between 128 and 32768");
  }

  @Test
  void rejectsANonPositiveMaxInstanceCount() {
    assertThatThrownBy(
            () -> new GcpManagedCodeProperties("p", "r", FUNCTION_SA, BUILD_SA, null, null, 0))
        .hasMessageContaining("max-instance-count 0 must be positive");
  }

  @Test
  void rejectsAFunctionServiceAccountThatIsNotAnEmail() {
    assertThatThrownBy(
            () -> new GcpManagedCodeProperties("p", "r", "executor", BUILD_SA, null, null, null))
        .hasMessageContaining("must be the email of a service account");
  }

  @Test
  void readsTheMemoryThatTheApiReportsInBytes() {
    assertThat(GcpManagedCodeProperties.memoryBytes("512Mi")).isEqualTo(512L * 1024 * 1024);
    assertThat(GcpManagedCodeProperties.memoryBytes("536870912")).isEqualTo(512L * 1024 * 1024);
    assertThat(GcpManagedCodeProperties.memoryBytes("1Gi")).isEqualTo(1024L * 1024 * 1024);
    assertThat(GcpManagedCodeProperties.memoryBytes("512M")).isEqualTo(512_000_000L);
    assertThat(GcpManagedCodeProperties.memoryBytes("")).isEqualTo(-1);
    assertThat(GcpManagedCodeProperties.memoryBytes("0.5Gi")).isEqualTo(-1);
  }

  private static GoogleCredentials serviceAccount() {
    try {
      final var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return ServiceAccountCredentials.newBuilder()
          .setClientEmail("runtime@camunda-p.iam.gserviceaccount.com")
          .setPrivateKey(generator.generateKeyPair().getPrivate())
          .setPrivateKeyId("key")
          .setProjectId("camunda-p")
          .build();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static GoogleCredentials workloadIdentityFederation() throws IOException {
    return GoogleCredentials.fromStream(
        new ByteArrayInputStream(WORKLOAD_IDENTITY_FEDERATION.getBytes(StandardCharsets.UTF_8)));
  }
}
