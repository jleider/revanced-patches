#!/usr/bin/env python3
"""Fail if the patch extension calls a method or field that doesn't exist in the patched app.

The extension is compiled against upstream libraries (OkHttp 5.3.2, org.json, ...), but Sync ships its
own, minified copies (OkHttp 5.0.0-alpha.8, okio renamed to ae.*). A call that compiles fine can be
missing at runtime and only crash when that code path runs (NoSuchMethodError). This resolves every
method/field reference made from app/revanced/extension/** against the patched APK's own classes,
walking superclasses and interfaces. Platform classes (java., javax., android., dalvik., org.json.,
org.w3c., org.xml.) are provided by the OS and skipped.

    scripts/check_extension_api.py <patched-smali-dir>
"""
import re
import sys
from pathlib import Path

PLATFORM = ("Ljava/", "Ljavax/", "Landroid/", "Ldalvik/", "Lorg/json/", "Lorg/w3c/", "Lorg/xml/", "Lsun/",
            "Llibcore/", "Lorg/apache/http/")
OBJECT_METHODS = {"equals", "hashCode", "toString", "getClass", "notify", "notifyAll", "wait", "clone", "finalize"}
OP = re.compile(r"^(invoke-|[si](?:get|put))")
REF = re.compile(r"(\[*L[^;\s{},]+;)->([^\s(:]+)(\([^)]*\)\S+|:\S+)")


def index(root):
    classes = {}
    for f in root.glob("smali*/**/*.smali"):
        text = f.read_text(errors="replace")
        m = re.search(r"^\.class [^\n]*?(L[^;]+;)", text, re.M)
        if not m:
            continue
        name = m.group(1)
        sup = re.search(r"^\.super (L[^;]+;)", text, re.M)
        ifaces = re.findall(r"^\.implements (L[^;]+;)", text, re.M)
        methods = set(re.findall(r"^\.method (?:[^\n]* )?([^\s(]+\([^)]*\)\S+)$", text, re.M))
        fields = set(re.findall(r"^\.field (?:[^\n]*? )?([^\s:=]+:[^\s=]+)(?: = .*)?$", text, re.M))
        classes[name] = (sup.group(1) if sup else None, ifaces, methods, fields, f)
    return classes


def resolves(classes, cls, member, seen=None):
    seen = seen or set()
    if cls in seen:
        return False
    seen.add(cls)
    if cls == "Ljava/lang/Object;":
        return member.split("(")[0] in OBJECT_METHODS
    if cls.startswith(PLATFORM):
        return True            # inherited from an OS class (Activity, InputStream, ...); out of scope
    c = classes.get(cls)
    if c is None:
        return False
    sup, ifaces, methods, fields, _ = c
    if member in methods or member in fields:
        return True
    return any(resolves(classes, p, member, seen) for p in ([sup] if sup else []) + ifaces)


def main(root):
    root = Path(root)
    classes = index(root)
    missing = set()
    checked = 0
    for name, (_, _, _, _, f) in classes.items():
        if not name.startswith("Lapp/revanced/extension/"):
            continue
        for line in f.read_text(errors="replace").splitlines():
            line = line.strip()
            if not OP.match(line):
                continue
            m = REF.search(line)
            if not m:
                continue
            cls, member, sig = m.groups()
            cls = cls.lstrip("[")
            if cls.startswith("[") or not cls.startswith("L") or cls.startswith(PLATFORM):
                continue
            checked += 1
            full = member + sig   # "name(args)ret" or "name:type"
            if not resolves(classes, cls, full):
                missing.add(f"{cls}->{full}   (from {name})")
    print(f"checked {checked} extension references into app/library classes; {len(missing)} missing")
    for m in sorted(missing):
        print("  MISSING", m)
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "out/patched-smali"))
