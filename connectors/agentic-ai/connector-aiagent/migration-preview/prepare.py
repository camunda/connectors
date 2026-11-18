#!/usr/bin/env python3
"""Prepare synthetic AI Agent cases for the upstream draft migration demo."""

import argparse
import json
from pathlib import Path
from xml.etree import ElementTree as ET

MODULE = Path(__file__).resolve().parents[1]
DRAFT_HEAD = "e0d17a4869eedc6472ea56e56bc6dfea07bec23c"
PROVIDER = "provider.type"
OPENAI = {
    "openai": "openai-api",
    "azureOpenAi": "foundry",
    "openaiCompatible": "custom",
}
CASES = {
    "task": ("Azure OpenAI", "AzureTask", "azureOpenAi", "50", {
        "provider.azureOpenAi.authentication.type": "clientCredentials",
        "provider.azureOpenAi.model.deploymentName": "demo-gpt-deployment",
    }),
    "subprocess": ("Vertex AI", "VertexSubprocess", "google-vertex-ai", "270", {
        "provider.googleVertexAi.endpoint": "https://vertex.example.invalid",
        "provider.googleVertexAi.projectId": "demo-vertex-project",
        "provider.googleVertexAi.region": "us-central1",
    }),
}
NS = {
    "bpmn": "http://www.omg.org/spec/BPMN/20100524/MODEL",
    "bpmndi": "http://www.omg.org/spec/BPMN/20100524/DI",
    "dc": "http://www.omg.org/spec/DD/20100524/DC",
    "modeler": "http://camunda.org/schema/modeler/1.0",
    "zeebe": "http://camunda.org/schema/zeebe/1.0",
}


def load(path):
    return json.loads((MODULE / path).read_text())


def inputs(template):
    return {p["binding"]["name"]: p for p in template["properties"]
            if p["binding"]["type"] == "zeebe:input"}


def add_migration(source, target, recipe):
    old, new = inputs(source), inputs(target)
    paths = recipe["sources"][0]["paths"]
    assert recipe["sources"][0]["sourceTemplateId"] == source["id"]
    hints = []

    def move(src, dst, values=None):
        assert old[src]["type"] != "Hidden" and new[dst]["type"] != "Hidden", (src, dst)
        hint = {"from": old[src]["binding"], "to": new[dst]["binding"]}
        if values is not None:
            assert set(values.values()) <= {choice["value"] for choice in new[dst]["choices"]}
            hint["values"] = values
        hints.append(hint)

    move(PROVIDER, PROVIDER, {**dict.fromkeys(OPENAI, "openai"), "google-vertex-ai": "google-gemini"})
    # All selectors read the old BPMN before replacement. Inactive destinations are skipped.
    move(PROVIDER, "provider.openai.api.type", dict.fromkeys(OPENAI, "completions"))
    move(PROVIDER, "provider.openai.backend.type", OPENAI)
    move(PROVIDER, "provider.googleGemini.backend.type", {"google-vertex-ai": "google-vertex-ai"})

    for path in paths:
        for rule in path.get("rules", []):
            # Only direct moves. In particular Vertex's endpoint targets a Hidden field;
            # the prototype's nested rules for custom endpoints/auth need predicates.
            if set(rule) == {"from", "to"} and rule["from"] != "provider.googleVertexAi.endpoint":
                move(rule["from"], rule["to"])

    # Authentication selectors precede their credential fields. Copy shared provider inputs
    # explicitly since the initial template application can deactivate their branches.
    move("provider.bedrock.authentication.type", "provider.bedrock.authentication.type")
    for name in old:
        if (name.startswith(("provider.anthropic.", "provider.bedrock.", "provider.openai."))
                and name in new and old[name]["type"] != "Hidden"
                and name != "provider.bedrock.authentication.type"):
            move(name, name)
    move("data.systemPrompt.prompt", "data.systemPrompt.prompt")

    target["migratesFrom"] = [{
        "id": source["id"],
        "description": "Preview only: some legacy configurations lose settings. Review changes first.",
        "changes": (
            "- OpenAI keeps Chat Completions; Azure and compatible endpoints move to Foundry and Custom.\n"
            "- Vertex moves to Google Gemini's Vertex backend. Custom Vertex endpoints are not carried over.\n"
            "- OpenAI-compatible API keys are not carried over. Anthropic endpoints and API keys are not "
            "carried over: the draft cannot distinguish public, Foundry, and custom endpoints.\n"
            "- The draft cannot restrict the action to eligible source versions or configurations. "
            "This is not a verified runtime upgrade."
        ),
        "properties": hints,
    }]


def qname(name):
    prefix, local = name.split(":")
    return f"{{{NS[prefix]}}}{local}"


def add(parent, tag, **attributes):
    return ET.SubElement(parent, qname(tag), attributes)


def example_value(prop):
    if "value" in prop:
        value = prop["value"]
        return str(value).lower() if isinstance(value, bool) else str(value)
    name = prop["binding"].get("name", "")
    if name.endswith(".endpoint"):
        return "https://provider.example.invalid"
    if name.endswith(".timeout"):
        return "PT30S"
    if prop.get("feel") == "required":
        return "=previewValue"
    return "preview-value"


def enabled(prop, selections):
    condition = prop.get("condition")
    if condition is None:
        return True
    return all(selections.get(part["property"]) == part["equals"]
               for part in condition.get("allMatch", [condition]))


def add_case(process, plane, template, label, case):
    scenario, node_id, provider, x, overrides = case
    settings = {PROVIDER: provider, **overrides}
    fields = {p["id"]: settings.get(p["id"], example_value(p))
              for p in template["properties"] if "id" in p}
    kind = {"bpmn:ServiceTask": "bpmn:serviceTask",
            "bpmn:AdHocSubProcess": "bpmn:adHocSubProcess"}[template["elementType"]["value"]]
    agent = add(process, kind, id=node_id, name=f"{label}: {scenario}", **{
        qname("zeebe:modelerTemplate"): template["id"],
        qname("zeebe:modelerTemplateVersion"): str(template["version"]),
    })
    ext = add(agent, "bpmn:extensionElements")
    task_definition = {p["binding"]["property"]: example_value(p) for p in template["properties"]
                       if p["binding"]["type"] == "zeebe:taskDefinition"}
    add(ext, "zeebe:taskDefinition", **task_definition)
    io = add(ext, "zeebe:ioMapping")
    for name, prop in inputs(template).items():
        if not enabled(prop, fields):
            continue
        if prop["type"] == "Hidden" and "value" not in prop:
            continue
        if name == "data.systemPrompt.prompt":
            value = '="Keep this legacy prompt"'
        else:
            value = settings.get(name, example_value(prop))
        add(io, "zeebe:input", source=value, target=name)
    shape = add(plane, "bpmndi:BPMNShape", id=f"{node_id}_di", bpmnElement=node_id)
    add(shape, "dc:Bounds", x=x, y="210", width="160", height="90")


def prepare(demo):
    for prefix, uri in NS.items():
        ET.register_namespace(prefix, uri)
    diagram = ET.parse(demo / "diagram.bpmn")
    diagram.getroot().set(qname("modeler:executionPlatformVersion"), "8.10.0")
    process = diagram.find("bpmn:process", NS)
    process.set("isExecutable", "false")
    plane = diagram.find("bpmndi:BPMNDiagram/bpmndi:BPMNPlane", NS)
    templates = json.loads((demo / "templates.json").read_text())
    for prefix, suffix in (("", ""), ("hybrid/", "-hybrid")):
        for kind, legacy, label in (("task", "outbound-connector", "Task"),
                                    ("subprocess", "job-worker", "Sub-process")):
            source = load(f"element-templates/{prefix}agenticai-aiagent-{legacy}{suffix}.json")
            target = load(f"element-templates/{prefix}agenticai-ai-agent-{kind}.v2{suffix}.json")
            recipe = load(f"element-template-overrides/ai-agent-{kind}.v2{suffix}.migrates-from.json")
            add_migration(source, target, recipe)
            if not suffix:
                add_case(process, plane, source, label, CASES[kind])
            templates.extend([source, target])
    (demo / "ai-agent-templates.json").write_text(json.dumps(templates, indent=2) + "\n")
    ET.indent(diagram)
    diagram.write(demo / "ai-agent-diagram.bpmn", encoding="utf-8", xml_declaration=True)
    upstream_example = demo / "Migration.example.spec.js"
    example = upstream_example.read_text()
    upstream_example.write_text(example.replace("'./templates.json'", "'./ai-agent-templates.json'")
                                .replace("'./diagram.bpmn'", "'./ai-agent-diagram.bpmn'"))


if __name__ == "__main__":
    import subprocess

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("checkout", type=Path, help="disposable checkout of draft bpmn-js-element-templates")
    args = parser.parse_args()
    head = subprocess.check_output(["git", "-C", args.checkout, "rev-parse", "HEAD"], text=True).strip()
    if head != DRAFT_HEAD:
        parser.error(f"expected upstream draft {DRAFT_HEAD}; got {head}")
    prepare(args.checkout / "test/spec/cloud-element-templates/migration")
