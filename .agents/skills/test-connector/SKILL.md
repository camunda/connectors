---
name: test-connector
description: Use when a connector needs to be tested. Verifies with a canary BPMN that the local connectors runtime is running with the Vault secrets, then deploys and runs the BPMN for the requested test.
---

# Test a connector with real secrets

Never ask for, read or print secret values. Never run `scripts/runtime-with-secrets.sh` yourself and never read the runtime's `/proc/<pid>/environ`. The developer starts the runtime.

## 1. Verify the setup with the canary

The only proof that the runtime has the secrets is a run that uses one, so deploy and run `canary.bpmn` (next to this file) first. Use `rtk proxy curl` if a hook rewrites curl output.

- Deploy: `curl -s -X POST localhost:8080/v2/deployments -F "resources=@.agents/skills/test-connector/canary.bpmn"`
- Start: `POST localhost:8080/v2/process-instances` with `{"processDefinitionId":"secret-canary"}`
- Wait about 10 seconds, then read the instance state and incidents from `/v2/process-instances/search` and `/v2/incidents/search`.

The canary calls the Beeceptor gate `https://camunda.proxy.beeceptor.com/verify-local-runtime-with-secrets` with `X-Api-Key: {{secrets.VERIFY_SETUP_VERIFY}}`. The gate returns 202 only for the right value, and Beeceptor answers 200 for everything else. The task's `errorExpression` raises `SECRET_GATE_REJECTED` unless the status is exactly 202, which becomes an incident.

**If the instance is `COMPLETED`**, say only "Setup is fine" (no instance key, no gate or secret details). Then continue with step 2.

**If it fails** (the engine or runtime is not reachable, the instance has an incident, or a secret is unresolved), tell the developer to start the IntelliJ run configuration **"LocalConnectorRuntime with secrets"** and approve the Vault login in the browser. Then ask them to tell you when the runtime is up, and run the canary again. Cancel the failed canary instance first: `POST /v2/process-instances/<key>/cancellation`. Add one hint if it fits the error:
- Engine unreachable or its partition unhealthy: the local Camunda distribution must be running without OIDC first.
- `SECRET_GATE_REJECTED`: the runtime lacks the right `VERIFY_SETUP_VERIFY`, or the Beeceptor gate rule is missing.

Gate setup, once, in the Beeceptor dashboard for `camunda`: a mock rule for `GET /verify-local-runtime-with-secrets` with header `X-Api-Key` equal to the value of `VERIFY_SETUP_VERIFY` returns 202. The developer sets the expected value there. Never ask for it.

## 2. Build the BPMN

- Use only names listed in `.available-secrets.md`, as `{{secrets.NAME}}`. If a needed secret is missing, tell the developer to add it to Vault and restart the runtime.
- Take the task type and input names from the connector's element template under `connectors/**/element-templates/`.
- Keep `resultExpression` to non-sensitive fields, like a status code, so responses that echo credentials are not stored.
- Save the file in the scratchpad. Copy it to `diagrams/` only if asked.
- SageMaker is billed per hour while its endpoint exists, so it is never left running. When SageMaker should be tested, tell the developer to start the endpoint manually and to stop it again afterwards. Do not create or delete endpoints yourself.
- Comprehend uses the `async` mode with a custom classifier that was trained on short product-review sentences with two labels, `POSITIVE` and `NEGATIVE`, for example `POSITIVE,Fantastic service and fast delivery` and `NEGATIVE,Terrible quality and hard to use`. Assert on one of those labels with clearly positive or negative review text, never on other topics. Input and output S3 locations must not overlap, so use `comprehend/input/` for input and `comprehend-output/` for output. The classifier and role ARNs are expected in the secrets list; if they are missing, tell the developer to add them to Vault.

## 3. Deploy and run

Deploy and start it the same way as the canary, then check state, incidents and variables with the `/v2/process-instances/search`, `/v2/incidents/search` and `/v2/variables/search` endpoints.

## 4. Report

Say whether the instance completed, any incident message, and the result variables. A variable holding the literal `{{secrets.NAME}}` is correct. Never print a resolved secret value.
