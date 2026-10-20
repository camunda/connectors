// @camunda/linting and bpmn-js-element-templates expect browser globals; provide them before they load.
import { JSDOM } from 'jsdom';

const dom = new JSDOM('<!doctype html><html><body></body></html>');

for (const key of [ 'window', 'document', 'navigator', 'Node', 'Element', 'HTMLElement', 'DOMParser',
  'getComputedStyle', 'SVGElement', 'Event', 'CustomEvent', 'KeyboardEvent', 'MouseEvent' ]) {
  if (!(key in globalThis)) {
    try {
      globalThis[key] = key === 'window' ? dom.window : dom.window[key];
    } catch {
      // read-only global in this Node version
    }
  }
}
