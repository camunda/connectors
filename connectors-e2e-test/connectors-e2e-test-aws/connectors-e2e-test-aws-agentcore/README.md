# AWS AgentCore process tests

This module exercises the standalone AgentCore Long-Term Memory, Runtime, and Code Interpreter
connectors by deploying BPMN processes to Camunda Process Test and running the real connector
workers. The BPMN models are constructed in the test classes and, where applicable, the
published connector element templates are applied before deployment.

The regular `*Tests` suite uses a local AWS protocol test double with fixed responses, **not real
AWS**. No AWS account or resources are needed; its access and secret keys are dummy signing
credentials. These tests check process variables and expected incidents but cannot establish
that the live AWS integration works.

Run with Docker and `element-templates-cli` installed:

```sh
mvn -pl connectors-e2e-test/connectors-e2e-test-aws/connectors-e2e-test-aws-agentcore -am verify
```

The E2E branch workflow discovers this module automatically and executes its `verify` goal in
the test matrix. The nightly E2E workflow runs the branch workflow; for pull requests, apply the
`e2e-tests` label to trigger it.

## Live AWS CPT

`RealAgentCoreCptIT` uses the same provisioned resources and credential names as the existing
cross-component AgentCore smoke tests. It invokes the real AgentCore Runtime agent (which may
call an LLM), Long-Term Memory, and Code Interpreter. These are standalone connectors, not
interchangeable AI Agent chat-model providers, so the suite uses named scenarios rather than a
provider capability matrix. Its ten reported cases cover two-turn session reuse; Python,
JavaScript, and TypeScript execution separately; generated files; memory retrieval and
pagination; and a separate invalid-identifier incident for each connector. It requires
Docker, the element templates CLI, AWS permissions on these resources, and the following
nonblank environment variables for the selected connector:

- Runtime: `AGENTCORE_AWS_ACCESS_KEY`, `AGENTCORE_AWS_SECRET_KEY`,
  `AGENTCORE_AWS_REGION`, `AGENTCORE_AGENT_RUNTIME_ARN`.
- Memory: `AGENTCORE_LTM_AWS_ACCESS_KEY`, `AGENTCORE_LTM_AWS_SECRET_KEY`,
  `AGENTCORE_LTM_AWS_REGION`, `AGENTCORE_LTM_MEMORY_ID`, `AGENTCORE_LTM_NAMESPACE`.
- Interpreter: `BEDROCK_CI_AWS_ACCESS_KEY`, `BEDROCK_CI_AWS_SECRET_KEY`,
  `BEDROCK_CI_AWS_REGION`.

Memory content and pagination checks additionally need **two stable, distinct records** already
seeded in the configured namespace and four nonblank variables:
`AGENTCORE_LTM_EXPECTED_RECORD_ID`, `AGENTCORE_LTM_EXPECTED_RECORD_CONTENT`,
`AGENTCORE_LTM_EXPECTED_SECOND_RECORD_ID`, and
`AGENTCORE_LTM_EXPECTED_SECOND_RECORD_CONTENT`. The existing cross-component smoke test does
not seed or require records. These four values were not used by the existing CI wiring; their
presence in Vault and the existence of the matching seeded records have **not been verified**.
Selected live memory cases fail explicitly if the expected values are absent or the records
do not match. IAM must also allow listing and retrieving records in that memory. Do not infer
a passing real-provider run from the green test-double suite.

```sh
mvn -pl connectors-e2e-test/connectors-e2e-test-aws/connectors-e2e-test-aws-agentcore \
  -Pit-real-agentcore -Dit.test='RealAgentCoreCptIT' verify
```

To run a single scenario from the repository root, use Failsafe's method selector. For
example, the following runs the three individually reported language cases; replace the method
name with `runtimeReusesRealSessionAcrossTwoTurns`, `memoryRetrievesExpectedSeededRecord`,
`memoryListsTwoSeededRecordsAcrossPages`, `codeInterpreterReturnsGeneratedDocument`,
`invalidMemoryIdCreatesIncident`, `invalidRuntimeArnCreatesIncident`, or
`invalidCodeInterpreterIdCreatesIncident` to focus on another scenario:

```sh
mvn -pl connectors-e2e-test/connectors-e2e-test-aws/connectors-e2e-test-aws-agentcore \
  -Pit-real-agentcore -Dit.test='RealAgentCoreCptIT#codeInterpreterExecutesLanguage' verify
```

Build and install reactor dependencies first if they are not already present locally:

```sh
./mvnw -pl connectors-e2e-test/connectors-e2e-test-aws/connectors-e2e-test-aws-agentcore \
  -am install -DskipTests -DskipChecks
```

In an IDE, select the AgentCore E2E module's test
classpath, set that module as the working directory (template paths are relative to it),
enable the `it-real-agentcore` Maven profile or invoke Failsafe as above, and provide the
selected scenario's environment variables to the test process. Running the class as a plain
JUnit test also needs Docker and `element-templates-cli` available to that process. Running
without the profile through the regular `verify` goal only exercises the test-double suite.

For a same-repository PR touching AgentCore, applying the existing
`ai-agent-model-e2e-test` label triggers the credential-backed `AGENTCORE_CPT_PR.yml`
workflow. It checks the AgentCore path filter before fetching the AWS variables from the QA
CI Vault path and passes them only to the test step; forks are excluded. The workflow runs
the full live class, including memory fixture checks. It is opt-in on the **label event**,
not a required check on every push: remove and reapply the label to test a later commit.
Until the four expected memory values, seeded records, and IAM permissions are confirmed,
this live run cannot be considered verified. A green regular suite proves only the test-double
behavior, not real AWS or LLM access.
