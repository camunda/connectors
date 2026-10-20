import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { runParity, toJUnit } from './dist/parity.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const bundle = JSON.parse(fs.readFileSync(path.join(here, 'bundle/camunda-modeler-5.51.1/connector-templates.json'), 'utf8'));
const reference = JSON.parse(fs.readFileSync(path.join(here, 'reference/linked-prompts-agent.element-template.json'), 'utf8'));
const fixedDir = path.join(here, 'fixtures/fixed');
const fixedTemplates = path.join(fixedDir, '.camunda/element-templates');

const read = file => fs.readFileSync(file, 'utf8');

async function parity(bpmnFile, localDir) {
  const report = await runParity({ bpmnXml: read(bpmnFile), localDir, bundle, reference });

  return Object.fromEntries(report.checks.map(c => [ c.name, c ]));
}

const tempDirs = [];

test.after(() => tempDirs.forEach(dir => fs.rmSync(dir, { recursive: true, force: true })));

function tempTemplates(files) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'parity-'));
  tempDirs.push(dir);

  for (const [ name, content ] of Object.entries(files)) {
    fs.writeFileSync(path.join(dir, name), typeof content === 'string' ? content : JSON.stringify(content));
  }

  return dir;
}

test('fixed fixture passes every sub-check', async () => {
  const checks = await parity(path.join(fixedDir, 'card-dispute.bpmn'), fixedTemplates);

  for (const c of Object.values(checks)) {
    assert.equal(c.passed, true, `${c.name}: ${c.reasons.join('; ')}`);
  }
});

test('v14 fails every sub-check with the Modeler 5.51.1 findings', async () => {
  const checks = await parity(path.join(here, 'fixtures/v14/card-dispute.bpmn'), path.join(here, 'fixtures/v14/.camunda/element-templates'));

  assert.deepEqual(checks.templatesResolve.reasons, [
    'AnalyzeInSandbox: io.camunda.connectors.agenticai.sandbox.daytona.v0@1 not found in .camunda/element-templates (directory absent) or the Modeler 5.51.1 bundle',
    'Fraud_Send: dev.hansel.a2a.client.v1@1 not found in .camunda/element-templates (directory absent) or the Modeler 5.51.1 bundle'
  ]);

  assert.deepEqual(checks.lintClean.reasons, [
    'DisputeAgent: API endpoint must not be empty. (element-templates/validate)',
    'DisputeAgent: Model must not be empty. (element-templates/validate)',
    'AnalyzeInSandbox: Linked element template \'io.camunda.connectors.agenticai.sandbox.daytona.v0\' (version: 1) not found (element-templates/validate)',
    'Fraud_Send: Linked element template \'dev.hansel.a2a.client.v1\' (version: 1) not found (element-templates/validate)'
  ]);

  assert.equal(checks.linkedPrompt75.passed, false);
  assert.ok(checks.linkedPrompt75.reasons.includes('BPMN: inline data.systemPrompt.prompt input is still present'));
  assert.ok(checks.linkedPrompt75.reasons.includes('template: missing property bound to zeebe:taskHeader instructionSource'));

  assert.match(checks.serverTools83.reasons.join(), /no web-search property bound to zeebe:input/);
  assert.match(checks.savedCredential86.reasons.join(), /no property bound to zeebe:input provider\.openai\.backend\.custom\.openAiCustomCredential/);
});

test('missing applied template fails (c)-(e) with "applied template not found" and does not crash', async () => {
  const dir = tempTemplates({
    'daytona.json': read(path.join(fixedTemplates, 'daytona-sandbox.json')),
    'a2a.json': read(path.join(fixedTemplates, 'a2a-1.0-client.json'))
  });
  const checks = await parity(path.join(fixedDir, 'card-dispute.bpmn'), dir);

  assert.match(checks.templatesResolve.reasons.join(), /DisputeAgent: io\.camunda\.connectors\.agenticai\.ai-agent-subprocess\.v2@2 not found/);
  assert.deepEqual(checks.linkedPrompt75.reasons, [ 'applied template not found' ]);
  assert.deepEqual(checks.serverTools83.reasons, [ 'applied template not found' ]);
  assert.deepEqual(checks.savedCredential86.reasons, [ 'applied template not found' ]);
});

test('a local template that shadows a bundled id@version is ambiguous', async () => {
  const bundled = bundle.find(t => t.id === 'io.camunda.connectors.HttpJson.v2' && t.version === 18);
  const dir = tempTemplates({
    'http.json': { ...bundled, name: 'local copy' },
    'daytona.json': read(path.join(fixedTemplates, 'daytona-sandbox.json')),
    'a2a.json': read(path.join(fixedTemplates, 'a2a-1.0-client.json')),
    'agent.json': read(path.join(fixedTemplates, 'ai-agent-subprocess.json'))
  });
  const checks = await parity(path.join(fixedDir, 'card-dispute.bpmn'), dir);

  assert.match(checks.templatesResolve.reasons.join(), /HttpJson\.v2@18 is ambiguous: properties panel applies \.camunda\/element-templates\/http\.json, lint uses Modeler 5\.51\.1 bundle/);
});

test('an invalid local template does not count as resolved', async () => {
  const agent = JSON.parse(read(path.join(fixedTemplates, 'ai-agent-subprocess.json')));
  agent.properties.push({ type: 'NoSuchType', binding: { type: 'zeebe:input', name: 'x' } });
  const dir = tempTemplates({
    'agent.json': agent,
    'daytona.json': read(path.join(fixedTemplates, 'daytona-sandbox.json')),
    'a2a.json': read(path.join(fixedTemplates, 'a2a-1.0-client.json'))
  });
  const checks = await parity(path.join(fixedDir, 'card-dispute.bpmn'), dir);

  assert.match(checks.templatesResolve.reasons.join(), /ai-agent-subprocess\.v2@2 found but invalid for Modeler 5\.51\.1/);
  assert.deepEqual(checks.serverTools83.reasons, [ 'applied template not found' ]);
});

test('an unconditional inline prompt in the applied template fails #75', async () => {
  const agent = JSON.parse(read(path.join(fixedTemplates, 'ai-agent-subprocess.json')));
  delete agent.properties.find(p => p.id === 'data.systemPrompt.prompt').condition;
  const dir = tempTemplates({
    'agent.json': agent,
    'daytona.json': read(path.join(fixedTemplates, 'daytona-sandbox.json')),
    'a2a.json': read(path.join(fixedTemplates, 'a2a-1.0-client.json'))
  });
  const checks = await parity(path.join(fixedDir, 'card-dispute.bpmn'), dir);

  assert.deepEqual(checks.linkedPrompt75.reasons, [
    'template: property bound to zeebe:input data.systemPrompt.prompt has condition [], expected [{"equals":"inline","property":"instructionSource"}]'
  ]);
});

test('PDF parsing bound elsewhere fails #83', async () => {
  const agent = JSON.parse(read(path.join(fixedTemplates, 'ai-agent-subprocess.json')));
  agent.properties.find(p => p.binding?.name === 'provider.openai.backend.custom.bodyProperties').binding.name =
    'provider.openai.backend.custom.serverTools.pdfParsing';
  const dir = tempTemplates({
    'agent.json': agent,
    'daytona.json': read(path.join(fixedTemplates, 'daytona-sandbox.json')),
    'a2a.json': read(path.join(fixedTemplates, 'a2a-1.0-client.json'))
  });
  const checks = await parity(path.join(fixedDir, 'card-dispute.bpmn'), dir);

  assert.deepEqual(checks.serverTools83.reasons, [
    'template: no PDF-parsing property bound to zeebe:input provider.openai.backend.custom.bodyProperties'
  ]);
});

test('provider capabilities are found by bindings regardless of group id', async () => {
  const agent = JSON.parse(read(path.join(fixedTemplates, 'ai-agent-subprocess.json')));
  agent.groups.find(g => g.id === 'server-tools').id = 'provider-capabilities';
  for (const property of agent.properties) {
    if (property.group === 'server-tools') property.group = 'provider-capabilities';
  }
  const dir = tempTemplates({
    'agent.json': agent,
    'daytona.json': read(path.join(fixedTemplates, 'daytona-sandbox.json')),
    'a2a.json': read(path.join(fixedTemplates, 'a2a-1.0-client.json'))
  });

  const checks = await parity(path.join(fixedDir, 'card-dispute.bpmn'), dir);
  assert.equal(checks.serverTools83.passed, true, checks.serverTools83.reasons.join('; '));
});

test('web-search On or missing EU description fails provider parity', async () => {
  const agent = JSON.parse(read(path.join(fixedTemplates, 'ai-agent-subprocess.json')));
  const web = agent.properties.find(p => p.binding?.name === 'provider.openai.api.completions.serverTools');
  web.value = web.choices.find(c => c.value !== '').value;
  web.description = 'Enable web search';
  const dir = tempTemplates({
    'agent.json': agent,
    'daytona.json': read(path.join(fixedTemplates, 'daytona-sandbox.json')),
    'a2a.json': read(path.join(fixedTemplates, 'a2a-1.0-client.json'))
  });
  const checks = await parity(path.join(fixedDir, 'card-dispute.bpmn'), dir);
  assert.deepEqual(checks.serverTools83.reasons, [
    'template: web-search property must be visible and default to Off',
    'template: web-search property must describe the EU 403 restriction and Off setting'
  ]);
});

test('JUnit output has one escaped testcase per sub-check', async () => {
  const report = await runParity({
    bpmnXml: read(path.join(here, 'fixtures/v14/card-dispute.bpmn')),
    localDir: null,
    bundle,
    reference
  });
  const xml = toJUnit(report);

  assert.equal((xml.match(/<testcase /g) || []).length, 5);
  assert.equal((xml.match(/<failure /g) || []).length, 5);
  assert.match(xml, /Linked element template &#39;|Linked element template 'dev\.hansel/);
  assert.doesNotMatch(xml, /message="[^"]*<[^"]*"/);
});
