# Preview Environments

[Preview Environments](https://confluence.camunda.com/display/SRE/Preview+Environments) are a powerful tool.

The Connectors runtime has its own custom preview environment that allows
engineers to preview runtime and connector features before merging them to `main`.

[This presentation](https://docs.google.com/presentation/d/1rpDoYDYeo5KRMJ55qhUR-ZHBT9hPdNtohJtEUZU3qiQ/edit#slide=id.g10eaae40b02_2_0) contains detailed information about the preview environment.

This guide will discuss some known issues and troubleshooting steps.

## Known Issues/Limitations

- Network calls can be unreliable
- The environment can become inaccessible at any time
- Any branch updates will trigger the (temporary) destruction of the preview environment

## Troubleshooting Steps

### Connecting to Zeebe from within the Web Modeler in the preview environment

Internal short aliases must be used for Zeebe (Gateway) and Keycloak.

- Cluster endpoint: `http://zeebe:26500/`
- Authentication: `OAuth`
- Client ID: `test`
- Client secret: `test`
- OAuth URL: `http://keycloak/auth/realms/camunda-platform/protocol/openid-connect/token`
- Audience: `zeebe-api`

### Unresponsive Preview Environment

1. Wait 5 minutes - often the environment recovers on its own, and this is faster than the following steps
2. Find the PR associated with your preview environment.
   If you're unsure, ask whoever provided the preview environment link.
3. Scroll down to workflow section of the PR
4. Click on "Details" of one of the deploy jobs
    ![Job-Details](https://github.com/camunda/team-connectors/assets/7648845/2c9c0c0d-3002-4e6f-b1c1-e6b78c2fcd44)
5. Click on "Re-run all jobs" in the top right
    ![Rerun-Jobs](https://github.com/camunda/team-connectors/assets/7648845/923134cf-826f-4de8-92b4-c8840e372831)
6. Wait 5-10 minutes until the jobs complete
7. If the jobs fail, repeat from step 4. It can take 2-3 retries until the preview environment launches as expected.

### Preview Environment Workflow/Jobs Fail

Start at step 3 in the "Unresponsive Preview Environment" section.

## Things to Avoid / Pitfalls

- ❌ Don't remove then re-add the label. This will take longer than re-running the jobs and [could lead to a race condition](https://camunda.slack.com/archives/C5AHF1D8T/p1705073575360779?thread_ts=1705069824.930629&cid=C5AHF1D8T)
- ❌ Don't message #ask-infra (unless you're certain) - although Infra initially sets up preview environments for each repo,
  the team that owns the repo is responsible for maintenance and future support
