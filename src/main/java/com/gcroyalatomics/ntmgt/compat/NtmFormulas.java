package com.gcroyalatomics.ntmgt.compat;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import com.hbm.inventory.material.Mats;
import com.hbm.inventory.material.NTMMaterial;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Chemical formula line (GT's yellow line under the name) for NTM items whose material GT does not have, so GT
 * shows nothing: plastics, minerals, isotopes, NTM's own elements. The material comes from the item's c: tag
 * (c:ingots/polycarbonate, c:dusts/limestone, ...); items without one (gun parts, cast plates, pipes, crystals, ...)
 * use what NTM's crucible melts them into (Mats.getMaterialsFromItem), several materials written in amount order
 * with GT's group notation, e.g. Fe₃(C₂F₄)ₙ; bedrock ore by its type's two main metals (+ ? for the byproducts);
 * the rest by name: crystal_iron, plate_desh, plate_fuel_u233, part_lithium, ... → the material after the
 * prefix. Items GT knows (linked NTM items) keep GT's own line.
 * <p>
 * Real substances use their real formulas; NTM's own ones follow NTM's recipes, e.g. CMB steel = magnetized
 * tungsten 2 : Watz mud 1 (crucible), polymer = petroleum gas + coal + fluorite (a fluoropolymer, like PTFE),
 * fiberglass = sand + peroxide (crystallizer), flux = sand + limestone, Watz mud = what NTM's gas centrifuge
 * gets from it (lead, then iron, plus dust and nuclear waste = ?); reactor-grade Pu/Am = NTM's billet ratios.
 * Fictional elements get symbols in the style of GT's naquadah (Nq) and this addon's Sa / Gh: australium Aus
 * (gold-like, from TF2), euphemium Ep.
 */
public final class NtmFormulas {
    private NtmFormulas() {}

    /** NTM material (c: tag path after the form) → formula. */
    private static final Map<String, String> FORMULAS = Map.ofEntries(
            // plastics
            Map.entry("polycarbonate", "(C₁₆H₁₄O₃)ₙ"),
            Map.entry("bakelite", "(C₆H₆O·CH₂O)ₙ"),
            Map.entry("latex", "(C₅H₈)ₙ"),
            Map.entry("polymer", "(C₂F₄)ₙ"),
            Map.entry("fiberglass", "SiO₂"),
            // minerals and other real substances
            Map.entry("white_phosphorus", "P₄"),
            Map.entry("red_phosphorus", "Pₙ"),
            Map.entry("coltan", "(Fe,Mn)(Nb,Ta)₂O₆"),
            Map.entry("fluorite", "CaF₂"),
            Map.entry("molysite", "FeCl₃"),
            Map.entry("chlorocalcite", "KCaCl₃"),
            Map.entry("rare_earth", "(Ce,La,Nd)PO₄"),
            Map.entry("volcanic", "(Mg,Fe)₂SiO₄"),
            Map.entry("lignite", "C(H₂O)?"),
            Map.entry("flux", "(SiO₂)(CaCO₃)"),
            Map.entry("slag", "(CaSiO₃)(FeO)"),
            // NTM's own
            Map.entry("australium", "Aus"),
            Map.entry("euphemium", "Ep"),
            Map.entry("watz_mud", "PbFe?"),
            // isotopes and NTM's element mixes
            Map.entry("uranium", "U"),
            Map.entry("uranium_233", "U²³³"),
            Map.entry("plutonium", "Pu"),
            Map.entry("plutonium_238", "Pu²³⁸"),
            Map.entry("plutonium_240", "Pu²⁴⁰"),
            Map.entry("plutonium_rg", "(Pu²³⁹)₂Pu²⁴⁰"),
            Map.entry("americium_241", "Am²⁴¹"),
            Map.entry("americium_242", "Am²⁴²"),
            Map.entry("americium_rg", "Am²⁴¹(Am²⁴²)₂"),
            Map.entry("neptunium_237", "Np²³⁷"),
            Map.entry("actinium_227", "Ac²²⁷"),
            Map.entry("radium_226", "Ra²²⁶"),
            Map.entry("polonium_210", "Po²¹⁰"),
            Map.entry("technetium_99", "Tc⁹⁹"),
            Map.entry("lead_209", "Pb²⁰⁹"),
            Map.entry("gold_198", "Au¹⁹⁸"),
            Map.entry("cobalt_60", "Co⁶⁰"),
            Map.entry("strontium_90", "Sr⁹⁰"),
            Map.entry("caesium_137", "Cs¹³⁷"),
            Map.entry("iodine_131", "I¹³¹"),
            Map.entry("xenon_135", "Xe¹³⁵"),
            Map.entry("astatine_209", "At²⁰⁹"));

    /** NTM material → GT material of the same substance: show GT's own formula for it. */
    private static final Map<String, String> SAME_AS_GT = Map.of(
            "limestone", "calcite",
            "malachite", "malachite",
            "sodalite", "sodalite",
            "lignite_coke", "coke",
            "pet_coke", "coke");

    /** NTM's short names in item ids (plate_fuel_u233, crystal_rare, plate_desh, ...) → material name. */
    private static final Map<String, String> SHORT = Map.ofEntries(
            Map.entry("u233", "uranium_233"), Map.entry("u235", "uranium_235"), Map.entry("u238", "uranium_238"),
            Map.entry("pu238", "plutonium_238"), Map.entry("pu239", "plutonium_239"),
            Map.entry("pu240", "plutonium_240"), Map.entry("pu241", "plutonium_241"),
            Map.entry("sa326", "schrabidium"), Map.entry("sa327", "solinium"),
            Map.entry("sr90", "strontium_90"), Map.entry("am241", "americium_241"), Map.entry("am242", "americium_242"),
            Map.entry("desh", "workers_alloy"), Map.entry("rare", "rare_earth"), Map.entry("niter", "saltpeter"),
            Map.entry("uranium", "uranium"), Map.entry("aluminium", "aluminum"), Map.entry("pu_mix", "plutonium_rg"),
            Map.entry("am_mix", "americium_rg"), Map.entry("schrabidium", "schrabidium"));

    /** Formulas for NTM item-id names that are no material of their own. */
    private static final Map<String, String> NAMED = Map.ofEntries(
            Map.entry("mox", "(UO₂)₄PuO₂"),
            Map.entry("ra226be", "Ra²²⁶Be"), Map.entry("pu238be", "Pu²³⁸Be"), Map.entry("po210be", "Po²¹⁰Be"),
            Map.entry("kevlar", "(C₁₄H₁₀N₂O₂)ₙ"),
            Map.entry("cinnabar", "HgS"));

    /** Item-id prefixes before a material name. */
    private static final List<String> PREFIXES = List.of("plate_fuel_", "crystal_", "plate_", "part_", "billet_",
            "nugget_", "powder_", "ingot_", "fragment_", "chunk_ore_", "wire_", "bolt_", "pipe_", "pipes_");

    private static final Set<String> FORMS = Set.of("ingots", "dusts", "gems", "nuggets", "storage_blocks",
            "raw_materials", "plates");

    private static final Map<Item, Optional<String>> CACHE = new IdentityHashMap<>();

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !com.gcroyalatomics.ntmgt.NtmGtConfig.on(com.gcroyalatomics.ntmgt.NtmGtConfig.ITEM_FORMULAS)) return;
        String formula = (stack.getComponentsPatch().isEmpty()
                ? CACHE.computeIfAbsent(stack.getItem(), i -> find(stack)) : find(stack)).orElse(null);
        if (formula == null) return;
        var lines = event.getToolTip();
        lines.add(Math.min(1, lines.size()), Component.literal(formula).withStyle(ChatFormatting.YELLOW));
    }

    public static void onTagsUpdated(TagsUpdatedEvent event) {
        CACHE.clear();
    }

    public static Optional<String> find(Item item) {
        return find(new ItemStack(item));
    }

    /** Formula for an NTM item GT shows none for: its c: material tag, else what it melts into. */
    public static Optional<String> find(ItemStack stack) {
        Item item = stack.getItem();
        if (!BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("hbm")) return Optional.empty();
        if (ChemicalHelper.getMaterialEntry(item) != null) return Optional.empty(); // GT shows its own
        var tags = item.builtInRegistryHolder().tags().iterator();
        while (tags.hasNext()) {
            ResourceLocation tag = tags.next().location();
            if (!tag.getNamespace().equals("c")) continue;
            String[] parts = tag.getPath().split("/", 2);
            if (parts.length != 2 || !FORMS.contains(parts[0])) continue;
            String f = FORMULAS.get(parts[1]);
            if (f == null && SAME_AS_GT.containsKey(parts[1])) f = gtFormula(SAME_AS_GT.get(parts[1]));
            if (f != null && !f.isEmpty()) return Optional.of(f);
        }
        Optional<String> f = bedrockOre(stack);
        if (f.isEmpty()) f = fromMelt(stack);
        if (f.isEmpty()) f = byName(BuiltInRegistries.ITEM.getKey(item).getPath());
        return f;
    }

    /** NTM material or other name → formula, or null. */
    static String resolve(String name) {
        name = SHORT.getOrDefault(name, name);
        if (NAMED.containsKey(name)) return NAMED.get(name);
        String f = FORMULAS.get(name);
        if (f == null && SAME_AS_GT.containsKey(name)) f = gtFormula(SAME_AS_GT.get(name));
        if (f == null) {
            NTMMaterial mat = Mats.matByName.get(name);
            if (mat != null) f = of(mat);
        }
        if (f == null) {
            Material m = MaterialNames.gt(name);
            if (m != null) f = m.getChemicalFormula();
        }
        return f == null || f.isEmpty() ? null : f;
    }

    private static Optional<String> byName(String path) {
        if (path.endsWith("_tiny")) path = path.substring(0, path.length() - 5);
        for (String prefix : PREFIXES) {
            if (!path.startsWith(prefix)) continue;
            String f = resolve(path.substring(prefix.length()));
            if (f != null) return Optional.of(f);
        }
        return Optional.empty();
    }

    /** Bedrock ore (sample or graded ore): its type's two main metals, ? for the byproducts. */
    private static Optional<String> bedrockOre(ItemStack stack) {
        Map<NTMMaterial, Integer> amounts = new LinkedHashMap<>();
        if (stack.getItem() instanceof com.hbm.items.special.ItemBedrockOreNew) {
            if (com.hbm.items.special.ItemBedrockOreNew.ore(stack).type()
                    instanceof com.hbm.items.special.ItemBedrockOreNew.BedrockOreType t) addPrimaries(t, 1, amounts);
        } else if (stack.getItem() instanceof com.hbm.items.special.ItemBedrockOreBase) {
            for (var t : com.hbm.items.special.ItemBedrockOreNew.BedrockOreType.values()) {
                double a = com.hbm.items.special.ItemBedrockOreBase.getOreAmount(stack, t);
                if (a > 0) addPrimaries(t, (int) Math.max(1, Math.round(a * 10)), amounts);
            }
        } else return Optional.empty();
        if (amounts.isEmpty()) return Optional.empty();
        return compose(amounts).map(f -> f.endsWith("?") ? f : f + "?");
    }

    private static void addPrimaries(com.hbm.items.special.ItemBedrockOreNew.BedrockOreType t, int weight,
                                     Map<NTMMaterial, Integer> amounts) {
        for (var o : List.of(t.primary1, t.primary2)) {
            if (o != null && o.mat != null) amounts.merge(o.mat, o.amount * weight, Integer::sum);
        }
    }

    /** NTM material → formula: ours, else GT's material of that name (gtceu:, then the addon's ntmgt:). */
    static String of(NTMMaterial mat) {
        String name = mat.tagPath;
        String f = FORMULAS.get(name);
        if (f == null && SAME_AS_GT.containsKey(name)) f = gtFormula(SAME_AS_GT.get(name));
        if (f == null) {
            Material m = MaterialNames.gt(name);
            if (m != null) f = m.getChemicalFormula();
        }
        return f == null || f.isEmpty() ? null : f;
    }

    private static Optional<String> fromMelt(ItemStack stack) {
        List<Mats.MaterialStack> parts;
        try {
            parts = Mats.getMaterialsFromItem(stack);
        } catch (RuntimeException e) { // recipes not synced yet
            return Optional.empty();
        }
        Map<NTMMaterial, Integer> amounts = new LinkedHashMap<>();
        for (Mats.MaterialStack m : parts) {
            if (m.material != null && m.amount > 0) amounts.merge(m.material, m.amount, Integer::sum);
        }
        return compose(amounts);
    }

    /** Materials with amounts → one formula, largest first, GT's group notation; ? if one has no formula. */
    private static Optional<String> compose(Map<NTMMaterial, Integer> amounts) {
        if (amounts.isEmpty()) return Optional.empty();
        var sorted = new ArrayList<>(amounts.entrySet());
        sorted.sort((a, b) -> b.getValue() - a.getValue());
        int gcd = 0;
        for (var e : sorted) gcd = gcd(gcd, e.getValue());
        StringBuilder out = new StringBuilder();
        boolean unknown = false;
        for (var e : sorted) {
            String f = of(e.getKey());
            if (f == null) {
                unknown = true;
                continue;
            }
            int n = e.getValue() / gcd;
            if (n > 99) n = 0; // ratios too fine to be worth writing: leave the count out
            boolean group = sorted.size() > 1 || n > 1;
            out.append(group && compound(f) ? "(" + f + ")" : f);
            if (n > 1) out.append(subscript(n));
        }
        if (out.isEmpty()) return Optional.empty();
        if (unknown) out.append('?');
        return Optional.of(out.toString());
    }

    /** More than one element symbol (or a group already): needs brackets when counted or joined. */
    private static boolean compound(String f) {
        int caps = 0;
        for (char c : f.toCharArray()) if (Character.isUpperCase(c)) caps++;
        return caps > 1 || f.indexOf('(') >= 0 || f.endsWith("ₙ");
    }

    private static String subscript(int n) {
        StringBuilder b = new StringBuilder();
        for (char c : Integer.toString(n).toCharArray()) b.append((char) ('₀' + (c - '0')));
        return b.toString();
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static String gtFormula(String name) {
        Material m = GTRegistries.MATERIALS.get(ResourceLocation.fromNamespaceAndPath("gtceu", name));
        return m == null ? null : m.getChemicalFormula();
    }
}
