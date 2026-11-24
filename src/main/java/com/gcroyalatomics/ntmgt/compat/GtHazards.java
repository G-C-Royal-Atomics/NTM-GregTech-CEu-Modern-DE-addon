package com.gcroyalatomics.ntmgt.compat;

import com.gcroyalatomics.ntmgt.NtmGt;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.ItemMaterialData;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.ItemMaterialInfo;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialStack;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.hbm.hazard.HazardData;
import com.hbm.hazard.HazardEntry;
import com.hbm.hazard.HazardRegistry;
import com.hbm.hazard.HazardSystem;
import com.hbm.hazard.type.IHazardType;
import com.hbm.inventory.material.NTMMaterial;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NTM hazards (radiation, blinding, digamma, heat, hydroactive, asbestos, ...) for GT items NTM itself has no
 * hazard for (rods, gears, screws, foils, frames, GT dusts of compounds, machine parts, ...).
 * <p>
 * The level is NTM's own: NTM's per-ingot value for the material (its c:ingots/&lt;material&gt; hazard entry, the
 * same data the NTM ingot uses; dust / block entries scaled back to one ingot where NTM has no ingot entry) times
 * how many ingots of it the GT item holds (GT's composition, M = one ingot). Dust forms get NTM's powder factor
 * (×3, as NTM dust vs ingot). Nuclear hazards (radiation, blinding, digamma) also come through GT compounds and
 * alloys by mass share of the element (uraninite dust radiates by its uranium); chemical ones (heat, hydroactive,
 * asbestos, ...) only from the item's own material (table salt is not hydroactive). Ore blocks get nothing, like
 * NTM's own uranium/thorium ores; crushed / purified ore counts as one ingot of its material (NTM raw uranium = one
 * ingot).
 * <p>
 * Runs right after NTM applies its hazard data pack (HazardSystemMixin), so data pack changes to NTM's values carry
 * over. Items NTM already gives a hazard (item or tag) are left as NTM has them.
 */
public final class GtHazards {
    private static final double POWDER = HazardRegistry.mult_powder / HazardRegistry.mult_ingot;
    private static final Set<String> DUST_FORMS = Set.of("dust", "small_dust", "tiny_dust", "impure_dust",
            "pure_dust");
    private static final Set<IHazardType> NUCLEAR = Set.of(HazardRegistry.RADIATION, HazardRegistry.BLINDING,
            HazardRegistry.DIGAMMA);
    private static final double MIN_LEVEL = 1e-6;

    private GtHazards() {}

    /** Called at the end of HazardSystem.applyDataPack. */
    public static void apply() {
        Map<Item, HazardData> items = new HashMap<>(HazardSystem.itemMap);
        Map<String, Map<IHazardType, Double>> perIngot = new HashMap<>();
        int added = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            if (!"gtceu".equals(ns) && !"ntmgt".equals(ns)) continue;
            if (items.containsKey(item) || hasTagHazard(item)) continue;
            Map<IHazardType, Double> levels = levels(item, perIngot);
            if (levels.isEmpty()) continue;
            HazardData data = new HazardData();
            levels.forEach((type, level) -> data.addEntry(new HazardEntry(type, level)));
            items.put(item, data);
            added++;
        }
        HazardSystem.itemMap = items;
        HazardSystem.clearCaches();
        NtmGt.LOGGER.info("[ntmgt] NTM hazards given to {} GT items", added);
    }

    /** NTM hazard levels one GT item gets; empty if none. */
    public static Map<IHazardType, Double> levels(Item item, Map<String, Map<IHazardType, Double>> perIngot) {
        Map<IHazardType, Double> out = new LinkedHashMap<>();
        MaterialEntry entry = ChemicalHelper.getMaterialEntry(item);
        if (entry != null && entry.material() != null) {
            if (TagPrefix.ORES.containsKey(entry.tagPrefix())) return out;
            ResourceLocation prefix = GTRegistries.TAG_PREFIXES.getKey(entry.tagPrefix());
            double factor = prefix != null && DUST_FORMS.contains(prefix.getPath()) ? POWDER : 1D;
            long amount = entry.tagPrefix().getMaterialAmount(entry.material());
            if (amount <= 0) amount = GTValues.M; // ore-processing forms (crushed, purified, ...): one ore's worth
            add(out, entry.material(), amount / (double) GTValues.M * factor, true, 0, perIngot);
        } else {
            ItemMaterialInfo info = ItemMaterialData.getMaterialInfo(item);
            if (info == null) return out;
            for (MaterialStack part : info.getMaterials()) {
                if (part.material() == null) continue;
                add(out, part.material(), part.amount() / (double) GTValues.M, true, 0, perIngot);
            }
        }
        out.values().removeIf(v -> v < MIN_LEVEL);
        return out;
    }

    private static void add(Map<IHazardType, Double> out, Material material, double ingots, boolean direct,
                            int depth, Map<String, Map<IHazardType, Double>> perIngot) {
        if (ingots <= 0) return;
        NTMMaterial ntm = MaterialNames.ntm(material);
        Map<IHazardType, Double> own = ntm == null ? Map.of()
                : perIngot.computeIfAbsent(ntm.tagPath, GtHazards::ntmPerIngot);
        if (!own.isEmpty()) {
            own.forEach((type, level) -> {
                if (direct || NUCLEAR.contains(type)) out.merge(type, level * ingots, Double::sum);
            });
            return;
        }
        List<MaterialStack> components = material.getMaterialComponents();
        if (components.isEmpty() || depth >= 6) return;
        double total = 0;
        for (MaterialStack c : components) total += c.amount() * (double) c.material().getMass();
        if (total <= 0) return;
        for (MaterialStack c : components) {
            double share = c.amount() * (double) c.material().getMass() / total;
            add(out, c.material(), ingots * share, false, depth + 1, perIngot);
        }
    }

    /** NTM's hazard for one ingot of the material, from its tag entries (ingot, else dust / block / nugget). */
    private static Map<IHazardType, Double> ntmPerIngot(String tagPath) {
        Map<IHazardType, Double> m = fromTag("ingots", tagPath, 1D);
        if (m == null) m = fromTag("dusts", tagPath, 1D / POWDER);
        if (m == null) m = fromTag("storage_blocks", tagPath, HazardRegistry.mult_ingot / HazardRegistry.mult_block);
        if (m == null) m = fromTag("nuggets", tagPath, HazardRegistry.mult_ingot / HazardRegistry.mult_nugget);
        return m == null ? Map.of() : m;
    }

    private static @Nullable Map<IHazardType, Double> fromTag(String shape, String tagPath, double scale) {
        HazardData data = HazardSystem.tagMap.get(TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath("c", shape + "/" + tagPath)));
        if (data == null || data.entries.isEmpty()) return null;
        Map<IHazardType, Double> m = new LinkedHashMap<>();
        for (HazardEntry e : data.entries) {
            if (e.getMods().isEmpty()) m.merge(e.type, e.baseLevel * scale, Double::sum);
        }
        return m;
    }

    private static boolean hasTagHazard(Item item) {
        Iterator<TagKey<Item>> it = BuiltInRegistries.ITEM.wrapAsHolder(item).tags().iterator();
        while (it.hasNext()) if (HazardSystem.tagMap.containsKey(it.next())) return true;
        return false;
    }
}
