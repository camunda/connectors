"""Validate skill bytes and ordered, correlated sandbox evidence; never execute a command."""
from datetime import datetime
import hashlib
import json
from pathlib import PurePosixPath
import shlex

PATHS = ("SKILL.md", "references/network-rules.md", "scripts/deadline.py")
PREFIX = "Chargeback deadline skill:"


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON key: " + key)
        result[key] = value
    return result


def exact_json(actual, expected):
    if type(actual) is not type(expected):
        return False
    if isinstance(expected, dict):
        return actual.keys() == expected.keys() and all(
            exact_json(actual[key], value) for key, value in expected.items())
    if isinstance(expected, list):
        return len(actual) == len(expected) and all(
            exact_json(left, right) for left, right in zip(actual, expected))
    return actual == expected


def cited_result(citation):
    if not citation.startswith(PREFIX):
        raise ValueError("Missing final skill evidence prefix")
    start = citation.find("{", len(PREFIX))
    if start < 0:
        raise ValueError("Missing complete cited JSON object")
    decoder = json.JSONDecoder(object_pairs_hook=unique_object)
    result, end = decoder.raw_decode(citation, start)
    if not isinstance(result, dict) or any(char in citation[end:] for char in "{}"):
        raise ValueError("Ambiguous cited JSON object")
    return result


def digest(data):
    return hashlib.sha256(data).hexdigest()


def manifest(directory):
    result = {}
    for path in PATHS:
        file = directory / path
        if not file.is_file():
            raise ValueError("Missing skill document: " + path)
        result[path] = digest(file.read_bytes())
    return result


def command_tokens(command):
    lexer = shlex.shlex(command.replace("\\\n", ""), posix=True, punctuation_chars=True)
    lexer.whitespace_split = True
    lexer.commenters = ""
    return list(lexer)


def protocol_commands(expected_hash, filing_date):
    path = "chargeback-deadline/scripts/deadline.py"
    check = ["printf", "%s  %s\\n", expected_hash, path, "|", "sha256sum", "-c", "-"]
    run = [
        "python3", path, "--charge-date", "2026-09-14", "--charge-date", "2026-09-15",
        "--reason-code", "CN-DUP-01", "--filed-on", filing_date,
        "&&", "sha256sum", path,
    ]
    return check, run


def execution_command(command, expected_hash, filing_date):
    check, run = protocol_commands(expected_hash, filing_date)
    if command_tokens(command) != check + ["&&"] + run:
        raise ValueError("Expected fail-fast pre-check && unchanged Python && post-check command")


def validate(evidence, directory, expected_manifest):
    """Consume normalized engine scopes joined to Conversation calls by id, not LLM prose."""
    if manifest(directory) != expected_manifest or set(expected_manifest) != set(PATHS):
        raise ValueError("Original skill bytes differ from the input manifest")
    if evidence.get("filingDate") != "2026-10-20":
        raise ValueError("Missing or incorrect explicit disputeFilingDate")
    script_hash = expected_manifest["scripts/deadline.py"]
    if evidence.get("deadlineScriptSha256") != script_hash:
        raise ValueError("deadlineScriptSha256 differs from the original script")
    documents = evidence.get("documents", {})
    if set(documents) != set(PATHS) or len(set(documents.values())) != len(PATHS):
        raise ValueError("Missing or ambiguous uploaded skill document identities")
    if any(not isinstance(value, str) or not value for value in documents.values()):
        raise ValueError("Missing uploaded skill documentId")
    workdir = PurePosixPath(evidence.get("workDir", ""))
    if not workdir.is_absolute() or ".." in workdir.parts:
        raise ValueError("Missing CREATE.workDir")
    handle = evidence.get("sandboxId")
    if not handle or handle == "sandbox-fallback-in-process":
        raise ValueError("Missing real CREATE handle")
    imports, reads, runs = {}, {}, []
    call_ids, scope_keys = set(), set()
    events = evidence.get("events", [])
    previous_start = None
    precheck_completed = None
    for event in events:
        call_id, scope = event.get("callId"), event.get("scopeKey")
        if not call_id or not scope or call_id in call_ids or scope in scope_keys:
            raise ValueError("Missing or duplicate Conversation callId / AnalyzeInSandbox scopeKey")
        call_ids.add(call_id)
        scope_keys.add(scope)
        if event.get("conversationCallId") != call_id or event.get("elementId") != "AnalyzeInSandbox":
            raise ValueError("Conversation/tool operation identity does not match engine scope")
        started = datetime.fromisoformat(event["startedAt"].replace("Z", "+00:00"))
        completed = datetime.fromisoformat(event["completedAt"].replace("Z", "+00:00"))
        if started.tzinfo is None or completed.tzinfo is None or completed < started:
            raise ValueError("Invalid engine operation timestamps")
        if previous_start is not None and started < previous_start:
            raise ValueError("Operation history is not ordered")
        previous_start = started
        if event.get("handle") != handle or event.get("success") is not True:
            raise ValueError("Failed or foreign sandbox operation")
        operation = event.get("operation")
        if operation == "IMPORT_DOCUMENT":
            if precheck_completed is not None:
                raise ValueError("Import/write after prehash invalidates script provenance")
            logical = next((key for key, value in documents.items()
                            if event.get("documentId") == value), None)
            if logical is None:
                raise ValueError("Import documentId does not identify an uploaded skill document")
            destination = workdir / "chargeback-deadline" / logical
            if event.get("path") != str(destination) or logical in imports:
                raise ValueError("Unexpected or repeated skill import destination")
            if event.get("documentSha256") != expected_manifest[logical]:
                raise ValueError("Imported document bytes do not match the original")
            if logical == "scripts/deadline.py":
                if set(reads) != set(PATHS[:2]) or any(end > started for end in reads.values()):
                    raise ValueError("Both original instruction reads must finish before script import")
            imports[logical] = completed
        elif operation == "FS_READ":
            logical = next((key for key in PATHS[:2]
                            if event.get("path") == str(workdir / "chargeback-deadline" / key)), None)
            if logical not in imports or logical in reads or imports[logical] > started:
                raise ValueError("Read must follow its original document import")
            if event.get("content") != (directory / logical).read_text():
                raise ValueError("Read result differs from original instruction/reference bytes")
            reads[logical] = completed
        elif operation == "BASH":
            command = event.get("command", "")
            tokens = command_tokens(command)
            directories = [str(workdir / "chargeback-deadline" / name)
                           for name in ("", "references", "scripts")]
            directories += ["chargeback-deadline", "chargeback-deadline/references", "chargeback-deadline/scripts"]
            if tokens and tokens[0] == "mkdir":
                if tokens[:2] != ["mkdir", "-p"] or not tokens[2:] or any(
                        path not in directories for path in tokens[2:]):
                    raise ValueError("Only skill-directory creation is allowed before execution")
                if event.get("exitCode") != 0:
                    raise ValueError("Skill directory creation failed")
                continue
            if "scripts/deadline.py" not in imports or imports["scripts/deadline.py"] > started:
                raise ValueError("Python execution must follow the original script import")
            if event.get("exitCode") != 0:
                raise ValueError("Nonzero remote Python exit status")
            stdout = event.get("stdout", "")
            lines = stdout.strip().splitlines()
            check, run = protocol_commands(script_hash, evidence["filingDate"])
            path = "chargeback-deadline/scripts/deadline.py"
            bare_check = ["sha256sum", path]
            if tokens in (check, bare_check):
                if precheck_completed is not None or runs:
                    raise ValueError("Unexpected repeated or late prehash")
                if tokens == check:
                    valid = lines == [path + ": OK"]
                else:
                    valid = len(lines) == 1 and lines[0].split() == [script_hash, path]
                if not valid:
                    raise ValueError("Prehash differs from original script")
                precheck_completed = completed
                continue
            if tokens in (check + ["&&"] + run, bare_check + ["&&"] + run):
                if len(lines) != 3:
                    raise ValueError("Missing pre-check, complete JSON stdout or post-check")
                if tokens[:len(check)] == check:
                    valid = lines[0] == path + ": OK"
                else:
                    valid = lines[0].split() == [script_hash, path]
                if not valid:
                    raise ValueError("Prehash differs from original script")
                json_line = lines[1]
            elif tokens == run:
                if precheck_completed is None or precheck_completed > started or len(lines) != 2:
                    raise ValueError("Visible run/posthash requires an earlier completed same-sandbox prehash")
                json_line = lines[0]
            else:
                raise ValueError("Expected approved unchanged Python/hash protocol; script recreation forbidden")
            if lines[-1].split() != [script_hash, "chargeback-deadline/scripts/deadline.py"]:
                raise ValueError("Post-run script hash mismatch")
            result = json.loads(json_line, object_pairs_hook=unique_object)
            expected = {
                "skill": "chargeback-deadline", "reasonCode": "CN-DUP-01",
                "chargeDates": ["2026-09-14", "2026-09-15"], "timeLimitDays": 120,
                "anchorBasis": "last_duplicate_charge", "anchorDate": "2026-09-15",
                "lastChargeDate": "2026-09-15", "filingDeadline": "2027-01-13",
                "filedOn": evidence["filingDate"], "eligible": True,
                "eligibilityScope": "filing_window_only", "daysBetweenCharges": 1,
                "scriptSha256": script_hash,
            }
            if not exact_json(result, expected):
                raise ValueError("Remote skill JSON differs from the full reference contract")
            runs.append((result, json_line, digest(stdout.encode())))
        else:
            raise ValueError("Unsupported skill operation; script recreation is forbidden")
    if set(imports) != set(PATHS) or set(reads) != set(PATHS[:2]) or len(runs) != 1:
        raise ValueError("Missing successful original imports, reads or single Python execution")
    citations = [item for item in evidence.get("draftEvidence", []) if item.startswith(PREFIX)]
    if (len(citations) != 1 or not exact_json(cited_result(citations[0]), runs[0][0])
            or citations[0].count(script_hash) < 3):
        raise ValueError("Draft must cite complete actual JSON plus pre/post script hashes")
    return {"manifest": expected_manifest, "result": runs[0][0], "stdoutSha256": runs[0][2],
            "callIds": [event["callId"] for event in events]}
