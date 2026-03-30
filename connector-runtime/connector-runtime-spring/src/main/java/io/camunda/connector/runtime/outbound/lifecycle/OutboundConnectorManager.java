/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.outbound.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.annotation.value.JobWorkerValue;
import io.camunda.client.annotation.value.SourceAware;
import io.camunda.client.annotation.value.SourceAware.FromAnnotation;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.client.jobhandling.CommandExceptionHandlingStrategy;
import io.camunda.client.jobhandling.JobHandlerFactory;
import io.camunda.client.jobhandling.JobWorkerManager;
import io.camunda.client.jobhandling.ManagedJobWorker;
import io.camunda.client.lifecycle.CamundaClientLifecycleAware;
import io.camunda.client.metrics.MetricsRecorder;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.validation.ValidationProvider;
import io.camunda.connector.runtime.core.config.OutboundConnectorConfiguration;
import io.camunda.connector.runtime.core.outbound.OutboundConnectorFactory;
import io.camunda.connector.runtime.core.secret.SecretProviderAggregator;
import io.camunda.connector.runtime.outbound.job.SpringConnectorJobHandler;
import io.camunda.zeebe.model.bpmn.Bpmn;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OutboundConnectorManager implements CamundaClientLifecycleAware {

  private static final Logger LOG = LoggerFactory.getLogger(OutboundConnectorManager.class);

  /** Phases used by the job-worker dashboard simulation. */
  private enum SimPhase {
    /**
     * Workers receive jobs but intentionally do nothing — locks expire, jobs re-queue, backlog
     * grows.
     */
    NEVER_COMPLETE,
    /** Workers complete every received job immediately (happy path). */
    COMPLETE,
    /** Workers fail every received job with 0 retries (incident / error investigation path). */
    FAIL
  }

  private static final int SIM_JOB_TYPE_COUNT = 10;
  private static final int SIM_WORKERS_PER_TYPE = 2;
  private static final long SIM_PHASE_DURATION_MINUTES = 1;
  private static final long SIM_INSTANCE_CREATION_INTERVAL_SECONDS = 3;

  private final List<JobWorker> simulationWorkers = new ArrayList<>();
  private ScheduledExecutorService simulationScheduler;

  private final JobWorkerManager jobWorkerManager;
  private final OutboundConnectorFactory connectorFactory;
  private final CommandExceptionHandlingStrategy commandExceptionHandlingStrategy;
  private final SecretProviderAggregator secretProviderAggregator;
  private final ValidationProvider validationProvider;
  private final ObjectMapper objectMapper;
  private final DocumentFactory documentFactory;
  private final MetricsRecorder metricsRecorder;

  public OutboundConnectorManager(
      JobWorkerManager jobWorkerManager,
      OutboundConnectorFactory connectorFactory,
      CommandExceptionHandlingStrategy commandExceptionHandlingStrategy,
      SecretProviderAggregator secretProviderAggregator,
      ValidationProvider validationProvider,
      DocumentFactory documentFactory,
      ObjectMapper objectMapper,
      MetricsRecorder metricsRecorder) {
    this.jobWorkerManager = jobWorkerManager;
    this.connectorFactory = connectorFactory;
    this.commandExceptionHandlingStrategy = commandExceptionHandlingStrategy;
    this.secretProviderAggregator = secretProviderAggregator;
    this.validationProvider = validationProvider;
    this.documentFactory = documentFactory;
    this.objectMapper = objectMapper;
    this.metricsRecorder = metricsRecorder;
  }

  @Override
  public void onStart(final CamundaClient client) {
    // Currently, existing Spring beans have a higher priority
    // One result is that you will not disable Spring Bean Connectors by providing environment
    // variables for a specific connector
    Set<OutboundConnectorConfiguration> outboundConnectors =
        new TreeSet<>(new OutboundConnectorConfigurationComparator());
    outboundConnectors.addAll(connectorFactory.getConfigurations());
    outboundConnectors.forEach(connector -> openWorkerForOutboundConnector(client, connector));

    startJobWorkerDashboardSimulation(client);
  }

  @Override
  public void onStop(CamundaClient client) {
    jobWorkerManager.closeAllJobWorkers(this);
    simulationWorkers.forEach(JobWorker::close);
    simulationWorkers.clear();
    if (simulationScheduler != null) {
      simulationScheduler.shutdownNow();
      simulationScheduler = null;
    }
  }

  /**
   * Simulates three job-worker behaviour scenarios useful for demoing the job-worker dashboard:
   *
   * <ol>
   *   <li><b>Phase 1 – Never complete</b> ({@value SIM_PHASE_DURATION_MINUTES} min): 20 workers
   *       registered across 10 job types continuously receive jobs but never complete them. Job
   *       locks expire and jobs are re-queued, creating an ever-growing backlog.
   *   <li><b>Phase 2 – Normal (complete)</b> ({@value SIM_PHASE_DURATION_MINUTES} min): Same
   *       workers immediately complete every received job — the healthy happy path.
   *   <li><b>Phase 3 – Failing</b> ({@value SIM_PHASE_DURATION_MINUTES} min): Workers alternate
   *       between failing jobs with 0 retries (incident) and throwing a BPMN error (errorCode) to
   *       exercise both error paths visible on the dashboard.
   * </ol>
   *
   * Process instances are created every {@value SIM_INSTANCE_CREATION_INTERVAL_SECONDS} seconds for
   * each of the {@value SIM_JOB_TYPE_COUNT} job types throughout all phases.
   */
  private void startJobWorkerDashboardSimulation(CamundaClient client) {
    var jobTypes =
        IntStream.rangeClosed(1, SIM_JOB_TYPE_COUNT).mapToObj(i -> "sim-job-type-" + i).toList();

    // Deploy a minimal one-task process for each job type
    jobTypes.forEach(jobType -> deploySimulationProcess(client, jobType));

    var currentPhase = new AtomicReference<>(SimPhase.NEVER_COMPLETE);
    LOG.info(
        "[Simulation] Phase 1 started – {} workers registered, jobs will NOT be completed (backlog demo)",
        SIM_JOB_TYPE_COUNT * SIM_WORKERS_PER_TYPE);

    // Register SIM_WORKERS_PER_TYPE workers per type = SIM_JOB_TYPE_COUNT * SIM_WORKERS_PER_TYPE
    // total
    for (String jobType : jobTypes) {
      for (int w = 1; w <= SIM_WORKERS_PER_TYPE; w++) {
        final String workerName = jobType + "-worker-" + w;
        JobWorker worker =
            client
                .newWorker()
                .jobType(jobType)
                .handler(
                    (jobClient, job) -> {
                      switch (currentPhase.get()) {
                        case NEVER_COMPLETE ->
                            // Intentionally do nothing – lock expires, job is re-queued
                            LOG.trace(
                                "[Simulation] {} received job {} — not completing (phase 1)",
                                workerName,
                                job.getKey());
                        case COMPLETE -> {
                          LOG.trace(
                              "[Simulation] {} completing job {} (phase 2)",
                              workerName,
                              job.getKey());
                          jobClient.newCompleteCommand(job).send();
                        }
                        case FAIL -> {
                          if (job.getKey() % 2 == 0) {
                            LOG.trace(
                                "[Simulation] {} failing job {} (phase 3 – fail)",
                                workerName,
                                job.getKey());
                            jobClient
                                .newFailCommand(job)
                                .retries(0)
                                .errorMessage("Simulated failure – dashboard demo (phase 3)")
                                .send();
                          } else {
                            LOG.trace(
                                "[Simulation] {} throwing BPMN error for job {} (phase 3 – throwError)",
                                workerName,
                                job.getKey());
                            jobClient
                                .newThrowErrorCommand(job)
                                .errorCode("SIM_ERROR")
                                .errorMessage("Simulated BPMN error – dashboard demo (phase 3)")
                                .send();
                          }
                        }
                      }
                    })
                .name(workerName)
                .open();
        simulationWorkers.add(worker);
      }
    }
    LOG.info(
        "[Simulation] {} workers opened for {} job types",
        simulationWorkers.size(),
        jobTypes.size());

    simulationScheduler = Executors.newScheduledThreadPool(2);

    // Continuously create process instances at a steady rate across all job types
    simulationScheduler.scheduleAtFixedRate(
        () ->
            jobTypes.forEach(
                jobType -> {
                  try {
                    client
                        .newCreateInstanceCommand()
                        .bpmnProcessId("sim-process-" + jobType)
                        .latestVersion()
                        .send();
                  } catch (Exception e) {
                    LOG.warn(
                        "[Simulation] Failed to create instance for {}: {}",
                        jobType,
                        e.getMessage());
                  }
                }),
        2,
        SIM_INSTANCE_CREATION_INTERVAL_SECONDS,
        TimeUnit.SECONDS);

    // Phase 2: complete jobs
    simulationScheduler.schedule(
        () -> {
          LOG.info("[Simulation] Phase 2 started – workers will now COMPLETE every job");
          currentPhase.set(SimPhase.COMPLETE);
        },
        SIM_PHASE_DURATION_MINUTES,
        TimeUnit.MINUTES);

    // Phase 3: fail jobs
    simulationScheduler.schedule(
        () -> {
          LOG.info(
              "[Simulation] Phase 3 started – workers will alternately FAIL (0 retries) or THROW a BPMN error (SIM_ERROR)");
          currentPhase.set(SimPhase.FAIL);
        },
        SIM_PHASE_DURATION_MINUTES * 2,
        TimeUnit.MINUTES);

    // End: close simulation workers and scheduler
    simulationScheduler.schedule(
        () -> {
          LOG.info("[Simulation] All 3 phases complete – closing simulation workers");
          simulationWorkers.forEach(JobWorker::close);
          simulationWorkers.clear();
          simulationScheduler.shutdown();
        },
        SIM_PHASE_DURATION_MINUTES * 3,
        TimeUnit.MINUTES);
  }

  /**
   * Deploys a minimal single-service-task BPMN process for the given job type.
   *
   * <p>Process ID: {@code sim-process-<jobType>}
   */
  private void deploySimulationProcess(CamundaClient client, String jobType) {
    String processId = "sim-process-" + jobType;
    try {
      client
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(processId)
                  .name(processId)
                  .startEvent()
                  .serviceTask(jobType)
                  .name(jobType)
                  .zeebeJobType(jobType)
                  .endEvent()
                  .done(),
              processId + ".bpmn")
          .send()
          .join();
      LOG.info("[Simulation] Deployed process '{}' for job type '{}'", processId, jobType);
    } catch (Exception e) {
      LOG.error(
          "[Simulation] Failed to deploy process for job type '{}': {}", jobType, e.getMessage());
    }
  }

  private void openWorkerForOutboundConnector(
      CamundaClient client, OutboundConnectorConfiguration connector) {
    JobWorkerValue jobWorkerValue = new JobWorkerValue();
    jobWorkerValue.setName(new FromAnnotation<>(connector.name()));
    jobWorkerValue.setType(new FromAnnotation<>(connector.type()));
    jobWorkerValue.setFetchVariables(
        Arrays.stream(connector.inputVariables())
            .map(FromAnnotation::new)
            .map(fa -> (SourceAware<String>) fa)
            .toList());

    if (connector.timeout() != null) {
      jobWorkerValue.setTimeout(new FromAnnotation<>(Duration.ofMillis(connector.timeout())));
    }

    OutboundConnectorFunction connectorFunction = connectorFactory.getInstance(connector.type());
    LOG.trace("Opening worker for connector {}", connector.name());

    JobHandlerFactory jobHandlerFactory =
        ctx ->
            new SpringConnectorJobHandler(
                metricsRecorder,
                commandExceptionHandlingStrategy,
                secretProviderAggregator,
                validationProvider,
                documentFactory,
                objectMapper,
                connectorFunction);
    jobWorkerManager.createJobWorker(
        client, new ManagedJobWorker(jobWorkerValue, jobHandlerFactory), this);
  }
}
