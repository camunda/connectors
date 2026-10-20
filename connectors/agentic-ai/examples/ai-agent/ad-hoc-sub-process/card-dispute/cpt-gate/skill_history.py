"""Normalize owned engine/history observations. Synthetic fixtures are not execution proof."""
import hashlib
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import PurePosixPath

import skill_proof


def document_id(argument):
    if not isinstance(argument, str) or "<!" in argument:
        raise ValueError("Expected a single original document tag")
    try:
        tag = ET.fromstring(argument)
    except ET.ParseError as error:
        raise ValueError("Expected a single original document tag") from error
    if tag.tag != "doc" or len(tag) or (tag.text or "").strip() or not tag.get("id"):
        raise ValueError("Expected one doc tag with an id")
    return tag.get("id")


def normalize(raw, directory, expected_manifest):
    variables = {}
    for item in raw["variables"]:
        key = (str(item["scopeKey"]), item["name"])
        if key in variables and variables[key] != item["value"]:
            raise ValueError("Conflicting engine variable observations")
        variables[key] = item["value"]
    history = {}
    for item in raw["history"]:
        if item["role"] != "TOOL_RESULT":
            continue
        for call in item.get("toolCalls", []):
            if call.get("elementId") != "AnalyzeInSandbox":
                continue
            call_id = call["toolCallId"]
            if call_id in history and history[call_id] != item:
                raise ValueError("Ambiguous TOOL_RESULT call id")
            history[call_id] = item
    scopes = [item for item in raw["elements"] if item["elementId"] == "AnalyzeInSandbox"]
    def scoped_call(element):
        scope = str(element["elementInstanceKey"])
        if (scope, "toolCall") in variables:
            call = variables[(scope, "toolCall")]
            if call.get("_meta", {}).get("name") != "AnalyzeInSandbox":
                raise ValueError("Engine tool call does not name the sandbox element")
            return scope, call
        candidates = []
        for body in raw["elements"]:
            body_scope = str(body["elementInstanceKey"])
            call = variables.get((body_scope, "toolCall"))
            if body.get("type") != "AD_HOC_SUB_PROCESS_INNER_INSTANCE" or not call:
                continue
            if call.get("_meta", {}).get("name") != "AnalyzeInSandbox":
                continue
            if scope not in raw.get("scopeChildren", {}).get(body_scope, []):
                continue
            if not (body["startDate"] <= element["startDate"]
                    and body["endDate"] >= element["endDate"]):
                continue
            if body.get("elementName") != element.get("elementName"):
                continue
            if variables.get((body_scope, "toolCallResult")) != variables[(scope, "toolCallResult")]:
                continue
            candidates.append((body_scope, call))
        if len(candidates) != 1:
            raise ValueError("Missing or ambiguous engine inner activation scope for sandbox task")
        return candidates[0]

    creates = []
    for element in scopes:
        scope = str(element["elementInstanceKey"])
        _, call = scoped_call(element)
        if call["operation"] == "CREATE":
            if element["state"] != "COMPLETED":
                raise ValueError("CREATE did not complete")
            creates.append(variables[(scope, "toolCallResult")])
    if len(creates) != 1:
        raise ValueError("Exactly one successful engine CREATE required")
    create = creates[0]
    documents = {}
    for relative in skill_proof.PATHS:
        matched = [doc for doc in raw["documents"]
                   if doc["metadata"]["fileName"] == Path(relative).name]
        if len(matched) != 1:
            raise ValueError("Missing or ambiguous original skill upload")
        doc = matched[0]
        if raw["documentSha256"].get(doc["documentId"]) != expected_manifest[relative]:
            raise ValueError("Downloaded uploaded bytes differ from original skill")
        documents[relative] = doc["documentId"]
    evidence = {"filingDate": raw["filingDate"], "deadlineScriptSha256": raw["deadlineScriptSha256"],
                "documents": documents, "workDir": create["workDir"], "sandboxId": create["handle"],
                "draftEvidence": raw["draftEvidence"], "events": []}
    names = {"IMPORT_DOCUMENT": "sandbox_import_document", "FS_READ": "sandbox_fs_read",
             "BASH": "sandbox_bash", "FS_WRITE": "sandbox_fs_write"}
    for element in sorted(scopes, key=lambda item: item["startDate"]):
        scope = str(element["elementInstanceKey"])
        activation_scope, call = scoped_call(element)
        operation = call["operation"]
        if operation == "CREATE":
            continue
        if element["state"] != "COMPLETED":
            raise ValueError("Sandbox task failed; cannot publish successful skill proof")
        result = variables[(scope, "toolCallResult")]
        call_id = call["_meta"]["id"]
        item = history.get(call_id)
        if item is None:
            raise ValueError("Missing actual TOOL_RESULT identity for engine call")
        conversation = [c for c in item["toolCalls"] if c["toolCallId"] == call_id]
        if len(conversation) != 1 or conversation[0]["toolName"] != names.get(operation):
            raise ValueError("Conversation operation differs from engine call")
        arguments = conversation[0]["arguments"]
        for field in ("path", "command", "content"):
            if field in call and arguments.get(field) != call[field]:
                raise ValueError("Conversation arguments differ from actual engine inputs")
        blocks = item["content"]
        if isinstance(result, str):
            if not any(block.get("contentType") == "TEXT" and block.get("text") == result for block in blocks):
                raise ValueError("Actual TOOL_RESULT output differs from engine result")
        else:
            raise ValueError("Unsupported sandbox operation result shape")
        event = {"operation": operation, "callId": call_id, "conversationCallId": conversation[0]["toolCallId"],
                 "scopeKey": scope, "activationScopeKey": activation_scope,
                 "elementId": element["elementId"], "startedAt": element["startDate"],
                 "completedAt": element["endDate"], "handle": call.get("handle"), "success": True}
        def resolved(path):
            path = PurePosixPath(path)
            if ".." in path.parts:
                raise ValueError("Sandbox path traversal is not original skill provenance")
            return str(path if path.is_absolute() else PurePosixPath(create["workDir"]) / path)
        if operation == "IMPORT_DOCUMENT":
            doc_id = call["document"]["documentId"]
            if document_id(arguments.get("id")) != doc_id:
                raise ValueError("Conversation import id differs from engine document reference")
            if doc_id not in documents.values():
                raise ValueError("Unexpected document import in skill execution")
            path = call["path"]
            basename = Path(path).name
            relative = next(p for p, value in documents.items() if value == doc_id)
            size = (directory / relative).stat().st_size
            if result != f"Imported '{basename}' ({size} bytes) to {path}.":
                raise ValueError("Successful import does not report exact original bytes/destination")
            event.update(path=resolved(path), documentId=doc_id, documentSha256=raw["documentSha256"][doc_id])
        elif operation == "FS_READ":
            event.update(path=resolved(call["path"]), content=result)
        elif operation == "BASH":
            match = re.fullmatch(r"exit_code: (-?\d+)(?:\nstdout:\n(.*?))?(?:\nstderr:\n(.*))?", result, re.S)
            if match is None or match[3] not in (None, "") or int(match[1]) != 0:
                raise ValueError("Missing actual successful bash stdout or unexpected stderr")
            event.update(command=call["command"], exitCode=int(match[1]), stdout=match[2] or "")
        evidence["events"].append(event)
    skill_proof.validate(evidence, directory, expected_manifest)
    return evidence


if __name__ == "__main__":
    if sys.argv[1] != "normalize":
        raise ValueError("Expected normalize")
    source = Path(sys.argv[2])
    raw = json.loads(source.read_text())
    directory = Path(raw["skillDirectory"])
    evidence = normalize(raw, directory, raw["manifest"])
    source.with_name(source.stem + "-normalized.json").write_text(json.dumps(evidence, indent=2))
