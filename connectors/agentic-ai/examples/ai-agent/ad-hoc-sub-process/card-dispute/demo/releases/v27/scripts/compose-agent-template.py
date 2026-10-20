#!/usr/bin/env python3
"""Compose the prototype AI Agent sub-process template (version 101).

Base: connectors agenticai-ai-agent-subprocess.v2.json (version 2).
Fragments (card-dispute-proto/modeler): 75 system prompt, 83 provider capabilities
(round 2), 86 saved credential. Each fragment property replaces the removed base property at the
same position; new properties go after the last replaced one.
"""
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[3]
BASE = ROOT / "source-cluster/connectors/connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-subprocess.v2.json"
FRAGMENTS = ROOT / "modeler/fragments"
OUT = ROOT / "source-cluster/demo/.camunda/element-templates/agenticai-ai-agent-subprocess.proto-101.json"
VERSION = 101
FRAGMENT_FILES = {
    "75-system-prompt": FRAGMENTS / "75-system-prompt.json",
    "83-provider-capabilities": ROOT / "modeler/round2/83/83-provider-capabilities.json",
    "86-saved-credential": FRAGMENTS / "86-saved-credential.json",
}


def compose():
    tpl = json.loads(BASE.read_text())
    props = tpl["properties"]

    for name, path in FRAGMENT_FILES.items():
        frag = json.loads(path.read_text())
        removed = [i for i, p in enumerate(props) if p.get("id") in frag.get("remove", [])]
        if len(removed) != len(frag.get("remove", [])):
            sys.exit(f"{name}: not all removed ids exist in the base")
        anchor = removed[0]
        props = [p for p in props if p.get("id") not in frag["remove"]]
        props[anchor:anchor] = frag["properties"]

        for group in frag.get("groups", []):
            if group["id"] == "provider-capabilities":
                group = {**group, "openByDefault": True}
                after = [g["id"] for g in tpl["groups"]].index("model-options") + 1
                tpl["groups"].insert(after, group)
            else:
                tpl["groups"].append(group)

        for ct in frag.get("configurationTemplates", []):
            existing = tpl.setdefault("configurationTemplates", [])
            existing[:] = [c for c in existing if c["id"] != ct["id"]] + [ct]

    for p in props:
        if p.get("binding", {}).get("key") == "elementTemplateVersion":
            p["value"] = str(VERSION)

    tpl["properties"] = props
    tpl["version"] = VERSION
    tpl["name"] = tpl["name"] + " (8.11 prototype)"
    tpl["description"] += (
        ". Prototype: linked system prompt (#75), provider capabilities with PDF parsing (#83),"
        " saved credential (#86). Web search needs the Chat Completions API and a global"
        " endpoint; it stays off on EU endpoints."
    )

    ids = [p["id"] for p in props if "id" in p]
    dupes = {i for i in ids if ids.count(i) > 1}
    if dupes:
        sys.exit(f"duplicate property ids: {dupes}")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(tpl, indent=2, ensure_ascii=False) + "\n")
    print(f"{OUT} version {VERSION}, {len(props)} properties, {len(tpl['groups'])} groups")


if __name__ == "__main__":
    compose()
