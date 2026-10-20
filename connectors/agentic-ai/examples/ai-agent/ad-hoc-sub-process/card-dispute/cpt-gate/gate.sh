#!/usr/bin/env bash
set -euo pipefail
set +x
cd "$(dirname "$0")"

SOURCE="${SOURCE_CLUSTER:-$(cd .. && pwd)/source-cluster}"
IMAGE=camunda/camunda:8.11.0-proto-source
mode="${1:-all}"
case "$mode" in
  all|--deterministic-only) [[ $# -le 1 ]] || exit 2 ;;
  --e2e|--check-proof) [[ $# -eq 1 ]] || exit 2 ;;
  --live-only)
    if [[ $# -ne 2 || "$2" != L2 ]]; then
      echo "Usage: ./gate.sh --live-only L2" >&2
      exit 2
    fi
    ;;
  --help)
    echo "Usage: ./gate.sh [--deterministic-only | --live-only L2 | --e2e | --check-proof]"
    exit 0
    ;;
  *) echo "Usage: ./gate.sh [--deterministic-only | --live-only L2 | --e2e]" >&2; exit 2 ;;
esac
mkdir -p target src/test/resources proofs
# Serialize runs: policy latest-binding and the single real A2A budget are run-scoped.
if ! mkdir proofs/gate.lock 2>/dev/null; then
  echo "FAIL: another gate owns proofs/gate.lock (remove only after confirming no gate is running)" >&2
  exit 1
fi
trap 'rmdir proofs/gate.lock' EXIT
python3 -B -m unittest -q test_gate_report.py test_daytona_proof.py test_skill_proof.py test_skill_history.py test_tone_proof.py

if [[ "$mode" == --check-proof ]]; then
  python3 daytona_proof.py check
  exit $?
fi

check_disk() {
  python3 -c 'import shutil,sys; free=shutil.disk_usage(".").free; print(f"Free disk: {free / 2**30:.2f} GiB (minimum 15 GiB)"); sys.exit(0 if free >= 15 * 2**30 else 1)'
}

if [[ "$mode" == --e2e ]]; then
  python3 gate_report.py verify "$SOURCE"
  python3 gate_report.py eligible
  python3 gate_report.py eligible-e2e
  check_disk
  python3 gate_report.py reset-e2e
  python3 daytona_proof.py begin
  e2e=0
  mvn --batch-mode --no-transfer-progress test -Plive '-Dtest=LiveTest#l1+webSearchProbe' \
    -Dgate.realDaytona=true -Dgate.reports=target/surefire-e2e \
    >target/e2e.log 2>&1 || e2e=$?
  echo "$e2e" >target/e2e.exit
  if [[ "$e2e" == 0 ]]; then
    python3 daytona_proof.py commit || echo "Real-Daytona proof commit failed; previous proof preserved"
  fi
  python3 daytona_proof.py check || true
  python3 gate_report.py summary "$(cat target/deterministic.exit)" "$(cat target/live.exit)" | tee target/summary.txt
  exit 0
fi

if [[ "$mode" == --live-only ]]; then
  # Reuse evidence only for the exact snapshot that passed deterministic checks.
  python3 gate_report.py verify "$SOURCE"
  python3 gate_report.py eligible
  [[ -f target/deterministic.exit ]] || { echo "FAIL: no deterministic run exit record" >&2; exit 1; }
  python3 gate_report.py reset-l2
  check_disk
  live=0
  mvn --batch-mode --no-transfer-progress test -Plive -Dtest=LiveTest#l2 \
    -Dgate.reports=target/surefire-live-l2 >target/live-l2.log 2>&1 || live=$?
  echo "$live" >target/live.exit
  python3 daytona_proof.py check || true
  python3 gate_report.py summary "$(cat target/deterministic.exit)" "$live" | tee target/summary.txt
  exit 0
fi

python3 gate_report.py sync "$SOURCE"
check_disk

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  mkdir -p target/engine-build/dist
  cp -R "$SOURCE/camunda/dist/target/camunda-zeebe/bin" target/engine-build/dist/
  cp -R "$SOURCE/camunda/dist/target/camunda-zeebe/lib" target/engine-build/dist/
  cp -R "$SOURCE/camunda/dist/target/camunda-zeebe/config" target/engine-build/dist/
  cp engine.Dockerfile target/engine-build/Dockerfile
  if ! docker build -t "$IMAGE" target/engine-build >target/engine-build.log 2>&1; then
    echo "FAIL: source-built engine image unavailable; see target/engine-build.log" >&2
    exit 1
  fi
fi
docker image inspect "$IMAGE" --format 'Engine image: {{.Id}}' | tee target/engine-image.txt

deterministic=0
mvn --batch-mode --no-transfer-progress test -Pdeterministic >target/deterministic.log 2>&1 || deterministic=$?
# Modeler parity: what Camunda Desktop Modeler 5.51.1 shows for the snapshot. Writes a JUnit report
# next to the Maven ones; a crash leaves no report, which the reporter treats as FAIL (not executed).
parity=0
(
  cd modeler-parity
  npm ci --prefer-offline --no-audit --no-fund --silent
  npm test --silent
  node dist/parity.mjs --bpmn ../src/test/resources/demo/card-dispute.bpmn \
    --local ../src/test/resources/demo/.camunda/element-templates \
    --source-demo "$SOURCE/demo" \
    --bundle bundle/camunda-modeler-5.51.1/connector-templates.json \
    --reference reference/linked-prompts-agent.element-template.json \
    --junit ../target/surefire-deterministic/TEST-modeler.ModelerParity.xml \
    --json ../target/modeler-parity.json
) >target/modeler-parity.log 2>&1 || parity=$?
sed -n '/^Modeler parity (/,$p' target/modeler-parity.log
[[ "$parity" == 0 ]] || { echo "Modeler parity step exited $parity; see target/modeler-parity.log"; deterministic=1; }
echo "$deterministic" >target/deterministic.exit
echo "Deterministic HTML: $(pwd)/target/coverage-report/report.html"
echo "Deterministic log: $(pwd)/target/deterministic.log"
live=1
# Every deterministic check is required before spending live traffic.
if [[ "$mode" != --deterministic-only ]] && python3 gate_report.py eligible; then
  live=0
  check_disk
  python3 tone_proof.py begin
  mvn --batch-mode --no-transfer-progress test -Plive >target/live.log 2>&1 || live=$?
else
  echo "Live tier not run: deterministic-only requested or deterministic checks failed"
fi
echo "$live" >target/live.exit
if [[ "$mode" != --deterministic-only ]]; then
  python3 daytona_proof.py check || true
fi
python3 gate_report.py summary "$deterministic" "$live" | tee target/summary.txt
