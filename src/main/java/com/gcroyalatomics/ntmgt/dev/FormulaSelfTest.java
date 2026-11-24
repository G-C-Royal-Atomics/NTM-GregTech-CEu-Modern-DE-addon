package com.gcroyalatomics.ntmgt.dev;

import com.gcroyalatomics.ntmgt.compat.NtmFormulas;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

/** Dev-only (selftest): formula lines NtmFormulas gives NTM items GT shows none for. */
final class FormulaSelfTest {
    private FormulaSelfTest() {}

    private static final Map<String, String> EXPECT = Map.of(
            "hbm:ingot_pc", "(C₁₆H₁₄O₃)ₙ",
            "hbm:ingot_pu_mix", "(Pu²³⁹)₂Pu²⁴⁰",
            "hbm:ingot_euphemium", "Ep",
            "hbm:chunk_ore_malachite", "*",
            "hbm:powder_limestone", "*",
            "hbm:ingot_steel", "");

    static void run(Writer w) throws IOException {
        int n = 0;
        for (var item : BuiltInRegistries.ITEM) {
            var f = NtmFormulas.find(item);
            if (f.isPresent()) {
                w.write("formula\t" + BuiltInRegistries.ITEM.getKey(item) + "\t" + f.get() + "\n");
                n++;
            }
        }
        w.write("formula count\t" + n + "\n");
        int none = 0;
        for (var item : BuiltInRegistries.ITEM) {
            var id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("hbm")) continue;
            boolean gt = com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper.getMaterialEntry(item) != null;
            if (!gt && NtmFormulas.find(item).isEmpty()) {
                w.write("noformula\t" + id + "\n");
                none++;
            }
        }
        w.write("noformula count\t" + none + "\n");
        for (var e : EXPECT.entrySet()) {
            String got = NtmFormulas.find(BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.getKey()))).orElse("");
            boolean ok = e.getValue().equals("*") ? !got.isEmpty() : got.equals(e.getValue());
            w.write((ok ? "PASS" : "FAIL") + " formula " + e.getKey() + " = '" + got + "'\n");
        }
    }
}
