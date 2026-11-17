#!/usr/bin/env python3
"""Check structural reachability of extension hooks in apktool smali (stdlib only).

Exception edges deliberately include every instruction in each half-open try
range. This is a conservative CFG check, not register/value or exception analysis.
Existing dead code is discounted by a multiset of original dead instructions;
branch labels are normalized because apktool renumbers them after patching.
"""
import argparse
from collections import Counter
from dataclasses import dataclass
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]   # sync-for-reddit/
EXTENSION = 'Lapp/revanced/extension/'
LABEL = r':[\w$-]+'
LABEL_RE = re.compile(LABEL)
CATCH_RE = re.compile(r'\{\s*(' + LABEL + r')\s*\.\.\s*(' + LABEL + r')\s*\}\s*(' + LABEL + r')')
# Remove comments without treating a # inside a quoted string as a comment.
COMMENT_RE = re.compile(r'"(?:\\.|[^"\\])*"|(#.*$)')


def clean(line):
    return COMMENT_RE.sub(lambda m: '' if m.group(1) else m.group(0), line).strip()


@dataclass
class Method:
    signature: str
    lines: list


def methods(text):
    current = None
    for number, raw in enumerate(text.splitlines(), 1):
        line = clean(raw)
        if line.startswith('.method '):
            current = Method(line.split()[-1], [])
        elif line == '.end method':
            if current is not None:
                yield current
            current = None
        elif current is not None:
            current.lines.append((number, line))


def is_extension(instruction):
    return instruction.startswith('invoke-') and EXTENSION in instruction


def graph(method):
    instructions, labels, payloads, catches = [], {}, {}, []
    pending = []
    annotation_depth = 0
    payload = None
    for number, line in method.lines:
        if not line:
            continue
        if line.startswith(('.annotation ', '.subannotation ')):
            annotation_depth += 1
            continue
        if line in ('.end annotation', '.end subannotation'):
            annotation_depth -= 1
            continue
        if annotation_depth:
            continue
        if payload is not None:
            if line.startswith('.end '):
                payload = None
            elif payload[0] != '.array-data':
                payload[1].extend(LABEL_RE.findall(line))
            continue
        if line.startswith(':'):
            name = line.split()[0]
            labels[name] = len(instructions)
            pending.append(name)
            continue
        if line.startswith(('.packed-switch ', '.sparse-switch', '.array-data ')):
            targets = []
            for name in pending:
                payloads[name] = targets
            payload = (line.split()[0], targets)
            pending = []
            continue
        if line.startswith(('.catch ', '.catchall ')):
            match = CATCH_RE.search(line)
            if not match:
                raise ValueError(f'bad catch at line {number}: {line}')
            catches.append(match.groups())
        if line.startswith('.'):
            continue
        pending = []
        instructions.append((number, line))

    edges = [set() for _ in instructions]

    def target(label):
        if label not in labels:
            raise ValueError(f'unknown label {label}')
        index = labels[label]
        if index >= len(instructions) or label in payloads:
            raise ValueError(f'non-instruction branch target {label}')
        return index

    for i, (_, line) in enumerate(instructions):
        opcode = line.split()[0]
        if opcode.startswith('goto'):
            edges[i].add(target(line.rsplit(None, 1)[-1]))
            continue
        if opcode.startswith('if-'):
            edges[i].add(target(line.rsplit(None, 1)[-1]))
        if opcode in ('packed-switch', 'sparse-switch'):
            table = line.rsplit(None, 1)[-1]
            if table not in payloads:
                raise ValueError(f'unknown switch payload {table}')
            edges[i].update(target(label) for label in payloads[table])
        if not (opcode.startswith('return') or opcode == 'throw') and i + 1 < len(edges):
            edges[i].add(i + 1)
    for start, end, handler in catches:
        if start not in labels or end not in labels:
            raise ValueError(f'unknown try range {start} .. {end}')
        handler_index = target(handler)
        for i in range(labels[start], labels[end]):
            edges[i].add(handler_index)
    reachable = set()
    work = [0] if instructions else []
    while work:
        i = work.pop()
        if i not in reachable:
            reachable.add(i)
            work.extend(edges[i] - reachable)
    return instructions, reachable


def normalized(instruction):
    # Preserve strings/descriptors, including colons inside string constants.
    return re.sub(r'"(?:\\.|[^"\\])*"|' + LABEL,
                  lambda m: m.group(0) if m.group(0).startswith('"') else ':LABEL',
                  ' '.join(instruction.split()))


def dead_original(method):
    if method is None:
        return Counter()
    instructions, reachable = graph(method)
    return Counter(normalized(line) for i, (_, line) in enumerate(instructions)
                   if i not in reachable and line != 'nop')


def class_files(root):
    # Ignore dex placement: a patch can move a class to another classesN.dex.
    for file in sorted(root.rglob('*.smali')):
        relative = file.relative_to(root)
        parts = relative.parts
        if parts[0].startswith('smali'):
            parts = parts[1:]
        if parts[:2] != ('app', 'revanced'):
            yield '/'.join(parts), file


def replaced_at_entry(instructions):
    """True if the method starts with a straight-line block that calls the extension and returns.

    That is a deliberate whole-method replacement (a patch inserting `invoke ... ; return` at index 0),
    so the original body after it is expected to be dead. Labels, branches or try blocks end the scan.
    """
    called = False
    for _, line in instructions:
        op = line.split()[0] if line.split() else ''
        if is_extension(line):
            called = True
        elif op.startswith('return'):
            return called
        elif op.startswith(('if-', 'goto', 'switch', 'packed-switch', 'sparse-switch', 'throw')) or line.startswith((':', '.catch')):
            return False
    return False


def check(root, original):
    originals = dict(class_files(original)) if original.is_dir() else {}
    checked, problems = 0, []
    for class_path, file in class_files(root):
        text = file.read_text(encoding='utf-8')
        if EXTENSION not in text:
            continue
        original_methods = None
        for method in methods(text):
            if not any(is_extension(line) for _, line in method.lines):
                continue
            checked += 1
            try:
                instructions, reachable = graph(method)
                if original_methods is None:
                    old_file = originals.get(class_path)
                    original_methods = ({m.signature: m for m in methods(old_file.read_text(encoding='utf-8'))}
                                        if old_file else {})
                baseline = dead_original(original_methods.get(method.signature))
                replaced = replaced_at_entry(instructions)
                for i, (number, line) in enumerate(instructions):
                    if i in reachable or line == 'nop':
                        continue
                    if replaced and not is_extension(line):
                        continue   # whole method replaced on purpose: its original body is dead
                    key = normalized(line)
                    if not is_extension(line) and baseline[key]:
                        baseline[key] -= 1
                        continue
                    kind = 'extension invoke' if is_extension(line) else 'instruction'
                    problems.append(f'{file}:{number}: {method.signature}: unreachable {kind}: {line}')
            except ValueError as error:
                problems.append(f'{file}: {method.signature}: CFG error: {error}')
    return checked, problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('smali_dir', nargs='?', type=Path, default=ROOT / 'out/patched-smali')
    parser.add_argument('--original', type=Path, default=ROOT / 'decompiled/sync-23.06.30-smali')
    args = parser.parse_args()
    if not args.smali_dir.is_dir() or not any(args.smali_dir.rglob('*.smali')):
        parser.error(f'no smali files in {args.smali_dir}')
    checked, problems = check(args.smali_dir, args.original)
    for problem in problems:
        print(problem)
    print(f'checked {checked} methods with extension calls; {len(problems)} unreachable')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
