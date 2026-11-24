#!/usr/bin/env python3
"""Fluid tags for GtFluidLinks: GT recipes find fluids by tag c:<fluid path>, other mods by c:<material name>.
Writes data/c/tags/fluid/<name>.json (replace false, entries not required) for both names of every link."""
import json, os, re

SRC = "src/main/java/com/gcroyalatomics/ntmgt/compat/GtFluidLinks.java"
OUT = "src/main/resources/data/c/tags/fluid"
links = re.findall(r'Map\.entry\("([a-z0-9_]+)", "([a-z0-9_]+)"\)', open(SRC).read())
tags = {}
for ntm, gt in links:
    for name in (ntm, gt):
        tags.setdefault(name, set()).add("hbm:" + ntm)
os.makedirs(OUT, exist_ok=True)
for name, ids in sorted(tags.items()):
    with open(f"{OUT}/{name}.json", "w") as f:
        json.dump({"replace": False, "values": [{"id": i, "required": False} for i in sorted(ids)]}, f, indent=2)
        f.write("\n")
print(len(links), "links,", len(tags), "tags")

# GT's bedrock fluid deposits that name a linked GT fluid: same deposit, NTM's fluid (GT's still exists, but its
# recipes now use NTM's, so a rig must pump NTM's).
import glob, zipfile
jar = glob.glob("libs/gtceu-*.jar")[0]
gt_to_ntm = {gt: ntm for ntm, gt in links}
n = 0
with zipfile.ZipFile(jar) as z:
    for name in z.namelist():
        if not name.startswith("data/gtceu/gtceu/bedrock_fluid/") or not name.endswith(".json"):
            continue
        data = json.loads(z.read(name))
        fluid = data.get("fluid", "")
        if fluid.startswith("gtceu:") and fluid[6:] in gt_to_ntm:
            data["fluid"] = "hbm:" + gt_to_ntm[fluid[6:]]
            out = "src/main/resources/" + name
            os.makedirs(os.path.dirname(out), exist_ok=True)
            with open(out, "w") as f:
                json.dump(data, f, indent=2)
                f.write("\n")
            n += 1
print(n, "bedrock fluid deposits overridden")
