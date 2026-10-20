# Card-dispute demo

The card-dispute demo runs every 8.11 AI Agent epic in one process. It uses synthetic data only.
The demo issue is [agentic-orchestration-pod#137](https://github.com/camunda/agentic-orchestration-pod/issues/137).

This folder holds the BPMN diagrams, forms, and demo assets. It also holds the test gate that
proves the demo end to end.

> **Prototype only.** This example lives on the `ao-pod/8.11/topic/card-dispute-demo` branch. It needs
> features that are not on main yet. Run it from the generated `ao-pod/8.11-demo` branch. The
> [8.11 prototype index](https://github.com/camunda/agentic-orchestration-pod/blob/docs/8.11-prototype-index/initiatives/8.11-prototypes/README.md)
> lists every prototype branch that the demo needs.

## Contents

| Folder | What it holds |
| --- | --- |
| [`demo/`](demo/) | The demo as it runs on the prototype cluster. Start with [`demo/README.md`](demo/README.md) and [`demo/RUNBOOK.md`](demo/RUNBOOK.md). |
| [`demo/card-dispute.bpmn`](demo/card-dispute.bpmn) | The current process. It is CardDispute version 29. Its SHA-256 starts with `b8bea0ef8c77cbf9`. |
| [`demo/releases/`](demo/releases/) | The diagrams, templates, and notes of earlier releases v19 to v27. |
| [`demo/variants/`](demo/variants/) and [`demo/proof/`](demo/proof/) | A web-search variant and a binding-proof diagram. |
| [`cpt-gate/`](cpt-gate/) | The test gate. It has Camunda Process Test suites in Java, Python tests for the proof tools, and a Modeler parity check. Start with [`cpt-gate/README.md`](cpt-gate/README.md). |

## Run the tests

The gate tests the demo against an engine that is built from the prototype branches. It needs a
local build of the prototype cluster. Set `SOURCE_CLUSTER` to that build. The default is a
`source-cluster` folder next to `cpt-gate/`. The gate copies the demo from `$SOURCE_CLUSTER/demo`.

```sh
SOURCE_CLUSTER=<path-to-source-cluster> ./cpt-gate/gate.sh --deterministic-only
```

The deterministic tier makes no calls to paid services. The live tier needs the prototype cluster
and its connector secrets.

The Modeler parity check also needs the connector templates of Camunda Modeler 5.51.1. This folder
does not include them because they are 16 MB. Copy them as
[`cpt-gate/modeler-parity/SOURCE.json`](cpt-gate/modeler-parity/SOURCE.json) describes.

## What is not here

The run evidence stays on the
[evidence branch](https://github.com/camunda/agentic-orchestration-pod/tree/docs/preserve-model-providers-session-state/demo/8.11).
That includes proof logs, coverage reports, per-epic snapshots, and rollout records.

The files in this folder are copies from that branch. The `.zip` skill archives are force-added because
the repository ignores `*.zip`. Only `cpt-gate/gate.sh` changed. It now reads
the cluster path from `SOURCE_CLUSTER`. The tests pin the bytes of the policies, rules, and skills.
