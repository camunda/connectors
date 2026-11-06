The run deploys a freshly built connectors image, so a runtime or bundle
regression surfaces as a test failure. A Playwright job failing does not mean
the test is wrong — the manual's routing section is the full rule.

The SM specs execute from the published @camunda/e2e-test-suite npm package, so
a report's 'file' is a compiled basename. The source lives in
c8-cross-component-e2e-tests under tests/SM-8.x/ and tests/8.x/; find it by
basename rather than by the reported path.

Commit/PR type: 'test:' for test-only changes, 'ci:' for workflow changes,
'fix:' for connector, runtime or bundle code.

Connectors fixes target the dispatched base_ref (including stable/8.x);
c8-cross-component-e2e-tests fixes target main. The shared workflow publishes the PR.
camunda-docs is read-only reference material: diagnose from it, never open a PR
against it. camunda-platform-helm is read-only too, with one exception: for the
helm-report-merge and ci-infra surfaces you may fix a CI step in its shared
reusable workflows under .github/workflows/ (e.g. the Merge E2E Reports job in
test-integration-runner.yaml), targeting main. Chart, values and Keycloak root
causes are still escalated, never changed. The manual's "Where fixes go" section
has the rule and the CI hardening checklist.
