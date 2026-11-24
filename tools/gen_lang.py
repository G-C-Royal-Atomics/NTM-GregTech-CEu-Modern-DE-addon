"""Material names for the addon's GT materials (material.ntmgt.<name>), taken from NTM's own lang (hbmmat.<name>).

Usage: tools/gen_lang.py <ntm jar>   (writes src/main/resources/assets/ntmgt/lang/{en_us,ru_ru}.json)
"""
import json, re, sys, zipfile

names = re.findall(r'new Metal\("([a-z0-9_]+)"', open("src/main/java/com/gcroyalatomics/ntmgt/NtmGtNewMaterials.java").read())
jar = zipfile.ZipFile(sys.argv[1])
for lang in ("en_us", "ru_ru"):
    ntm = json.loads(jar.read(f"assets/hbm/lang/{lang}.json"))
    path = f"src/main/resources/assets/ntmgt/lang/{lang}.json"
    try:
        out = json.load(open(path))
    except FileNotFoundError:
        out = {}
    for n in names:
        if f"hbmmat.{n}" in ntm:
            out[f"material.ntmgt.{n}"] = ntm[f"hbmmat.{n}"]
    with open(path, "w") as f:
        json.dump(dict(sorted(out.items())), f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(lang, len(out))
