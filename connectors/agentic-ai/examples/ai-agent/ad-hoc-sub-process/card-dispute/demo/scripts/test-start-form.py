#!/usr/bin/env python3
"""Check Tasklist/CLI initialization against the owned cluster without starting an instance."""

import json
from pathlib import Path
import sys
import urllib.request
import xml.etree.ElementTree as ET

from importlib.util import module_from_spec, spec_from_file_location

HERE = Path(__file__).resolve().parent
spec = spec_from_file_location("prepare_start_form", HERE / "prepare-start-form.py")
module = module_from_spec(spec)
spec.loader.exec_module(module)
model = (HERE.parent / "card-dispute.bpmn").read_text()
variables = json.loads(Path(sys.argv[1]).read_text())
prepared = module.prepare(model, variables)
assert prepared == module.prepare(prepared, variables), "Preparation must be idempotent"
ns = {"bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL",
      "zeebe": "http://camunda.org/schema/zeebe/1.0"}
prompt = ET.fromstring(prepared).find(
    ".//zeebe:input[@target='data.userPrompt.prompt']", ns
).attrib["source"]
assert "sandbox_bash" in prompt and "deadline.py" in prompt
assert "sha256sum" not in prompt and "deadlineScriptSha256" not in prompt
assert "deadlineSkillBundleSha256" not in prompt
assert "No transaction CSV is attached or preloaded" in prompt
assert "sandbox_fs_write" in prompt and "sandbox_export_document" in prompt
assert "transactions-2026-09.csv" not in prepared
assert not any(doc["metadata"]["fileName"].endswith(".csv") for doc in variables["disputeDocuments"])
for file in (
    HERE.parent / "policies/dispute-compliance-policy.md",
    HERE.parent / "policies/v2/dispute-compliance-policy.md",
    HERE.parent / "skills/chargeback-deadline/SKILL.md",
):
    instructions = file.read_text()
    assert "sha256sum" not in instructions and "pre-run" not in instructions
    assert "deadline.py" in instructions
event = ET.fromstring(prepared).find(".//bpmn:startEvent[@id='DisputeReceived']", ns)
mappings = event.findall("./bpmn:extensionElements/zeebe:ioMapping/zeebe:output", ns)
assert len(mappings) == 11
form = event.find("./bpmn:extensionElements/zeebe:formDefinition", ns)
assert form.attrib == {"formId": "StartDisputeForm", "bindingType": "deployment"}
example = event.find(".//zeebe:property[@name='camundaModeler:exampleOutputJson']", ns)
assert json.loads(example.attrib["value"])["claim"].startswith("Synthetic demo only.")
start_form = json.loads((HERE.parent / "start-dispute.form").read_text())
assert not any(c["type"] == "button" for c in start_form["components"]), \
    "Tasklist supplies the start action; the form must not duplicate it"
fields = {c["key"]: c for c in start_form["components"] if "key" in c}
assert set(fields) == {"claim", "statementDocuments"}
assert fields["claim"]["type"] == "textarea"
assert fields["statementDocuments"]["type"] == "filepicker"
assert all(c["validate"]["required"] is True for c in fields.values())
assert fields["statementDocuments"]["accept"] == ".pdf"
uploaded_pdf = {**variables["disputeDocuments"][0], "documentId": "tasklist-upload-proof"}
form_input = {"claim": "Synthetic pasted claim: two charges for one order.",
              "statementDocuments": [uploaded_pdf]}


def evaluate(expression, context):
    request = urllib.request.Request(
        "http://localhost:8280/v2/expression/evaluation",
        data=json.dumps({"expression": expression, "variables": context}).encode(),
        headers={"Content-Type": "application/json"},
    )
    response = json.load(urllib.request.urlopen(request, timeout=30))
    if response.get("result") is None:
        raise AssertionError(response)
    return response["result"]


for name, context in [("Tasklist", form_input), ("CLI", variables)]:
    initialized = dict(context)
    for mapping in mappings:
        initialized[mapping.attrib["target"]] = evaluate(mapping.attrib["source"], context)
    assert initialized["claim"] == context["claim"]
    assert initialized["disputeFilingDate"] == "2026-10-20"
    assert initialized["deadlineSkillBundles"] == variables["deadlineSkillBundles"]
    assert initialized["deadlineSkillManifest"] == variables["deadlineSkillManifest"]
    assert initialized["deadlineScriptSha256"] == variables["deadlineScriptSha256"]
    assert not any(doc["metadata"]["fileName"].endswith(".csv") for doc in initialized["disputeDocuments"])
    if name == "Tasklist":
        assert initialized["disputeDocuments"] == [uploaded_pdf] + variables["disputeDocuments"][1:]
    else:
        for key, value in variables.items():
            assert initialized[key] == value, key
    print(f"PASS: {name} maps exact submitted claim/documents and configured skill defaults")

bad = {**variables, "disputeDocuments": []}
try:
    module.prepare(model, bad)
except ValueError:
    print("PASS: malformed support document list rejected before rewriting model")
else:
    raise AssertionError("Malformed document list was accepted")
print("PASS: start form, deployment binding and synthetic Modeler example metadata")
