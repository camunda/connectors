#!/usr/bin/env python3
"""Bind Tasklist defaults to durable documents uploaded by the demo driver."""

import json
from pathlib import Path
import re
import sys
from xml.sax.saxutils import quoteattr


def feel(value):
    if isinstance(value, dict):
        return "{" + ", ".join(f"{key}: {feel(item)}" for key, item in value.items()) + "}"
    if isinstance(value, list):
        return "[" + ", ".join(feel(item) for item in value) + "]"
    return json.dumps(value, ensure_ascii=True)


def prepare(model, variables):
    documents = variables["disputeDocuments"]
    expected = [
        "statement-2026-09.pdf",
        "SKILL.md", "network-rules.md", "deadline.py",
    ]
    if [doc["metadata"]["fileName"] for doc in documents] != expected:
        raise ValueError("Expected the statement and three configured-skill preview documents, no seeded CSV")
    defaults = {key: variables[key] for key in (
        "disputeId", "cardholderName", "cardLast4", "disputedAmount", "clusterApiUrl",
        "disputeFilingDate", "deadlineScriptSha256", "deadlineSkillManifest",
        "deadlineSkillBundles", "deadlineSkillBundleSha256",
    )}
    mappings = [
        (
            key,
            f"=if is defined({key}) and {key} != null then {key} else {feel(value)}",
        )
        for key, value in defaults.items()
    ]
    mappings.append((
        "disputeDocuments",
        "=if is defined(disputeDocuments) and disputeDocuments != null then disputeDocuments "
        f"else concatenate(statementDocuments, {feel(documents[1:])})",
    ))
    example = {
        "claim": "Synthetic demo only. I was charged twice for one headphones order from "
        "Northwind Electronics in mid-September. I only placed one order."
    }
    lines = [
        '    <bpmn:startEvent id="DisputeReceived" name="Dispute received">',
        "      <bpmn:documentation>Tasklist start form: upload a PDF and paste dispute text. "
        "Synthetic example output is copy-paste documentation only. Configured skill "
        "references are uploaded at deployment and stored durably. Transactions must be extracted "
        "from the uploaded PDF; no CSV is preloaded. CLI variables take precedence."
        "</bpmn:documentation>",
        "      <bpmn:extensionElements>",
        '        <zeebe:formDefinition formId="StartDisputeForm" bindingType="deployment" />',
        "        <zeebe:ioMapping>",
    ]
    for target, source in mappings:
        lines.append(f"          <zeebe:output source={quoteattr(source)} target={quoteattr(target)} />")
    lines.extend([
        "        </zeebe:ioMapping>",
        "        <zeebe:properties>",
        "          <zeebe:property name=\"camundaModeler:exampleOutputJson\" "
        f"value={quoteattr(json.dumps(example))} />",
        "        </zeebe:properties>",
        "      </bpmn:extensionElements>",
        "      <bpmn:outgoing>Flow_ToAgentInput</bpmn:outgoing>",
        "    </bpmn:startEvent>",
    ])
    updated, count = re.subn(
        r'    <bpmn:startEvent id="DisputeReceived"[^>]*>.*?</bpmn:startEvent>',
        lambda _: "\n".join(lines), model, count=1, flags=re.DOTALL,
    )
    if count != 1:
        raise ValueError("Expected exactly one DisputeReceived start event")
    return updated


if __name__ == "__main__":
    path = Path(sys.argv[1])
    path.write_text(prepare(path.read_text(), json.load(sys.stdin)))
