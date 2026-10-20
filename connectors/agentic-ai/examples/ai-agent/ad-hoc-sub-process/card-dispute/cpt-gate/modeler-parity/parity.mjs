// Modeler parity checks for the CPT done-gate.
// Reproduces what Camunda Desktop Modeler 5.51.1 shows for card-dispute.bpmn (template resolution,
// Problems panel lint, applied-template properties) and reports one PASS/FAIL per sub-check.
import './dom.mjs';

import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

import { BpmnModdle } from 'bpmn-moddle';
import zeebeDescriptor from 'zeebe-bpmn-moddle/resources/zeebe.json';
import modelerDescriptor from 'modeler-moddle/resources/modeler.json';
import { Linter } from '@camunda/linting';
import { CloudElementTemplatesLinterPlugin, CloudElementTemplatesValidator } from 'bpmn-js-element-templates';
import { getCoerced, isCompatible } from '@bpmn-io/semver-compat';

export const MODELER_VERSION = '5.51.1';
export const AGENT_ELEMENT = 'DisputeAgent';
export const WEB_SEARCH_INPUT = 'provider.openai.api.completions.serverTools';
export const BODY_PROPERTIES_INPUT = 'provider.openai.backend.custom.bodyProperties';
export const CREDENTIAL_INPUT = 'provider.openai.backend.custom.openAiCustomCredential';
export const INLINE_PROMPT_INPUT = 'data.systemPrompt.prompt';

export const CHECKS = [
  [ 'templatesResolve', '(a) element templates resolve (demo/.camunda + Modeler 5.51.1 bundle)' ],
  [ 'lintClean', '(b) Modeler 5.51.1 lint: 0 errors' ],
  [ 'linkedPrompt75', '(c) #75 linked system prompt (instructionSource=resource, no inline prompt)' ],
  [ 'serverTools83', '(d) #83 provider capabilities (web search Off + file-parser/native PDF parsing)' ],
  [ 'savedCredential86', '(e) #86 saved-credential selector in the applied template' ]
];

const moddle = () => new BpmnModdle({ zeebe: zeebeDescriptor, modeler: modelerDescriptor });

// Same files Modeler globs: element-templates/**/*.json, dotfiles included.
export function readTemplateDir(dir) {
  if (!dir || !fs.existsSync(dir)) {
    return { exists: false, entries: [], errors: [] };
  }

  const entries = [];
  const errors = [];
  const files = fs.readdirSync(dir, { recursive: true })
    .map(String)
    .filter(f => f.endsWith('.json') && fs.statSync(path.join(dir, f)).isFile())
    .sort();

  for (const file of files) {
    const source = `.camunda/element-templates/${file}`;
    let parsed;

    try {
      parsed = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
    } catch (err) {
      errors.push(`${source} is not valid JSON (${err.message})`);
      continue;
    }

    for (const template of Array.isArray(parsed) ? parsed : [ parsed ]) {
      entries.push({ source, template });
    }
  }

  return { exists: true, entries, errors };
}

function collectFlowElements(container, out = []) {
  for (const element of container.flowElements || []) {
    out.push(element);
    collectFlowElements(element, out);
  }

  return out;
}

function extensionValues(element, type) {
  return (element.extensionElements?.values || []).filter(v => v.$type === type);
}

function inputsOf(element) {
  return extensionValues(element, 'zeebe:IoMapping').flatMap(io => io.inputParameters || []);
}

function headersOf(element) {
  return extensionValues(element, 'zeebe:TaskHeaders').flatMap(h => h.values || []);
}

function linkedResourcesOf(element) {
  return extensionValues(element, 'zeebe:LinkedResources').flatMap(l => l.values || []);
}

const key = (id, version) => `${id}@${version === undefined ? '_' : version}`;

const canonical = value => {
  if (Array.isArray(value)) {
    return value.map(canonical);
  }

  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(k => [ k, canonical(value[k]) ]));
  }

  return value;
};

const same = (a, b) => JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));

function normalizeCondition(condition) {
  if (!condition) {
    return [];
  }

  const list = condition.allMatch || [ condition ];

  return list
    .map(({ type, ...rest }) => canonical(rest))
    .sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));
}

const describeBinding = b => [ b.type, b.name || b.key || b.linkName, b.property ].filter(Boolean).join(' ');

const compact = value => String(value ?? '').replace(/\s+/g, '');

function isFileParserNative(value) {
  const v = compact(value);

  return v.includes('id:"file-parser"') && v.includes('engine:"native"');
}

/**
 * Resolve every zeebe:modelerTemplate reference the way Modeler 5.51.1 does.
 *
 * Properties panel: templates (local .camunda first, then the app bundle) pass the cloud
 * validator, which keeps the FIRST valid template per id@version.
 * Linter plugin: unvalidated cloud-schema templates, LAST one per id@version wins.
 */
function resolveTemplates({ references, local, bundle, engines }) {
  const all = [ ...local.entries, ...bundle.map(template => ({ source: 'Modeler 5.51.1 bundle', template })) ];
  const schemaFilter = new CloudElementTemplatesValidator();
  const cloud = all.filter(e => schemaFilter.isSchemaValid(e.template?.$schema));

  const validator = new CloudElementTemplatesValidator(moddle());
  validator.addAll(cloud.map(e => e.template));
  const valid = new Set(validator.getValidTemplates());

  const errorsFor = template => validator.getErrors()
    .filter(err => err.template === template || String(err.message).includes(`<${template.id}>`))
    .map(err => err.message);

  const coercedEngines = getCoerced(engines);
  const results = [];

  for (const ref of references) {
    const matches = cloud.filter(e => e.template.id === ref.templateId &&
      String(e.template.version) === String(ref.templateVersion));
    const localMatches = local.entries.filter(e => e.template?.id === ref.templateId &&
      String(e.template?.version) === String(ref.templateVersion));
    const panel = matches.find(e => valid.has(e.template));
    const linter = matches[matches.length - 1];
    const label = `${ref.elementId}: ${key(ref.templateId, ref.templateVersion)}`;
    const reasons = [];

    if (!matches.length) {
      const nonCloud = localMatches.length ? ' (local file has no Camunda 8 $schema)' : '';
      reasons.push(`${label} not found in .camunda/element-templates${local.exists ? '' : ' (directory absent)'} or the Modeler ${MODELER_VERSION} bundle${nonCloud}`);
    } else if (!panel) {
      const why = matches.flatMap(e => errorsFor(e.template));
      reasons.push(`${label} found but invalid for Modeler ${MODELER_VERSION}: ${why.join('; ') || 'rejected by validator'}`);
    } else {
      if (panel !== linter) {
        reasons.push(`${label} is ambiguous: properties panel applies ${panel.source}, lint uses ${linter.source}`);
      }

      if (!isCompatible(panel.template.engines || {}, coercedEngines)) {
        reasons.push(`${label} engines ${JSON.stringify(panel.template.engines)} incompatible with ${JSON.stringify(engines)}`);
      }
    }

    results.push({ ...ref, applied: panel ? panel.template : null, source: panel?.source, reasons });
  }

  return { results, cloudTemplates: cloud.map(e => e.template), validator };
}

function check75(element, applied, reference) {
  const reasons = [];

  if (!element) {
    return [ `element ${AGENT_ELEMENT} not found in the BPMN` ];
  }

  const header = headersOf(element).find(h => h.key === 'instructionSource');
  if (header?.value !== 'resource') {
    reasons.push(`BPMN: taskHeader instructionSource is ${header ? JSON.stringify(header.value) : 'absent'}, expected "resource"`);
  }

  const link = linkedResourcesOf(element).find(l => l.linkName === 'systemPrompt');
  if (!link) {
    reasons.push('BPMN: no zeebe:linkedResource linkName="systemPrompt"');
  } else {
    if (link.resourceType !== 'system-prompt') {
      reasons.push(`BPMN: linked resource systemPrompt has resourceType ${JSON.stringify(link.resourceType)}, expected "system-prompt"`);
    }

    if (!link.resourceId) {
      reasons.push('BPMN: linked resource systemPrompt has no resourceId');
    }
  }

  if (inputsOf(element).some(i => i.target === INLINE_PROMPT_INPUT)) {
    reasons.push(`BPMN: inline ${INLINE_PROMPT_INPUT} input is still present`);
  }

  if (!applied) {
    reasons.push('applied template not found');

    return reasons;
  }

  const required = (reference.properties || []).filter(p => {
    const b = JSON.stringify(p.binding);

    return b.includes('instructionSource') || b.includes('systemPrompt');
  });

  for (const ref of required) {
    const candidates = (applied.properties || []).filter(p => same(p.binding, ref.binding));
    const what = `property bound to ${describeBinding(ref.binding)}${ref.type === 'Hidden' ? ` (value ${JSON.stringify(ref.value)})` : ''}`;

    if (!candidates.length) {
      reasons.push(`template: missing ${what}`);
      continue;
    }

    const match = candidates.find(p => same(normalizeCondition(p.condition), normalizeCondition(ref.condition)) &&
      (ref.type !== 'Hidden' || p.value === ref.value));

    if (!match) {
      reasons.push(`template: ${what} has condition ${JSON.stringify(normalizeCondition(candidates[0].condition))}, expected ${JSON.stringify(normalizeCondition(ref.condition))}`);
      continue;
    }

    if (match.type !== ref.type) {
      reasons.push(`template: ${what} is type ${match.type}, expected ${ref.type}`);
    }

    if (ref.choices) {
      const want = ref.choices.map(c => c.value).sort();
      const got = (match.choices || []).map(c => c.value).sort();

      if (!same(want, got)) {
        reasons.push(`template: ${what} choices ${JSON.stringify(got)}, expected ${JSON.stringify(want)}`);
      }
    }
  }

  return reasons;
}

function check83(applied) {
  if (!applied) {
    return [ 'applied template not found' ];
  }

  const reasons = [];
  const props = applied.properties || [];
  const bound = name => props.filter(p => p.binding?.type === 'zeebe:input' && p.binding.name === name);

  if (!bound(WEB_SEARCH_INPUT).length) {
    reasons.push(`template: no web-search property bound to zeebe:input ${WEB_SEARCH_INPUT}`);
  } else {
    const web = bound(WEB_SEARCH_INPUT);
    if (!web.some(p => p.type !== 'Hidden' && p.value === '' &&
        (p.choices || []).some(c => c.name === 'Off' && c.value === ''))) {
      reasons.push('template: web-search property must be visible and default to Off');
    }
    if (!web.some(p => /EU/i.test(p.description || '') &&
        /403/.test(p.description || '') && /off/i.test(p.description || ''))) {
      reasons.push('template: web-search property must describe the EU 403 restriction and Off setting');
    }
  }

  const pdf = bound(BODY_PROPERTIES_INPUT);
  if (!pdf.length) {
    reasons.push(`template: no PDF-parsing property bound to zeebe:input ${BODY_PROPERTIES_INPUT}`);
  } else if (!pdf.some(p => isFileParserNative(p.value) || (p.choices || []).some(c => isFileParserNative(c.value)))) {
    reasons.push(`template: ${BODY_PROPERTIES_INPUT} property offers no file-parser plugin with pdf engine "native"`);
  }

  return reasons;
}

function check86(applied) {
  if (!applied) {
    return [ 'applied template not found' ];
  }

  const prop = (applied.properties || []).find(p => p.binding?.type === 'zeebe:input' && p.binding.name === CREDENTIAL_INPUT);

  if (!prop) {
    return [ `template: no property bound to zeebe:input ${CREDENTIAL_INPUT} (only a raw input in the XML)` ];
  }

  if (prop.type === 'Hidden') {
    return [ `template: ${CREDENTIAL_INPUT} property is Hidden, not a selector` ];
  }

  return [];
}

export async function runParity({ bpmnXml, localDir, bundle, reference, ancestorTemplateDirs = [] }) {
  const { rootElement: definitions } = await moddle().fromXML(bpmnXml);
  const executionPlatformVersion = definitions.get('modeler:executionPlatformVersion');
  const engines = { camunda: executionPlatformVersion, camundaDesktopModeler: MODELER_VERSION };

  const elements = (definitions.rootElements || [])
    .filter(r => r.$type === 'bpmn:Process')
    .flatMap(p => collectFlowElements(p));

  const references = elements
    .filter(e => e.get('zeebe:modelerTemplate'))
    .map(e => ({
      elementId: e.id,
      templateId: e.get('zeebe:modelerTemplate'),
      templateVersion: e.get('zeebe:modelerTemplateVersion')
    }));

  const local = readTemplateDir(localDir);
  const { results, cloudTemplates } = resolveTemplates({ references, local, bundle, engines });

  const linter = new Linter({
    modeler: 'desktop',
    type: 'cloud',
    plugins: [ CloudElementTemplatesLinterPlugin(cloudTemplates) ]
  });

  const lint = (await linter.lint(bpmnXml)).map(r => ({
    category: r.category,
    id: r.id,
    message: r.message,
    rule: r.rule
  }));

  const agent = elements.find(e => e.id === AGENT_ELEMENT);
  const applied = results.find(r => r.elementId === AGENT_ELEMENT)?.applied || null;

  const reasons = {
    templatesResolve: [
      ...(references.length ? [] : [ 'no zeebe:modelerTemplate references found' ]),
      ...local.errors,
      ...ancestorTemplateDirs.map(dir => `Modeler also loads ${dir}, which the gate does not snapshot`),
      ...results.flatMap(r => r.reasons)
    ],
    lintClean: lint.filter(r => r.category === 'error').map(r => `${r.id}: ${r.message} (${r.rule})`),
    linkedPrompt75: check75(agent, applied, reference),
    serverTools83: check83(applied),
    savedCredential86: check86(applied)
  };

  return {
    modelerVersion: MODELER_VERSION,
    executionPlatformVersion,
    localTemplatesDir: local.exists ? local.entries.map(e => e.source) : null,
    references: results.map(({ applied: a, reasons: _r, ...rest }) => ({ ...rest, resolved: !!a })),
    appliedAgentTemplate: applied ? key(applied.id, applied.version) : null,
    lint,
    checks: CHECKS.map(([ name, title ]) => ({
      name,
      title,
      passed: reasons[name].length === 0,
      reasons: reasons[name]
    }))
  };
}

const xmlEscape = s => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

export function toJUnit(report, className = 'modeler.ModelerParity') {
  const failures = report.checks.filter(c => !c.passed).length;
  const cases = report.checks.map(c => {
    const body = c.passed ? '' :
      `<failure message="${xmlEscape(c.reasons.join(' | '))}" type="ModelerParity">${xmlEscape(c.reasons.join('\n'))}</failure>`;

    return `  <testcase classname="${className}" name="${c.name}" time="0">${body}</testcase>`;
  });

  return `<?xml version="1.0" encoding="UTF-8"?>\n<testsuite name="${className}" tests="${report.checks.length}" failures="${failures}" errors="0" skipped="0">\n${cases.join('\n')}\n</testsuite>\n`;
}

export function formatReport(report) {
  const lines = [
    `Modeler parity (Camunda Modeler ${report.modelerVersion}, executionPlatformVersion ${report.executionPlatformVersion})`,
    `applied ${AGENT_ELEMENT} template: ${report.appliedAgentTemplate || 'not found'}`,
    'lint:'
  ];

  for (const r of report.lint) {
    lines.push(`  ${r.category.padEnd(5)} ${r.id} | ${r.message} | ${r.rule}`);
  }

  if (!report.lint.length) {
    lines.push('  (no reports)');
  }

  for (const c of report.checks) {
    lines.push(`${c.passed ? 'PASS' : 'FAIL'} ${c.name} ${c.title}`);
    c.reasons.forEach(r => lines.push(`     - ${r}`));
  }

  return lines.join('\n');
}

function arg(name) {
  const i = process.argv.indexOf(`--${name}`);

  return i > 0 ? process.argv[i + 1] : undefined;
}

// Modeler loads .camunda/element-templates from every ancestor of the diagram's folder too.
export function ancestorTemplateDirs(demoDir) {
  const found = [];

  for (let dir = path.dirname(path.resolve(demoDir)); ; dir = path.dirname(dir)) {
    const candidate = path.join(dir, '.camunda', 'element-templates');

    if (fs.existsSync(candidate)) {
      found.push(candidate);
    }

    if (path.dirname(dir) === dir) {
      return found;
    }
  }
}

async function main() {
  const bpmn = arg('bpmn');
  const report = await runParity({
    bpmnXml: fs.readFileSync(bpmn, 'utf8'),
    localDir: arg('local') || path.join(path.dirname(bpmn), '.camunda', 'element-templates'),
    bundle: JSON.parse(fs.readFileSync(arg('bundle'), 'utf8')),
    reference: JSON.parse(fs.readFileSync(arg('reference'), 'utf8')),
    ancestorTemplateDirs: arg('source-demo') ? ancestorTemplateDirs(arg('source-demo')) : []
  });

  console.log(formatReport(report));

  if (arg('junit')) {
    fs.mkdirSync(path.dirname(arg('junit')), { recursive: true });
    fs.writeFileSync(arg('junit'), toJUnit(report));
  }

  if (arg('json')) {
    fs.mkdirSync(path.dirname(arg('json')), { recursive: true });
    fs.writeFileSync(arg('json'), JSON.stringify(report, null, 2) + '\n');
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href) {
  main().catch(err => {
    console.error(`modeler parity crashed: ${err.stack || err}`);
    process.exit(2);
  });
}
