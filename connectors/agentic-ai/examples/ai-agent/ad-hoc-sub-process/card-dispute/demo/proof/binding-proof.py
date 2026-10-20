#!/usr/bin/env python3
"""#75 binding proof (unsupported prototype, synthetic data).

Derives a deployment-bound variant of card-dispute.bpmn (process id CardDisputeBindingProof,
bindingType="deployment") and deploys it together with policy v1 in ONE deployment. Starts an
instance, waits for the first draft, then deploys policy v2 (new latest version) and sends the
draft back. The agent re-activates; with deployment binding it must still be pinned to the v1
resource key. Prints the linkedResources header of every DisputeAgent job, then cancels the
instance and restores policy v1 as latest.

Usage: proof/binding-proof.py   (env: CAMUNDA_URL, C8_PROFILE, TIMEOUT_SECONDS)
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

DEMO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
URL = os.environ.get("CAMUNDA_URL", "http://localhost:8280").rstrip("/")
PROFILE = os.environ.get("C8_PROFILE", "local-proto")
TIMEOUT = int(os.environ.get("TIMEOUT_SECONDS", "900"))
PROCESS_ID = "CardDisputeBindingProof"
POLICY = "dispute-compliance-policy.md"


def api(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(URL + path, data=data, method=method)
    req.add_header("accept", "application/json")
    if data is not None:
        req.add_header("content-type", "application/json")
    with urllib.request.urlopen(req) as r:
        raw = r.read()
        return json.loads(raw) if raw else None


def say(msg):
    print("==> " + msg, flush=True)


def deploy(*paths):
    out = subprocess.run(["c8ctl", "deploy", "--profile", PROFILE, *paths],
                         check=True, capture_output=True, text=True).stdout
    print(out.strip())
    return out


def policy_versions():
    items = api("POST", "/v2/resources/search", {
        "filter": {"resourceName": POLICY}, "sort": [{"field": "version", "order": "DESC"}],
        "page": {"limit": 100}})["items"]
    return {str(i["resourceKey"]): i["version"] for i in items}


def policy_label(key):
    with urllib.request.urlopen(f"{URL}/v2/resources/{key}/content/binary") as r:
        m = re.search(r"^Version: (v\d+)\.", r.read().decode(), re.M)
    return m.group(1) if m else "?"


def wait_task(pi, exclude=None):
    waited = 0
    while waited < TIMEOUT:
        items = api("POST", "/v2/user-tasks/search", {"filter": {
            "processInstanceKey": pi, "elementId": "ReviewDraftedDecision", "state": "CREATED"},
            "page": {"limit": 1}})["items"]
        if items and str(items[0]["userTaskKey"]) != str(exclude):
            return str(items[0]["userTaskKey"])
        inc = api("POST", "/v2/incidents/search", {"filter": {
            "processInstanceKey": pi, "state": "ACTIVE"}, "page": {"limit": 1}})["page"]["totalItems"]
        if inc:
            sys.exit(f"ERROR: incident on {pi}: {URL}/operate/processes/{pi}")
        time.sleep(5)
        waited += 5
    sys.exit(f"ERROR: no review task after {TIMEOUT}s")


def draft(task):
    items = api("POST", f"/v2/user-tasks/{task}/variables/search",
                {"filter": {"name": "draft"}, "page": {"limit": 1}})["items"]
    d = json.loads(items[0]["value"]) if items else {}
    return json.loads(d) if isinstance(d, str) else d


def main():
    src = open(os.path.join(DEMO, "card-dispute.bpmn")).read()
    variant = (src.replace('id="CardDispute"', f'id="{PROCESS_ID}"')
               .replace('bpmnElement="CardDispute"', f'bpmnElement="{PROCESS_ID}"')
               .replace('name="Card dispute"', 'name="Card dispute (binding proof)"')
               .replace('bindingType="latest"', 'bindingType="deployment"')
               .replace('key="systemPromptBinding" value="latest"',
                        'key="systemPromptBinding" value="deployment"'))
    assert variant.count('bindingType="deployment"') == 1 and PROCESS_ID in variant
    path = os.path.join(DEMO, "proof", "card-dispute-binding-proof.bpmn")
    open(path, "w").write(variant)

    say("Deploying the deployment-bound variant together with policy v1 (one deployment)")
    deploy(path, os.path.join(DEMO, "review-dispute.form"),
           os.path.join(DEMO, "policies", POLICY))
    time.sleep(15)

    say("Starting an instance")
    env = dict(os.environ, PROCESS_ID=PROCESS_ID)
    out = subprocess.run([os.path.join(DEMO, "scripts", "demo.sh"), "start"], env=env,
                         check=True, capture_output=True, text=True).stdout
    pi = re.search(r"(\d{16})", out).group(1)
    say(f"Instance {pi}: {URL}/operate/processes/{pi}")
    t1 = wait_task(pi)
    d1 = draft(t1)
    say(f"First draft: decision={d1.get('decision')} policyVersion={d1.get('policyVersion')}")

    say("Deploying policy v2 as a new latest version while the instance runs")
    deploy(os.path.join(DEMO, "policies", "v2", POLICY))
    time.sleep(15)
    after = policy_versions()
    v2_key = max(after, key=lambda k: after[k])
    say(f"Latest policy is now resource {v2_key} (version {after[v2_key]}, {policy_label(v2_key)})")

    say("Sending the draft back so the agent activates again")
    api("POST", f"/v2/user-tasks/{t1}/assignment", {"assignee": "demo", "allowOverride": True})
    api("POST", f"/v2/user-tasks/{t1}/completion", {"variables": {
        "analystVerdict": "revise",
        "analystFeedback": "Re-check the governed policy thresholds and confirm the decision."}})
    t2 = wait_task(pi, exclude=t1)
    d2 = draft(t2)
    say(f"Revised draft: decision={d2.get('decision')} policyVersion={d2.get('policyVersion')}")

    say("linkedResources header of every DisputeAgent job (engine-pinned)")
    jobs = api("POST", "/v2/jobs/search", {"filter": {"processInstanceKey": pi,
               "elementId": "DisputeAgent"}, "page": {"limit": 50}})["items"]
    pinned = set()
    for j in jobs:
        h = j.get("customHeaders", {})
        keys = [r["resourceKey"] for r in json.loads(h.get("linkedResources", "[]"))]
        pinned.update(keys)
        labels = ", ".join(f"{k} (resource version {after.get(k)}, {policy_label(k)})" for k in keys)
        print(f"   job {j['jobKey']} {j['state']:<9} binding={h.get('systemPromptBinding')} -> {labels}")

    ok = (len(pinned) == 1 and v2_key not in pinned
          and d1.get("policyVersion") == "v1" and d2.get("policyVersion") == "v1")
    say("PASS: deployment binding kept v1 after v2 was deployed" if ok else "FAIL")
    print(json.dumps({"processInstanceKey": pi, "pinnedResourceKeys": sorted(pinned),
                      "latestAfterV2": v2_key, "draft1": d1.get("policyVersion"),
                      "draft2": d2.get("policyVersion"), "decision2": d2.get("decision")}))

    subprocess.run(["c8ctl", "cancel", "pi", "--profile", PROFILE, pi], check=False)
    say("Restoring policy v1 as latest")
    deploy(os.path.join(DEMO, "policies", POLICY))
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
