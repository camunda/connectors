# Ad-hoc tools schema element template

This directory contains the [element template](https://docs.camunda.io/docs/components/modeler/desktop-modeler/element-templates/about-templates/)
of the ad-hoc tools schema connector, which resolves the tools available in an ad-hoc sub-process. The
templates of the other Agentic AI connectors live in their own modules, see the
[index of all Agentic AI element templates](../../connector-agentic-ai/element-templates/README.md).

## Which template do I need?

Templates are versioned per Camunda minor version. The table below lists the newest template for each
Camunda minor version that the connector supports. The newest template lives directly in this folder.
Templates for older Camunda minor versions live in [`versioned/`](./versioned/).

Pick the row with the highest minimum Camunda version that is not higher than the Camunda version you
run. A template also works on later Camunda minor versions: the `engines.camunda` field `^8.8` means
"Camunda 8.8 or later".

For example, on Camunda 8.8, 8.9 and 8.10 use the ad-hoc tools schema template version `2`.

## Ad-hoc tools schema connector

See the [Ad-hoc tools schema resolver documentation](https://docs.camunda.io/docs/next/components/connectors/out-of-the-box-connectors/agentic-ai-ad-hoc-tools-schema-resolver/).

| Connector           | Minimum Camunda version | Template version | File |
| --- | --- | --- | --- |
| Ad-hoc tools schema | 8.8 | 2 | [`agenticai-adhoctoolsschema-outbound-connector.json`](./agenticai-adhoctoolsschema-outbound-connector.json) |

## Maintaining this index

This index must stay in sync with the JSON files in this folder every time a template version is
bumped. The newest template lives in this folder; superseded ones move into [`versioned/`](./versioned/)
when `versionHistoryEnabled` is set.
