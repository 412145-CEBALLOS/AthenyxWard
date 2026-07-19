export interface LegalMeta {
  version: string;
  date: string;
}

export type BlockKind = 'heading1' | 'heading2' | 'heading3' | 'paragraph' | 'orderedList' | 'unorderedList';

export interface LegalBlock {
  kind: BlockKind;
  content: string;
  items?: string[];
}

export interface ParsedLegal {
  meta: LegalMeta;
  blocks: LegalBlock[];
}

function parseFrontmatter(lines: string[]): { meta: LegalMeta; bodyStartIdx: number } {
  if (lines[0]?.trim() !== '---') {
    return { meta: { version: '', date: '' }, bodyStartIdx: 0 };
  }
  let endIdx = 1;
  while (endIdx < lines.length && lines[endIdx]?.trim() !== '---') {
    endIdx++;
  }
  const raw = lines.slice(1, endIdx).join('\n');
  const meta: LegalMeta = { version: '', date: '' };
  for (const line of raw.split('\n')) {
    const colonIdx = line.indexOf(':');
    if (colonIdx === -1) continue;
    const key = line.slice(0, colonIdx).trim();
    const value = line.slice(colonIdx + 1).trim();
    if (key === 'version') meta.version = value;
    if (key === 'date') meta.date = value;
  }
  return { meta, bodyStartIdx: endIdx + 1 };
}

function processInline(text: string): string {
  return text.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
}

function parseBody(lines: string[]): LegalBlock[] {
  const blocks: LegalBlock[] = [];
  let currentParagraph = '';
  let currentListKind: BlockKind | null = null;
  let currentListItems: string[] = [];

  function flushParagraph() {
    const trimmed = currentParagraph.trim();
    if (trimmed) {
      blocks.push({ kind: 'paragraph', content: processInline(trimmed) });
    }
    currentParagraph = '';
  }

  function flushList() {
    if (currentListItems.length > 0) {
      blocks.push({ kind: currentListKind as BlockKind, content: '', items: [...currentListItems] });
    }
    currentListKind = null;
    currentListItems = [];
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const trimmed = line.trim();

    if (!trimmed) {
      flushParagraph();
      flushList();
      continue;
    }

    if (trimmed.startsWith('## ')) {
      flushParagraph();
      flushList();
      blocks.push({ kind: 'heading2', content: processInline(trimmed.slice(3).trim()) });
      continue;
    }

    if (trimmed.startsWith('### ')) {
      flushParagraph();
      flushList();
      blocks.push({ kind: 'heading3', content: processInline(trimmed.slice(4).trim()) });
      continue;
    }

    if (/^\d+\.\s/.test(trimmed)) {
      flushParagraph();
      if (currentListKind !== 'orderedList') { flushList(); currentListKind = 'orderedList'; }
      currentListItems.push(processInline(trimmed.replace(/^\d+\.\s/, '')));
      continue;
    }

    if (trimmed.startsWith('- ')) {
      flushParagraph();
      if (currentListKind !== 'unorderedList') { flushList(); currentListKind = 'unorderedList'; }
      currentListItems.push(processInline(trimmed.slice(2).trim()));
      continue;
    }

    flushList();
    currentParagraph += (currentParagraph ? '\n' : '') + trimmed;
  }

  flushParagraph();
  flushList();
  return blocks;
}

export function parseLegalMarkdown(raw: string): ParsedLegal {
  const lines = raw.split(/\r?\n/);
  const { meta, bodyStartIdx } = parseFrontmatter(lines);
  const bodyLines = lines.slice(bodyStartIdx);
  const blocks = parseBody(bodyLines);
  return { meta, blocks };
}
