"""Dump NTM DE c: item tags as form/material -> items (for building the GT mapping)."""
import json, re, sys, zipfile

jar = zipfile.ZipFile(sys.argv[1])
rx = re.compile(r"data/c/tags/(item|block)/([a-z_]+)/([a-z0-9_]+)\.json$")
for name in sorted(jar.namelist()):
    m = rx.match(name)
    if not m:
        continue
    kind, form, mat = m.groups()
    vals = json.loads(jar.read(name)).get("values", [])
    vals = [v if isinstance(v, str) else v["id"] + ("?" if not v.get("required", True) else "") for v in vals]
    print(f"{kind}\t{form}\t{mat}\t{' '.join(vals)}")
