#!/usr/bin/env python3
"""Snapshot inputs and fail closed on scenario results and runtime coverage."""
import hashlib
import json
import re
from pathlib import Path
import shutil
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
TARGET = ROOT / "target"
EXPECTED = {
    "demo.StructuralTest": [
        "linkedPrompt", "documents", "sandbox", "webFetch", "authenticatedA2a",
        "credentialTemplate", "fraudUrl", "llmSettings", "webSearchOff", "sandboxFallbackWiring",
        "documentParsing", "customMemory",
    ],
    "demo.DeterministicTest": ["d1", "d2", "d4"],
    "demo.SandboxFailureCandidateTest": [
        "createCleanFailure", "createSeededFailure", "bashCleanFailure", "bashSeededFailure",
        "cleanSuccessPropagatesCreateThenBash", "seededSuccessPropagatesCreateThenBash",
    ],
    "demo.SkillInputsTest": [
        "fiveDocumentInputsUseOriginalHashesAndExplicitFilingDate",
        "missingOrChangedBytesFailBeforeUploads", "uploadIdentityAndConcurrentByteChangeFailClosed",
    ],
    "demo.MemoryEvidenceTest": [
        "ownedSnapshotRoundtripDocumentsAndReviseAreVerified",
        "arbitraryOriginsAndUnownedSnapshotPathsAreRejected", "missingLedgerDocumentsOrArchiveContentFail",
        "persistedOwnedContextSnapshotHasDistinctProvenanceFromLaggingHistory",
    ],
    "demo.ResourceConsistencyTest": ["deploymentKeyReadWaitsOnlyForNotFoundAndChecksExactBytes"],
    "demo.FraudEndpointTest": ["stubPushesCompletedWithoutExternalCalls"],
    "demo.CredentialSafetyTest": ["referencesAreAcceptedAndRawKeysRejectedWithoutPrintingThem"],
    "demo.DaytonaEndpointTest": [
        "sdkUsesOnlyLocalMockForCreateFilesAndExecution",
        "sandboxFailureIsExplicitAndRejectsNonDummyCredentials",
        "syntheticStdoutMatchesOriginalLocalScriptSerializationNotRemoteProof",
    ],
    "demo.WebSearchProbeTest": [
        "onlyExactRegionalRejectionIsEligible", "isolatedProbeEnablesWebSearchUsingOnlyTheSavedCredential",
    ],
    "demo.SandboxCleanupTest": [
        "budgetFailureStillDeletesEveryOwnedSandboxAndVerifiesAbsence",
        "foreignSandboxIsNeverDeleted", "failedDeletionDoesNotSkipOtherOwnedSandboxes",
    ],
    "demo.RealDaytonaEvidenceTest": [
        "importedCsvIsJoinedByDocumentIdWhenToolReferenceOmitsMetadata", "failedImportCannotCertifyTheUploadedCsv",
        "missingImportIdentityNamesTheExpectedFieldAndSource",
    ],
    "modeler.ModelerParity": [
        "templatesResolve", "lintClean", "linkedPrompt75", "serverTools83", "savedCredential86",
    ],
    "demo.LiveTest": ["l1", "l2", "webSearchProbe"],
}
WEB_SEARCH_NA = "N/A: not available on EU account"
DEMO_FOLDERS = ("data", "policies", "rules", "scripts", "skills", "variants", ".camunda")


def sync(source):
    destination = ROOT / "src/test/resources/demo"
    if destination.exists():
        shutil.rmtree(destination)
    destination.mkdir(parents=True)
    files = ["card-dispute.bpmn", "review-dispute.form", "rehearse.sh"]
    for folder in DEMO_FOLDERS:
        files += [str(p.relative_to(source / "demo")) for p in (source / "demo" / folder).rglob("*") if p.is_file()]
    hashes = {}
    for relative in sorted(files):
        src, dst = source / "demo" / relative, destination / relative
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, dst)
        hashes[relative] = hashlib.sha256(dst.read_bytes()).hexdigest()
        if hashes[relative] != hashlib.sha256(src.read_bytes()).hexdigest():
            raise RuntimeError("Source changed during snapshot: " + relative)
    template = source / "connectors/connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-subprocess.v2.json"
    shutil.copyfile(template, ROOT / "src/test/resources/agent-template.json")
    hashes["agent-template.json"] = hashlib.sha256(template.read_bytes()).hexdigest()
    (TARGET / "input-sha256.json").write_text(json.dumps(hashes, indent=2) + "\n")
    (TARGET / "input-source.txt").write_text(str(source.resolve()) + "\n")
    print("Model SHA256:", hashes["card-dispute.bpmn"])
    print("Snapshot manifest SHA256:", hashlib.sha256((TARGET / "input-sha256.json").read_bytes()).hexdigest())
    # Stale reports must never count as evidence of this run.
    for directory in ("surefire-deterministic", "surefire-live", "surefire-live-l2", "surefire-e2e", "coverage-report", "live-coverage-report"):
        path = TARGET / directory
        if path.exists():
            shutil.rmtree(path)
    for name in ("live-l2-attempted", "e2e-attempted", "e2e.exit", "deterministic.exit", "live.exit", "summary.txt",
                 "modeler-parity.json", "modeler-parity.log", "web-search-probe.json", "real-daytona-check.json",
                 "real-daytona-before.json", "real-daytona-execution.json", "real-daytona-handles.json"):
        (TARGET / name).unlink(missing_ok=True)
    for name in ("tone-run.json", "tone-cold.json", "tone-warm.json"):
        (TARGET / name).unlink(missing_ok=True)


def verify(source):
    hashes = json.loads((TARGET / "input-sha256.json").read_text())
    names = {"card-dispute.bpmn", "review-dispute.form", "rehearse.sh", "agent-template.json"}
    for folder in DEMO_FOLDERS:
        names.update(str(p.relative_to(source / "demo")) for p in (source / "demo" / folder).rglob("*") if p.is_file())
    if names != set(hashes):
        raise ValueError("Snapshot file set changed; run deterministic tier on the new source")
    for name, digest in hashes.items():
        src = source / "demo" / name
        copied = ROOT / "src/test/resources/demo" / name
        if name == "agent-template.json":
            src = source / "connectors/connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-subprocess.v2.json"
            copied = ROOT / "src/test/resources/agent-template.json"
        if any(hashlib.sha256(p.read_bytes()).hexdigest() != digest for p in (src, copied)):
            raise ValueError("Source or snapshot drift: " + name + "; run deterministic tier on the new source")
    print("Verified unchanged snapshot SHA256:", hashes["card-dispute.bpmn"])
    print("Snapshot manifest SHA256:", hashlib.sha256((TARGET / "input-sha256.json").read_bytes()).hexdigest())


def results(tier):
    cases = {}
    for report in (TARGET / ("surefire-" + tier)).glob("TEST-*.xml"):
        for case in ET.parse(report).getroot().iter("testcase"):
            state = "PASS"
            if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                state = "FAIL"
            key = (case.get("classname"), case.get("name"))
            # A duplicate report for the same case can only make it worse, never green.
            if cases.get(key, "PASS") == "PASS":
                cases[key] = state
    if tier == "live" and (TARGET / "live-l2-attempted").exists():
        # A failed/discovery-less retry must override older successful evidence too.
        key = ("demo.LiveTest", "l2")
        cases[key] = "FAIL (not executed)"
        for report in (TARGET / "surefire-live-l2").glob("TEST-*.xml"):
            for case in ET.parse(report).getroot().iter("testcase"):
                if (case.get("classname"), case.get("name")) == key:
                    cases[key] = "FAIL" if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")) else "PASS"
    return cases


def failure_reasons(tier, cls):
    reasons = {}
    for report in (TARGET / ("surefire-" + tier)).glob("TEST-*.xml"):
        for case in ET.parse(report).getroot().iter("testcase"):
            failure = case.find("failure")
            if case.get("classname") == cls and failure is not None:
                reasons[case.get("name")] = (failure.text or failure.get("message") or "").strip()
    return reasons


def coverage(path=None, model=None):
    path = path or TARGET / "coverage-report/report.html"
    html = path.read_text()
    marker = "window.COVERAGE_DATA"
    start = html.index("{", html.index("=", html.index(marker)))
    data, _ = json.JSONDecoder().raw_decode(html[start:])
    runs = {}
    for suite in data["suites"]:
        for run in suite["runs"]:
            visited = set()
            for item in run.get("coverages", run.get("processCoverages", [])):
                if item.get("processDefinitionId") == "CardDispute":
                    visited.update(item.get("completedElements", []))
                    visited.update(item.get("takenSequenceFlows", []))
            runs[run.get("runName", run.get("name", run.get("displayName", "?")))] = visited
    ns = "{http://www.omg.org/spec/BPMN/20100524/MODEL}"
    tree = ET.parse(model or ROOT / "src/test/resources/demo/card-dispute.bpmn")
    kinds = ("startEvent", "endEvent", "exclusiveGateway", "eventBasedGateway", "adHocSubProcess", "subProcess",
             "serviceTask", "scriptTask", "userTask", "intermediateCatchEvent", "boundaryEvent",
             "sequenceFlow")
    expected = {node.get("id") for node in tree.iter() if node.tag in {ns + kind for kind in kinds}}
    models = [model for model in data["processModels"] if model["processDefinitionId"] == "CardDispute"]
    if len(models) != 1 or models[0]["totalElementCount"] != len(expected):
        raise ValueError("CPT model total differs from enumerated flow nodes/flows; update the coverage parser")
    visited = set().union(*runs.values()) if runs else set()
    covered = expected & visited
    pct = 100 * len(covered) / len(expected)
    print(f"Coverage: {pct:.2f}% ({len(covered)}/{len(expected)}); HTML: {path}")
    print("Uncovered:", ", ".join(sorted(expected - covered)) or "none")
    redundancy_ok = set(runs) == set(EXPECTED["demo.DeterministicTest"] + EXPECTED["demo.SandboxFailureCandidateTest"])
    for name, own in runs.items():
        other = set().union(*(v for key, v in runs.items() if key != name))
        lost = (own & expected) - other
        override = name == "d2" or name in EXPECTED["demo.SandboxFailureCandidateTest"]
        print(f"Leave-one-out {name}: loses {len(lost)} ids" +
              ("; retained explicit tone-demo/propagation-regression override" if override and not lost else ""))
        if not lost and not override:
            redundancy_ok = False
    return covered == expected and redundancy_ok


def eligible():
    if not token_scan():
        print("FAIL token-leak scan (matches withheld)")
        return False
    cases = results("deterministic")
    for key, state in cases.items():
        if state != "PASS":
            return False
    for cls in (name for name in EXPECTED if name != "demo.LiveTest"):
        for name in EXPECTED[cls]:
            if cases.get((cls, name)) != "PASS":
                return False
    try:
        return coverage()
    except (OSError, ValueError, KeyError, TypeError) as error:
        print("FAIL coverage:", error)
        return False


def web_search_result():
    state = results("live").get(("demo.LiveTest", "webSearchProbe"))
    try:
        probe = json.loads((TARGET / "web-search-probe.json").read_text())
    except (OSError, ValueError):
        return "FAIL (probe evidence missing or invalid)"
    if not isinstance(probe, dict) or probe.get("processId") != "CptGateWebSearchProbe" or not probe.get("processInstanceKey"):
        return "FAIL (probe identity missing or invalid)"
    outcome = probe.get("outcome")
    if outcome == "REGIONAL_UNAVAILABLE":
        return WEB_SEARCH_NA if state == "PASS" else "FAIL (probe missing or failed)"
    if outcome == "AVAILABLE":
        return "FAIL (web search now available; enable it in the model)"
    return "FAIL (non-regional web-search error)"


def summary(deterministic, live):
    success = deterministic == "0" and live == "0"
    source_record = TARGET / "input-source.txt"
    if source_record.exists():
        try:
            verify(Path(source_record.read_text().strip()))
        except (OSError, ValueError, KeyError) as error:
            print("FAIL input snapshot:", error)
            print("Results below describe the copied snapshot, not the changed source.")
            success = False
    print("\n| Scenario/check | Result |")
    print("|---|---|")
    failing = []
    for cls, names in EXPECTED.items():
        cases = results("live" if cls == "demo.LiveTest" else "deterministic")
        for name in names:
            state = web_search_result() if (cls, name) == ("demo.LiveTest", "webSearchProbe") else cases.get((cls, name), "FAIL (not executed)")
            print(f"| {cls.rsplit('.', 1)[-1]}.{name} | {state} |")
            acceptable = state in ("PASS", WEB_SEARCH_NA)
            success &= acceptable
            if not acceptable:
                failing.append(f"{cls.rsplit('.', 1)[-1]}.{name}")
    parity = failure_reasons("deterministic", "modeler.ModelerParity")
    if parity:
        print("\nModeler parity failures (Camunda Modeler 5.51.1 view of the model; strict, blocks live):")
        for name in EXPECTED["modeler.ModelerParity"]:
            for line in parity.get(name, "").splitlines():
                print(f"- {name}: {line}")
    if (TARGET / "e2e-attempted").exists():
        state = results("e2e").get(("demo.LiveTest", "l1"), "FAIL (not executed)")
        print(f"| E2E.realDaytona | {state} |")
        success &= state == "PASS" and (TARGET / "e2e.exit").read_text().strip() == "0"
    else:
        print("| E2E.realDaytona | NOT RUN (opt-in) |")
    try:
        proof = json.loads((TARGET / "real-daytona-check.json").read_text())
    except (OSError, ValueError):
        proof = {"passed": False, "reason": "FAIL (proof not checked); run ./gate.sh --e2e"}
    if not isinstance(proof, dict):
        proof = {"passed": False, "reason": "FAIL (invalid proof result); run ./gate.sh --e2e"}
    print(f"| RealDaytona.proof | {proof.get('reason', 'FAIL (invalid proof result)')} |")
    proof_passed = isinstance(proof, dict) and proof.get("passed") is True
    success &= proof_passed
    if not proof_passed:
        failing.append("RealDaytona.proof")
    try:
        success &= coverage()
    except (OSError, ValueError, KeyError, TypeError) as error:
        print("FAIL coverage:", error)
        success = False
    print("#86 live checks require saved custom credential binding, reference-only cluster value, no raw key patterns in process variables, and successful live cases.")
    print("#83 web search: N/A requires a gate-owned live probe proving the regional rejection; never live proof of web search. Round 3 has no REST stand-in.")
    print("#83 provider-side document parsing is separate: file-parser/native configuration, USER PDF history evidence, and PDF-only draft facts are required in both live cases.")
    print("Default live uses mocked Daytona: deadline 2027-01-13 is stub evidence, not a computed-deadline proof. Only opt-in --e2e gates the real sandbox deadline.")
    leak_free = token_scan()
    print("Token-leak scan:", "PASS" if leak_free else "FAIL (matches withheld)")
    success &= leak_free
    na = web_search_result() == WEB_SEARCH_NA
    details = (["N/A: #83 web search not available on EU account"] if na else []) + failing
    print("GATE:", ("PASS" if success else "FAIL") + (f" ({'; '.join(details)})" if details else ""))
    return success


def token_scan():
    pattern = re.compile(rb"Bearer[ \t]+(?!REDACTED\b|redacted\b)[A-Za-z0-9_.~+/-]{8,}")
    for path in list(TARGET.rglob("*")) + list((ROOT / "proofs").rglob("*")):
        if path.is_file():
            with path.open("rb") as stream:
                tail = b""
                while chunk := stream.read(1024 * 1024):
                    data = tail + chunk
                    if pattern.search(data):
                        return False
                    tail = data[-4096:]
    return True


def eligible_e2e():
    return ((TARGET / "live.exit").is_file()
            and (TARGET / "live.exit").read_text().strip() == "0"
            and all(results("live").get(("demo.LiveTest", name)) == "PASS" for name in ("l1", "l2"))
            and web_search_result() == WEB_SEARCH_NA)


if __name__ == "__main__":
    if sys.argv[1] == "sync":
        sync(Path(sys.argv[2]))
    elif sys.argv[1] == "eligible":
        sys.exit(0 if eligible() else 1)
    elif sys.argv[1] == "eligible-e2e":
        if not eligible_e2e():
            raise SystemExit("FAIL: --e2e requires a successful default live run on this snapshot")
    elif sys.argv[1] == "verify":
        verify(Path(sys.argv[2]))
    elif sys.argv[1] == "reset-l2":
        path = TARGET / "surefire-live-l2"
        if path.exists():
            shutil.rmtree(path)
        (TARGET / "live-l2-attempted").touch()
    elif sys.argv[1] == "reset-e2e":
        path = TARGET / "surefire-e2e"
        if path.exists():
            shutil.rmtree(path)
        (TARGET / "e2e.exit").unlink(missing_ok=True)
        (TARGET / "real-daytona-execution.json").unlink(missing_ok=True)
        (TARGET / "real-daytona-handles.json").unlink(missing_ok=True)
        (TARGET / "e2e-attempted").touch()
    elif sys.argv[1] == "summary":
        sys.exit(0 if summary(*sys.argv[2:]) else 1)
    elif sys.argv[1] == "coverage":
        sys.exit(0 if coverage(Path(sys.argv[2]), Path(sys.argv[3])) else 1)
    else:
        raise SystemExit("Unknown gate report command")
