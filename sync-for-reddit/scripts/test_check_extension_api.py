#!/usr/bin/env python3
"""Negative/positive control for check_extension_api.py on a synthetic smali tree."""
import importlib.util, sys, tempfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("c", Path(__file__).with_name("check_extension_api.py"))
c = importlib.util.module_from_spec(spec); spec.loader.exec_module(c)

def tree(ext_body):
    d = Path(tempfile.mkdtemp())
    (d / "smali/okhttp3").mkdir(parents=True)
    (d / "smali/app/revanced/extension").mkdir(parents=True)
    (d / "smali/okhttp3/ResponseBody.smali").write_text(
        ".class public abstract Lokhttp3/ResponseBody;\n.super Ljava/lang/Object;\n"
        ".method public static final create(Ljava/lang/String;Lokhttp3/MediaType;)Lokhttp3/ResponseBody;\n.end method\n")
    (d / "smali/okhttp3/Sub.smali").write_text(".class public Lokhttp3/Sub;\n.super Lokhttp3/ResponseBody;\n")
    (d / "smali/app/revanced/extension/X.smali").write_text(
        ".class public Lapp/revanced/extension/X;\n.super Ljava/lang/Object;\n.method a()V\n" + ext_body + "\n.end method\n")
    return d

ok = tree("invoke-static {v0, v1}, Lokhttp3/Sub;->create(Ljava/lang/String;Lokhttp3/MediaType;)Lokhttp3/ResponseBody;\n"
          "sget-object v0, Ljava/util/Locale;->ROOT:Ljava/util/Locale;")
bad = tree("invoke-static {v0, v1}, Lokhttp3/ResponseBody;->create([BLokhttp3/MediaType;)Lokhttp3/ResponseBody;")
assert c.main(ok) == 0, "inherited method + platform field must resolve"
assert c.main(bad) == 1, "method missing from the minified library must fail"
print("PASS: check_extension_api self-test")
