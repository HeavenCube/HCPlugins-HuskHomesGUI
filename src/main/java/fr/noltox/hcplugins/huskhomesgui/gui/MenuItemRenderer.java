package fr.noltox.hcplugins.huskhomesgui.gui;

import fr.noltox.hcplugins.core.api.message.MiniMessages;
import me.clip.placeholderapi.PlaceholderAPI;
import fr.noltox.hcplugins.huskhomesgui.config.GuiConfiguration;
import io.papermc.paper.block.BlockPredicate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemAdventurePredicate;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.william278.huskhomes.position.Home;
import net.william278.huskhomes.position.World;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.tag.DamageTypeTags;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.ItemWrapper;

import java.util.*;

/**
 * Builds an InvUI item from one {@code menus.yml} or {@code items.yml} entry.
 */
final class MenuItemRenderer {

    private MenuItemRenderer() {
    }

    static ItemProvider render(
            Player player,
            GuiConfiguration.MenuItemDefinition definition,
            Home home,
            ItemStack dynamicIcon,
            Map<String, String> configuredPlaceholders,
            boolean forceGlow
    ) {
        ItemStack itemStack = baseItem(definition.material(), home, dynamicIcon);
        itemStack.setAmount(definition.amount());
        ItemMeta meta = itemStack.getItemMeta();
        applyProperties(meta, definition.properties());

        if (!definition.displayName().isBlank()) {
            meta.displayName(text(player, definition.displayName(), home, configuredPlaceholders));
        }
        if (!definition.lore().isEmpty()) {
            meta.lore(lore(player, definition.lore(), home, configuredPlaceholders));
        }
        if (forceGlow) {
            meta.setEnchantmentGlintOverride(true);
        }
        applyItemMeta(itemStack, meta);
        applyAdventurePredicates(itemStack, definition.properties());
        return new ItemWrapper(itemStack);
    }

    static Component text(Player player, String template, Home home, Map<String, String> configuredPlaceholders) {
        String resolved = replaceConfigured(template, configuredPlaceholders);

        Map<String, String> values = homePlaceholders(home);
        TagResolver[] placeholders = new TagResolver[values.size()];
        int index = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            resolved = resolved.replace('%' + entry.getKey() + '%', '<' + entry.getKey() + '>');
            placeholders[index] = Placeholder.unparsed(entry.getKey(), entry.getValue());
            index++;
        }
        if (player != null) {
            if (player.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                resolved = PlaceholderAPI.setPlaceholders(player, resolved);
            }
        }
        return MiniMessages.parse(resolved, TagResolver.resolver(placeholders))
                .decoration(TextDecoration.ITALIC, false);
    }

    static Map<String, String> homePlaceholders(Home home) {
        Map<String, String> values = new HashMap<>();
        if (home == null) {
            return values;
        }

        String description = home.getMeta().getDescription();
        values.put("home_name", home.getName());
        values.put("home_description", description == null ? "" : description);
        values.put("home_world", home.getWorld().getName());
        values.put("home_x", Integer.toString((int) Math.floor(home.getX())));
        values.put("home_y", Integer.toString((int) Math.floor(home.getY())));
        values.put("home_z", Integer.toString((int) Math.floor(home.getZ())));
        values.put("home_favorite", HomePreferences.favorite(home) ? "Oui" : "Non");
        return values;
    }

    static ItemStack homeIcon(Home home) {
        ItemStack icon = HomePreferences.icon(home);
        if (icon == null) {
            icon = new ItemStack(iconFor(home.getWorld()));
        } else {
            icon = icon.clone();
        }
        icon.setAmount(1);
        ItemMeta meta = icon.getItemMeta();
        meta.addItemFlags(ItemFlag.values());
        applyItemMeta(icon, meta);
        return icon;
    }

    private static List<Component> lore(
            Player player,
            List<String> templates,
            Home home,
            Map<String, String> configuredPlaceholders
    ) {
        List<Component> result = new ArrayList<>();
        for (String template : templates) {
            String resolved = replaceConfigured(template, configuredPlaceholders);
            for (String line : resolved.split("\\n", -1)) {
                result.add(text(player, line, home, Map.of()));
            }
        }
        return result;
    }

    private static String replaceConfigured(String template, Map<String, String> configuredPlaceholders) {
        String resolved = template;
        for (Map.Entry<String, String> entry : configuredPlaceholders.entrySet()) {
            resolved = resolved.replace('%' + entry.getKey() + '%', entry.getValue());
        }
        return resolved;
    }

    private static ItemStack baseItem(String configuredMaterial, Home home, ItemStack dynamicIcon) {
        if (("%home_icon%".equalsIgnoreCase(configuredMaterial) || "%icon_item%".equalsIgnoreCase(configuredMaterial))
                && dynamicIcon != null) {
            return dynamicIcon.clone();
        }
        if ("%home_icon%".equalsIgnoreCase(configuredMaterial) && home != null) {
            return homeIcon(home);
        }
        Material material = Material.getMaterial(configuredMaterial.toUpperCase(Locale.ROOT));
        return new ItemStack(material == null ? Material.PAPER : material);
    }

    private static Material iconFor(World world) {
        World.Environment environment = world.getEnvironment();
        if (environment == null) {
            return Material.ENDER_PEARL;
        }
        return switch (environment) {
            case OVERWORLD -> Material.GRASS_BLOCK;
            case NETHER -> Material.RESPAWN_ANCHOR;
            case THE_END -> Material.END_STONE;
            case CUSTOM -> Material.ENDER_PEARL;
        };
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void applyProperties(
            ItemMeta meta,
            GuiConfiguration.ItemProperties properties
    ) {
        meta.setUnbreakable(properties.unbreakable());
        meta.setHideTooltip(properties.hideTooltip());
        meta.setDamageResistantTypes(properties.fireResistant()
                ? RegistrySet.keySetFromValues(RegistryKey.DAMAGE_TYPE, DamageTypeTags.IS_FIRE.getValues())
                : RegistrySet.keySet(RegistryKey.DAMAGE_TYPE));
        if (properties.enchantmentGlintOverride() != null) {
            meta.setEnchantmentGlintOverride(properties.enchantmentGlintOverride());
        }
        if (properties.maxStackSize() != null) {
            meta.setMaxStackSize(properties.maxStackSize());
        }
        if (properties.enchantable() != null) {
            meta.setEnchantable(properties.enchantable());
        }
        if (!properties.itemModel().isBlank()) {
            meta.setItemModel(NamespacedKey.fromString(properties.itemModel()));
        }
        if (!properties.tooltipStyle().isBlank()) {
            meta.setTooltipStyle(NamespacedKey.fromString(properties.tooltipStyle()));
        }
        if (!properties.rarity().isBlank()) {
            meta.setRarity(ItemRarity.valueOf(properties.rarity().toUpperCase(Locale.ROOT)));
        }
        if (!properties.itemFlags().isEmpty()) {
            ItemFlag[] flags = properties.itemFlags().stream()
                    .map(flag -> ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT)))
                    .toArray(ItemFlag[]::new);
            meta.addItemFlags(flags);
        }
        for (Map.Entry<String, Integer> entry : properties.enchantments().entrySet()) {
            Enchantment enchantment = RegistryAccess.registryAccess()
                    .getRegistry(RegistryKey.ENCHANTMENT)
                    .get(Objects.requireNonNull(NamespacedKey.fromString(entry.getKey()), entry.getKey()));
            if (enchantment == null || !meta.addEnchant(enchantment, entry.getValue(), true)) {
                throw new IllegalStateException("Impossible d'appliquer l'enchantement '" + entry.getKey() + "'.");
            }
        }
        applyModelComponents(meta, properties.customModelData());
    }

    private static void applyAdventurePredicates(ItemStack itemStack, GuiConfiguration.ItemProperties properties) {
        if (!properties.canBreak().isEmpty()) {
            itemStack.setData(DataComponentTypes.CAN_BREAK, adventurePredicate(properties.canBreak()));
        }
        if (!properties.canPlaceOn().isEmpty()) {
            itemStack.setData(DataComponentTypes.CAN_PLACE_ON, adventurePredicate(properties.canPlaceOn()));
        }
    }

    private static ItemAdventurePredicate adventurePredicate(List<String> names) {
        List<org.bukkit.block.BlockType> blocks = names.stream()
                .map(name -> Objects.requireNonNull(
                        Material.getMaterial(name.toUpperCase(Locale.ROOT)),
                        name
                ))
                .map(material -> Objects.requireNonNull(material.asBlockType(), material.name()))
                .toList();
        BlockPredicate predicate = BlockPredicate.predicate()
                .blocks(RegistrySet.keySetFromValues(RegistryKey.BLOCK, blocks))
                .build();
        return ItemAdventurePredicate.itemAdventurePredicate(List.of(predicate));
    }

    private static void applyModelComponents(
            ItemMeta meta,
            GuiConfiguration.ModelComponents components
    ) {
        if (components.isEmpty()) {
            return;
        }
        CustomModelDataComponent model = meta.getCustomModelDataComponent();
        model.setFloats(components.floats());
        model.setStrings(components.strings());
        model.setFlags(components.flags());
        meta.setCustomModelDataComponent(model);
    }

    private static void applyItemMeta(ItemStack itemStack, ItemMeta meta) {
        if (!itemStack.setItemMeta(meta)) {
            throw new IllegalStateException("Impossible d'appliquer les métadonnées à l'objet "
                    + itemStack.getType() + '.');
        }
    }
}
