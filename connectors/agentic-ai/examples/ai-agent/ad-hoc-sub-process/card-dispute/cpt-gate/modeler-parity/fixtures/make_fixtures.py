#!/usr/bin/env python3
"""Regenerate the Modeler parity fixtures (run by hand; outputs are checked in).

fixtures/v14/   the CardDispute v14 BPMN as the user opened it (no .camunda folder).
fixtures/fixed/ one way to satisfy (a)-(e), built from the read-only modeler/ inputs:
                the source-built AI Agent sub-process template (version 2) merged with
                fragments 75, 83 and 86 per modeler/CONTRACT.md, the Daytona and A2A
                templates, and the BPMN without the inline prompt, pinned to version 2.
The fixed fixture is a test oracle for the checker, not a proposed model.
"""
import json
import pathlib
import re
import shutil

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[2]  # card-dispute-proto
DEMO = ROOT / "source-cluster" / "demo"
MODELER = ROOT / "modeler"
BASE = (ROOT / "source-cluster/connectors/connectors/agentic-ai/connector-agentic-ai"
        / "element-templates/agenticai-ai-agent-subprocess.v2.json")
AGENT_ID = "io.camunda.connectors.agenticai.ai-agent-subprocess.v2"


def merge(template, fragment):
    remove = set(fragment.get("remove", []))
    props = template["properties"]
    anchor = next((i for i, p in enumerate(props) if p.get("id") in remove), len(props))
    kept_before = [p for p in props[:anchor] if p.get("id") not in remove]
    kept_after = [p for p in props[anchor:] if p.get("id") not in remove]
    template["properties"] = kept_before + fragment["properties"] + kept_after
    groups = template["groups"]
    for group in fragment.get("groups", []):
        if all(g["id"] != group["id"] for g in groups):
            at = next((i + 1 for i, g in enumerate(groups) if g["id"] == "model-options"), len(groups))
            groups.insert(at, group)
    if fragment.get("configurationTemplates"):
        template.setdefault("configurationTemplates", []).extend(fragment["configurationTemplates"])


def main():
    v14 = (DEMO / "card-dispute.bpmn").read_text()

    out = HERE / "v14"
    shutil.rmtree(out, ignore_errors=True)
    out.mkdir(parents=True)
    (out / "card-dispute.bpmn").write_text(v14)

    agent = json.loads(BASE.read_text())
    for name in ("75-system-prompt.json", "83-server-tools.json", "86-saved-credential.json"):
        merge(agent, json.loads((MODELER / "fragments" / name).read_text()))

    fixed = re.sub(
        r'(id="DisputeAgent"[^>]*zeebe:modelerTemplateVersion=")1(")', r"\g<1>%s\2" % agent["version"], v14, count=1)
    fixed, removed = re.subn(r'\s*<zeebe:input source="[^"]*" target="data\.systemPrompt\.prompt" />', "", fixed)
    assert removed == 1 and fixed != v14, "v14 shape changed; update make_fixtures.py"

    out = HERE / "fixed"
    shutil.rmtree(out, ignore_errors=True)
    templates = out / ".camunda" / "element-templates"
    templates.mkdir(parents=True)
    (out / "card-dispute.bpmn").write_text(fixed)
    (templates / "ai-agent-subprocess.json").write_text(json.dumps(agent, indent=2) + "\n")
    shutil.copy(MODELER / "templates" / "daytona-sandbox.json", templates / "daytona-sandbox.json")
    shutil.copy(MODELER / "templates" / "a2a-1.0-client.json", templates / "a2a-1.0-client.json")
    print(f"agent template {AGENT_ID}@{agent['version']}: {len(agent['properties'])} properties")


if __name__ == "__main__":
    main()
