"""Copies of GT data files that name GT items the addon replaced with NTM items (material_links.tsv).

GT's chest loot tables list e.g. gtceu:steel_ingot; that item no longer exists (hbm:ingot_steel is GT's steel ingot
now), so the whole table would fail to load. The copies (same path, the addon's data pack loads after GT's) name the
NTM items instead.  Usage: tools/gen_gt_overrides.py <gtceu jar>
"""
import os, re, sys, zipfile

GT_ITEM = {  # GT prefix id -> GT item id pattern
    "ingot": "{m}_ingot", "dust": "{m}_dust", "tiny_dust": "tiny_{m}_dust", "nugget": "{m}_nugget",
    "plate": "{m}_plate", "block": "{m}_block", "gem": "{m}_gem", "fine_wire": "fine_{m}_wire",
    "bolt": "{m}_bolt", "raw": "raw_{m}",
}
replace = {}
for line in open("src/main/resources/ntmgt/material_links.tsv"):
    if line.startswith("#") or not line.strip():
        continue
    prefix, mat, item = line.rstrip("\n").split("\t")
    replace["gtceu:" + GT_ITEM[prefix].format(m=mat)] = item

jar = zipfile.ZipFile(sys.argv[1])
for name in jar.namelist():
    if not (name.startswith("data/gtceu/loot_table/") and name.endswith(".json")):
        continue
    text = jar.read(name).decode()
    new = re.sub(r'"(gtceu:[a-z0-9_]+)"', lambda m: '"' + replace.get(m.group(1), m.group(1)) + '"', text)
    if new != text:
        out = os.path.join("src/main/resources", name)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        open(out, "w").write(new)
        print(name)
