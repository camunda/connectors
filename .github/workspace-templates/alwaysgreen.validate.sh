#!/usr/bin/env bash
# Extra validation for a camunda/connectors AlwaysGreen patch, run by the shared
# fix agent (c8-cross-component-e2e-tests/.github/workflows/alwaysgreen-fix.yml)
# after its generic checks and BEFORE any publish token exists.
#
# Why this is not in the shared workflow: it asserts a property only this
# repository has. A `fix/alwaysgreen-` branch here triggers workflows that read
# Vault and check out the branch's own code, so publishing the branch is what
# would expose them. Each job below must therefore either withhold itself from an
# alwaysgreen branch or refuse to persist credentials. If that isolation is ever
# dropped, this fails the patch instead of pushing it.
#
# $1 — the workspace root; this repository is checked out at $1/connectors.
set -euo pipefail

ws_dir="$1"
repo_dir="${ws_dir}/connectors"
guard="startsWith(github.event.pull_request.head.ref || github.ref_name, 'fix/alwaysgreen-')"

# Only a connectors patch can expose connectors' own CI.
[ "${REPO_NAME}" = "connectors" ] || exit 0

job_contains() { # job, needle  (reads the workflow on stdin)
  awk -v job="$1" -v needle="$2" '
    $0 == "  " job ":" { in_job = 1; next }
    in_job && /^  [[:alnum:]_-]+:/ { exit }
    in_job && index($0, needle) { found = 1 }
    END { exit(found ? 0 : 1) }
  '
}

job_line_contains() { # job, first, second
  awk -v job="$1" -v first="$2" -v second="$3" '
    $0 == "  " job ":" { in_job = 1; next }
    in_job && /^  [[:alnum:]_-]+:/ { exit }
    in_job && index($0, first) && index($0, second) { found = 1 }
    END { exit(found ? 0 : 1) }
  '
}

jobs_containing() { # needle
  awk -v needle="$1" '
    /^  [[:alnum:]_-]+:$/ { job = $1; sub(/:$/, "", job) }
    job != "" && index($0, needle) && !seen[job]++ { print job }
  '
}

feature_workflow="$(git -C "$repo_dir" show HEAD:.github/workflows/TEST_FEATURE_BRANCH.yml)" || {
  echo "::error::${BASE_REF} does not contain the protected feature-branch workflow"
  exit 1
}

mapfile -t checkout_jobs < <(jobs_containing "uses: actions/checkout@" <<< "$feature_workflow")
for job in "${checkout_jobs[@]}"; do
  job_contains "$job" 'persist-credentials: false' <<< "$feature_workflow" || {
    echo "::error::${BASE_REF} persists checkout credentials in the ${job} job"
    exit 1
  }
  job_contains "$job" "runs-on: ubuntu-latest" <<< "$feature_workflow" \
    || job_contains "$job" "!${guard}" <<< "$feature_workflow" || {
      echo "::error::${BASE_REF} can run untrusted code on a privileged runner in the ${job} job"
      exit 1
    }
done

mapfile -t privileged_jobs < <(jobs_containing "uses: hashicorp/vault-action@" <<< "$feature_workflow")
for job in "${privileged_jobs[@]}"; do
  # shellcheck disable=SC2016  # these are literal workflow source lines to find
  for requirement in \
    "!${guard}" \
    'if [ "${IS_ALWAYSGREEN_BRANCH}" = "true" ]; then' \
    'echo "internal=false" >> $GITHUB_OUTPUT'; do
    job_contains "$job" "$requirement" <<< "$feature_workflow" || {
      echo "::error::${BASE_REF} does not isolate AlwaysGreen branches in the ${job} job"
      exit 1
    }
  done
  job_line_contains "$job" "IS_ALWAYSGREEN_BRANCH:" "$guard" <<< "$feature_workflow" || {
    echo "::error::${BASE_REF} does not classify AlwaysGreen branches in the ${job} job"
    exit 1
  }
done

if license_workflow="$(git -C "$repo_dir" show HEAD:.github/workflows/CHECK_LICENSES.yml 2>/dev/null)"; then
  mapfile -t license_jobs < <(jobs_containing "uses: hashicorp/vault-action@" <<< "$license_workflow")
  for job in "${license_jobs[@]}"; do
    for requirement in "!startsWith(github.head_ref, 'fix/alwaysgreen-')" 'persist-credentials: false'; do
      job_contains "$job" "$requirement" <<< "$license_workflow" || {
        echo "::error::${BASE_REF} does not isolate AlwaysGreen branches in the ${job} license job"
        exit 1
      }
    done
  done
fi

while IFS=: read -r workflow_path guarded_job has_checkout; do
  guarded_workflow="$(git -C "$repo_dir" show "HEAD:.github/workflows/${workflow_path}" 2>/dev/null)" || continue
  job_contains "$guarded_job" "!startsWith(github.head_ref, 'fix/alwaysgreen-')" <<< "$guarded_workflow" || {
    echo "::error::${BASE_REF} does not isolate AlwaysGreen branches in ${workflow_path}"
    exit 1
  }
  if [ "$has_checkout" = "true" ]; then
    job_contains "$guarded_job" 'persist-credentials: false' <<< "$guarded_workflow" || {
      echo "::error::${BASE_REF} persists credentials in ${workflow_path}"
      exit 1
    }
  fi
done <<'EOF'
BUILD_PR_DOCKER_IMAGES.yml:build-images:true
NIGHTLY_E2E.yml:setup:false
PREVIEW-ENV-DEPLOY.yml:deploy-preview:true
QA_REQUIRED.yml:process-qa-label:false
EOF

echo "connectors branch-isolation checks passed"
