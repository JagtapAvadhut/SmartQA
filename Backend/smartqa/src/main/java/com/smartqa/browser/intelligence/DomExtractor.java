package com.smartqa.browser.intelligence;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Frame;
import com.smartqa.debug.DomTraceStats;
import com.smartqa.debug.TraceLogger;
import com.smartqa.debug.TraceMeta;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DomExtractor {

    static final String EXTRACTION_JS = """
            (meta) => {
              const interactiveSelector = 'a, button, input, select, textarea, [role], [tabindex], [onclick], [data-testid], [data-cy], [data-qa], [data-automation-id], [aria-label], [title], [class*="icon"], [class*="Icon"], h1, h2, h3, h4, label, img, svg';
              const containerSelector = 'form, fieldset, section, article, nav, header, footer, aside, dialog, main, table, tr, [role="dialog"], [role="search"], [role="tabpanel"], [role="menu"], [role="navigation"], [role="complementary"], [role="banner"], [role="contentinfo"], [aria-modal="true"], [aria-label*="filter" i], [class*="filter" i], [class*="facet" i], [class*="overlay" i], [class*="modal" i], [class*="drawer" i]';
              const intern = new WeakMap();
              const internList = [];
              const internId = (el, kind) => {
                if (!el || el.nodeType !== 1) return '';
                if (intern.has(el)) return intern.get(el);
                const id = (meta?.framePath || 'main') + '-el-' + internList.length;
                intern.set(el, id);
                internList.push({ el, id, kind: kind || 'INTERACTIVE' });
                return id;
              };
              const nearestInterned = (el) => {
                let cur = el ? el.parentElement : null;
                while (cur) {
                  if (intern.has(cur)) return intern.get(cur);
                  const root = cur.getRootNode();
                  cur = root instanceof ShadowRoot ? root.host : cur.parentElement;
                }
                return '';
              };
              const seen = new Set();
              const out = [];
              const textOf = (el) => (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim().slice(0, 120);
              const nearby = (el) => {
                const parent = el.closest('label, li, tr, section, form, nav, header, aside, div');
                return parent ? textOf(parent).slice(0, 80) : '';
              };
              const regionOf = (el) => {
                if (el.closest('[role="dialog"], dialog[open], [aria-modal="true"]')) return 'DIALOG';
                if (el.closest('[role="search"], form[role="search"]')) return 'SEARCH_AREA';
                if (el.closest('aside, [role="complementary"], [aria-label*="filter" i], [aria-label*="refine" i]')) return 'FILTER_PANEL';
                if (el.closest('header, [role="banner"]')) return 'HEADER';
                if (el.closest('nav, [role="navigation"]')) return 'NAVIGATION';
                if (el.closest('footer, [role="contentinfo"]')) return 'FOOTER';
                if (el.closest('main, [role="main"]')) return 'MAIN';
                const blob = ((el.className && typeof el.className === 'string') ? el.className : '')
                  + ' ' + (el.getAttribute('aria-label') || '') + ' ' + (el.id || '');
                const lower = blob.toLowerCase();
                if (lower.includes('filter') || lower.includes('facet') || lower.includes('sidebar')) return 'FILTER_PANEL';
                if (inHeaderRegion(el)) return 'HEADER';
                return 'CONTENT';
              };
              const headingContext = (el) => {
                let cur = el;
                let guard = 0;
                while (cur && guard < 8) {
                  const heading = cur.querySelector
                    ? cur.querySelector('h1,h2,h3,h4,[role="heading"],legend,summary,[class*="title" i],[class*="heading" i]')
                    : null;
                  if (heading) {
                    const t = textOf(heading).slice(0, 80);
                    if (t) return t;
                  }
                  const prev = cur.previousElementSibling;
                  if (prev) {
                    const tag = (prev.tagName || '').toLowerCase();
                    const role = (prev.getAttribute('role') || '').toLowerCase();
                    if (['h1','h2','h3','h4','legend','summary'].includes(tag) || role === 'heading') {
                      const t = textOf(prev).slice(0, 80);
                      if (t) return t;
                    }
                  }
                  cur = cur.parentElement;
                  guard += 1;
                }
                return '';
              };
              const ancestorContext = (el) => {
                const parts = [];
                let cur = el.parentElement;
                let guard = 0;
                while (cur && cur.nodeType === 1 && guard < 6) {
                  const tag = (cur.tagName || '').toLowerCase();
                  if (!['html','body','script','style'].includes(tag)) {
                    const role = cur.getAttribute('role') || '';
                    const label = cur.getAttribute('aria-label') || '';
                    const text = textOf(cur).slice(0, 40);
                    const bit = [tag, role, label, text].filter(Boolean).join(' ').slice(0, 60);
                    if (bit) parts.push(bit);
                  }
                  const root = cur.getRootNode();
                  cur = root instanceof ShadowRoot ? root.host : cur.parentElement;
                  guard += 1;
                }
                return parts.join(' | ').slice(0, 200);
              };
              const siblingContext = (el) => {
                const parent = el.parentElement;
                if (!parent) return '';
                return Array.from(parent.children)
                  .filter(c => c !== el)
                  .slice(0, 4)
                  .map(c => textOf(c).slice(0, 30))
                  .filter(Boolean)
                  .join(' | ')
                  .slice(0, 120);
              };
              const enrich = (el, base) => {
                const parent = el.parentElement;
                const container = el.closest('form, [role="dialog"], dialog, aside, fieldset, section, nav, header, [role="search"], [aria-label*="filter" i], [class*="filter" i], [class*="card" i], tr, table, [role="menu"], [role="tabpanel"]');
                const ancestorIds = [];
                let cur = parent;
                let g = 0;
                while (cur && g < 8) {
                  if (intern.has(cur)) ancestorIds.push(intern.get(cur));
                  cur = cur.parentElement;
                  g += 1;
                }
                const siblingEls = parent ? Array.from(parent.children).filter(c => c !== el && intern.has(c)) : [];
                const siblingIds = siblingEls.map(c => intern.get(c)).filter(Boolean).slice(0, 8);
                const prev = el.previousElementSibling;
                const next = el.nextElementSibling;
                const internedChildren = Array.from(el.children || []).filter(c => intern.has(c)).map(c => intern.get(c));
                const form = el.closest('form');
                const root = el.getRootNode();
                const containerId = intern.has(container) ? intern.get(container) : nearestInterned(container || el);
                const parentId = intern.has(parent) ? intern.get(parent) : nearestInterned(el);
                const style = window.getComputedStyle(el);
                const zIndex = parseInt(style.zIndex, 10);
                return Object.assign(base, {
                  region: regionOf(el),
                  headingContext: headingContext(el),
                  ancestorContext: ancestorContext(el),
                  siblingContext: siblingContext(el),
                  parentTag: parent ? (parent.tagName || '').toLowerCase() : '',
                  ariaExpanded: el.getAttribute('aria-expanded') === 'true',
                  parentId: parentId || '',
                  containerId: containerId || '',
                  ancestorIds: ancestorIds.join(','),
                  siblingIds: siblingIds.join(','),
                  childIds: internedChildren.join(','),
                  previousSiblingId: intern.has(prev) ? intern.get(prev) : '',
                  nextSiblingId: intern.has(next) ? intern.get(next) : '',
                  formId: form && intern.has(form) ? intern.get(form) : '',
                  altText: el.getAttribute('alt') || '',
                  ariaDescribedBy: el.getAttribute('aria-describedby') || '',
                  labelledByIds: el.getAttribute('aria-labelledby') || '',
                  describedByIds: el.getAttribute('aria-describedby') || '',
                  zIndex: Number.isFinite(zIndex) ? zIndex : 0,
                  frameId: meta?.framePath || 'main',
                  shadowRootId: root instanceof ShadowRoot ? internId(root.host, 'CONTAINER') : '',
                  inventoryKind: base.inventoryKind || 'INTERACTIVE'
                });
              };
              const labelFor = (el) => {
                if (!el) return '';
                if (el.id) {
                  const labelled = document.querySelector('label[for="' + CSS.escape(el.id) + '"]');
                  if (labelled) return textOf(labelled).slice(0, 80);
                }
                const wrapped = el.closest('label');
                return wrapped ? textOf(wrapped).slice(0, 80) : '';
              };
              const cssPath = (el) => {
                const parts = [];
                let current = el;
                let guard = 0;
                while (current && current.nodeType === 1 && guard < 16) {
                  let part = current.tagName.toLowerCase();
                  if (current.id) {
                    part += '#' + CSS.escape(current.id);
                    parts.unshift(part);
                    break;
                  }
                  const parent = current.parentElement;
                  if (parent) {
                    const same = Array.from(parent.children).filter(c => c.tagName === current.tagName);
                    if (same.length > 1) {
                      const index = same.indexOf(current) + 1;
                      part += ':nth-of-type(' + index + ')';
                    }
                  }
                  parts.unshift(part);
                  const root = current.getRootNode();
                  if (root instanceof ShadowRoot) {
                    current = root.host;
                  } else {
                    current = current.parentElement;
                  }
                  guard += 1;
                }
                return parts.join(' > ').slice(0, 240);
              };
              const findAssociatedControl = (el) => {
                const tag = (el.tagName || '').toLowerCase();
                if (tag !== 'label') return { selector: '', tag: '', role: '' };
                const forAttr = el.getAttribute('for');
                if (forAttr) {
                  const target = document.getElementById(forAttr);
                  if (target) {
                    return {
                      selector: '#' + forAttr,
                      tag: (target.tagName || '').toLowerCase(),
                      role: target.getAttribute('role') || ''
                    };
                  }
                }
                const inner = el.querySelector('input, select, textarea, [role="combobox"], [role="listbox"]');
                if (inner) {
                  const iTag = (inner.tagName || '').toLowerCase();
                  const iId = inner.id ? '#' + inner.id : '';
                  return { selector: iId || iTag, tag: iTag, role: inner.getAttribute('role') || '' };
                }
                const parent = el.parentElement;
                if (parent) {
                  const sibling = parent.querySelector(
                    'input, select, textarea, [role="combobox"], [role="listbox"], [role="button"][aria-haspopup], [aria-expanded]'
                  );
                  if (sibling && sibling !== el) {
                    const sTag = (sibling.tagName || '').toLowerCase();
                    const sId = sibling.id ? '#' + sibling.id : '';
                    const sTestId = sibling.getAttribute('data-testid');
                    const sel = sId || (sTestId ? '[data-testid="' + sTestId + '"]' : '');
                    return { selector: sel || sTag, tag: sTag, role: sibling.getAttribute('role') || '' };
                  }
                  const container = parent.closest('[class*="group"], [class*="field"], [class*="form"], [class*="input"], [class*="select"]');
                  if (container) {
                    const ctrl = container.querySelector(
                      'input, select, textarea, [role="combobox"], [role="listbox"], [role="button"][aria-haspopup], [aria-expanded], [class*="select"]:not(label)'
                    );
                    if (ctrl && ctrl !== el && ctrl.tagName.toLowerCase() !== 'label') {
                      const cTag = (ctrl.tagName || '').toLowerCase();
                      const cId = ctrl.id ? '#' + ctrl.id : '';
                      return { selector: cId || cTag, tag: cTag, role: ctrl.getAttribute('role') || '' };
                    }
                  }
                }
                return { selector: '', tag: '', role: '' };
              };
              const isClickable = (el) => {
                if (!el) return false;
                const tag = (el.tagName || '').toLowerCase();
                const role = (el.getAttribute('role') || '').toLowerCase();
                const tabIndex = el.getAttribute('tabindex');
                const cursor = window.getComputedStyle(el).cursor;
                if (['a', 'button', 'input', 'select', 'textarea'].includes(tag)) return true;
                if (['button', 'link', 'tab', 'menuitem', 'checkbox', 'radio'].includes(role)) return true;
                if (tabIndex !== null && Number(tabIndex) >= 0) return true;
                return !!el.getAttribute('onclick') || cursor === 'pointer';
              };
              const inHeaderRegion = (el) => {
                const r = el.getBoundingClientRect();
                return r.top < Math.max(window.innerHeight * 0.35, 280);
              };
              const emit = (el, kind, shadowChain) => {
                  const style = window.getComputedStyle(el);
                  if (style.display === 'none' || style.visibility === 'hidden' || Number(style.opacity) === 0) {
                    return;
                  }
                  const rect = el.getBoundingClientRect();
                  if (rect.width < 2 && rect.height < 2 && kind !== 'CONTAINER') {
                    return;
                  }
                  const tag = (el.tagName || '').toLowerCase();
                  if (tag === 'script' || tag === 'style' || tag === 'path') {
                    return;
                  }
                  const id = el.id || '';
                  const key = (meta?.framePath || 'main') + '|' + cssPath(el) + '|' + (shadowChain.length ? shadowChain.join('>') : 'none');
                  if (seen.has(key)) {
                    return;
                  }
                  seen.add(key);
                  const cid = internId(el, kind);
                  const role = el.getAttribute('role') || '';
                  const assoc = findAssociatedControl(el);
                  let actionable = el.closest('button, a, [role="button"], [role="link"], [role="menuitem"], [tabindex], [onclick]');
                  if (!actionable) {
                    let cur = el;
                    let guard = 0;
                    while (cur && cur.nodeType === 1 && guard < 10) {
                      if (isClickable(cur)) {
                        actionable = cur;
                        break;
                      }
                      cur = cur.parentElement;
                      guard += 1;
                    }
                  }
                  if (!actionable && isClickable(el)) {
                    actionable = el;
                  }
                  const hasIcon = !!(el.querySelector('svg, img, [role="img"]') || el.closest('svg, img, [role="img"]'));
                  out.push(enrich(el, {
                    candidateId: cid,
                    inventoryKind: kind,
                    tag,
                    role,
                    accessibleName: el.getAttribute('aria-label') || textOf(el),
                    text: textOf(el),
                    label: labelFor(el),
                    ariaLabel: el.getAttribute('aria-label') || '',
                    ariaLabelledBy: el.getAttribute('aria-labelledby') || '',
                    placeholder: el.getAttribute('placeholder') || '',
                    title: el.getAttribute('title') || '',
                    name: el.getAttribute('name') || '',
                    id,
                    className: (el.className && typeof el.className === 'string') ? el.className.slice(0, 100) : '',
                    testId: el.getAttribute('data-testid') || el.getAttribute('data-cy') || el.getAttribute('data-qa') || el.getAttribute('data-automation-id') || '',
                    href: el.getAttribute('href') || '',
                    inputType: el.getAttribute('type') || '',
                    value: (el.value ?? el.getAttribute('value') ?? '').toString().slice(0, 80),
                    visible: true,
                    enabled: !el.disabled && el.getAttribute('aria-disabled') !== 'true',
                    disabled: !!el.disabled || el.getAttribute('aria-disabled') === 'true',
                    checked: !!el.checked || el.getAttribute('aria-checked') === 'true',
                    selected: !!el.selected || el.getAttribute('aria-selected') === 'true',
                    readOnly: !!el.readOnly || el.getAttribute('readonly') !== null || el.getAttribute('aria-readonly') === 'true',
                    required: !!el.required || el.getAttribute('required') !== null || el.getAttribute('aria-required') === 'true',
                    boundingBox: [Math.round(rect.x), Math.round(rect.y), Math.round(rect.width), Math.round(rect.height)].join(','),
                    iframeContext: meta?.framePath || 'main',
                    frameUrl: meta?.frameUrl || '',
                    frameName: meta?.frameName || '',
                    parentFrameContext: meta?.parentFramePath || '',
                    shadowContext: shadowChain.length ? shadowChain.join(' > ') : '',
                    targetPath: cssPath(el),
                    parentContext: nearby(el),
                    nearbyText: nearby(el),
                    associatedControlSelector: assoc.selector,
                    associatedControlTag: assoc.tag,
                    associatedControlRole: assoc.role,
                    clickable: isClickable(el),
                    inHeaderRegion: inHeaderRegion(el),
                    hasIcon: hasIcon,
                    actionableSelector: actionable ? cssPath(actionable) : '',
                    actionableTag: actionable ? (actionable.tagName || '').toLowerCase() : '',
                    actionableRole: actionable ? (actionable.getAttribute('role') || '') : ''
                  }));
              };
              const collect = (root, shadowChain) => {
                let containerCount = internList.filter(x => x.kind === 'CONTAINER').length;
                const containers = Array.from(root.querySelectorAll(containerSelector));
                for (const el of containers) {
                  if (containerCount >= 250) break;
                  internId(el, 'CONTAINER');
                  containerCount += 1;
                }
                const nodes = Array.from(root.querySelectorAll(interactiveSelector));
                for (const el of nodes) {
                  internId(el, intern.has(el) ? internList.find(x => x.el === el)?.kind || 'INTERACTIVE' : 'INTERACTIVE');
                }
                for (const el of containers) {
                  if (out.length >= 650) return;
                  emit(el, 'CONTAINER', shadowChain);
                }
                for (const el of nodes) {
                  if (out.length >= 650) return;
                  emit(el, intern.has(el) && internList.find(x => x.el === el)?.kind === 'CONTAINER' ? 'CONTAINER' : 'INTERACTIVE', shadowChain);
                }
                // Short-text pointer leaves: custom menu/popover items without roles.
                const extras = Array.from(root.querySelectorAll('div, span'));
                for (const el of extras) {
                  if (out.length >= 650) return;
                  const style = window.getComputedStyle(el);
                  if (style.cursor !== 'pointer') continue;
                  const text = textOf(el);
                  if (!text || text.length > 40) continue;
                  internId(el, 'INTERACTIVE');
                  emit(el, 'INTERACTIVE', shadowChain);
                }
                const all = Array.from(root.querySelectorAll('*'));
                for (const maybeHost of all) {
                  if (maybeHost.shadowRoot) {
                    collect(maybeHost.shadowRoot, [...shadowChain, cssPath(maybeHost)]);
                  }
                }
              };
              collect(document, []);
              return out;
            }
            """;

    @SuppressWarnings("unchecked")
    public List<ElementCandidate> extract(Page page) {
        List<ElementCandidate> elements = new ArrayList<>();
        Map<Frame, String> framePaths = framePaths(page);
        for (Frame frame : page.frames()) {
            Map<String, String> meta = new HashMap<>();
            String framePath = framePaths.getOrDefault(frame, "main");
            meta.put("framePath", framePath);
            meta.put("frameUrl", safe(frame.url()));
            meta.put("frameName", safe(frame.name()));
            Frame parent = frame.parentFrame();
            meta.put("parentFramePath", parent == null ? "" : framePaths.getOrDefault(parent, ""));
            Object raw = frame.evaluate(EXTRACTION_JS, meta);
            if (!(raw instanceof List<?> list)) {
                continue;
            }
            int index = elements.size();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    ElementCandidate candidate = ElementCandidate.fromMap((Map<String, Object>) map, index++);
                    ControlType type = ControlClassifier.classifyFromCandidate(candidate);
                    elements.add(candidate.withStructure(candidate.structureOrEmpty().withControlType(type.name())));
                }
            }
        }
        TraceLogger.info("DOM", "DOM_REDUCTION_STARTED", "Reducing live DOM", TraceMeta.of(
                "totalNodes", elements.size(),
                "url", page.url()
        ));
        TraceLogger.info("DOM", "DOM_REDUCTION_COMPLETED", "DOM reduction completed", TraceMeta.of(
                "remainingNodes", elements.size(),
                "interactiveElements", elements.size(),
                "removedHidden", true,
                "removedScripts", true,
                "removedStyles", true,
                "removedSvgInternals", true
        ));
        TraceLogger.debug("DOM", "DOM_SNAPSHOT", "Reduced DOM snapshot", DomTraceStats.summarize(page.url(), elements));
        return elements;
    }

    private Map<Frame, String> framePaths(Page page) {
        Map<Frame, String> paths = new HashMap<>();
        Frame main = page.mainFrame();
        paths.put(main, "main");
        annotateChildren(main, "main", paths);
        return paths;
    }

    private void annotateChildren(Frame parent, String parentPath, Map<Frame, String> paths) {
        List<Frame> children = parent.childFrames();
        for (int i = 0; i < children.size(); i++) {
            Frame child = children.get(i);
            String childPath = parentPath + "/" + i;
            paths.put(child, childPath);
            annotateChildren(child, childPath, paths);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
