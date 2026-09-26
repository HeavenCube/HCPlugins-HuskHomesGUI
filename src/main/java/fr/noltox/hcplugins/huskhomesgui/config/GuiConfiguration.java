package fr.noltox.hcplugins.huskhomesgui.config;

import de.exlll.configlib.Configuration;
import de.exlll.configlib.SerializeWith;
import de.exlll.configlib.Serializer;
import fr.noltox.hcconfig.HCConfigurations;
import fr.noltox.hcplugins.huskhomesgui.support.MiniMessages;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static fr.noltox.hcplugins.huskhomesgui.config.MenuActions.*;

/**
 * Immutable representation of the administrator-configurable GUI files backed by ConfigLib.
 */
public final class GuiConfiguration {

    private static final String ITEMS_FILE = "items.yml";
    private static final String LOCALE_FILE = "locale.yml";
    private static final String MENUS_FILE = "menus.yml";
    private static final List<String> CONFIGURATION_FILES = List.of(ITEMS_FILE, LOCALE_FILE, MENUS_FILE);
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(
            "arrange_homes",
            "change_description",
            "change_icon",
            "change_location",
            "change_name",
            "clear_search",
            CLOSE_INVENTORY,
            "edit_home",
            "move_home_down",
            "move_home_up",
            NEXT_PAGE,
            OPEN_LAST_MENU,
            PREVIOUS_PAGE,
            "reset_icon",
            "search_icon",
            "select_icon",
            "select_order_home",
            "set_favorite",
            "teleport_home"
    );

    private static final Set<String> DYNAMIC_MATERIALS = Set.of("%home_icon%", "%icon_item%");
    private static final int MENU_COLUMNS = 9;
    private static final int MAX_MENU_ROWS = 6;
    private final MenuDefinition homesMenu;
    private final MenuDefinition editHomeMenu;
    private final MenuDefinition selectIconMenu;
    private final MenuDefinition arrangeHomesMenu;
    private final Messages messages;

    private GuiConfiguration(
            MenuDefinition homesMenu,
            MenuDefinition editHomeMenu,
            MenuDefinition selectIconMenu,
            MenuDefinition arrangeHomesMenu,
            Messages messages
    ) {
        this.homesMenu = homesMenu;
        this.editHomeMenu = editHomeMenu;
        this.selectIconMenu = selectIconMenu;
        this.arrangeHomesMenu = arrangeHomesMenu;
        this.messages = messages;
    }

    public static GuiConfiguration load(JavaPlugin plugin) {
        saveDefaultFiles(plugin);

        Path dataFolder = plugin.getDataFolder().toPath();
        SharedItemsConfig sharedConfig = HCConfigurations.load(
                dataFolder.resolve(ITEMS_FILE),
                SharedItemsConfig.class
        );
        MenusConfigFile menusConfig = HCConfigurations.load(
                dataFolder.resolve(MENUS_FILE),
                MenusConfigFile.class
        );
        LocaleConfig localeConfig = HCConfigurations.load(
                dataFolder.resolve(LOCALE_FILE),
                LocaleConfig.class
        );

        Map<String, MenuItemDefinition> defaultItems = readItems(sharedConfig.toMap(), ITEMS_FILE);
        MenuDefinition homes = readMenu("homes", menusConfig.homes(), defaultItems, true);
        MenuDefinition editHome = readMenu("edit-home", menusConfig.editHome(), defaultItems, false);
        MenuDefinition selectIcon = readMenu("select-icon", menusConfig.selectIcon(), defaultItems, true);
        MenuDefinition arrangeHomes = readMenu("arrange-homes", menusConfig.arrangeHomes(), defaultItems, true);

        return new GuiConfiguration(homes, editHome, selectIcon, arrangeHomes, localeConfig.toMessages());
    }

    private static void saveDefaultFiles(JavaPlugin plugin) {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create the plugin data directory", exception);
        }

        for (String fileName : CONFIGURATION_FILES) {
            Path path = plugin.getDataFolder().toPath().resolve(fileName);
            if (!Files.exists(path)) {
                plugin.saveResource(fileName, false);
            }
        }
    }

    private static MenuDefinition readMenu(
            String id,
            MenuConfig config,
            Map<String, MenuItemDefinition> defaultItems,
            boolean paged
    ) {
        String source = MENUS_FILE + " " + id;
        if (config == null) {
            throw new IllegalStateException(MENUS_FILE + " must contain a '" + id + "' menu");
        }

        List<String> shape = config.shape();
        validateMenuShape(source, shape);

        Map<String, MenuItemDefinition> items = new LinkedHashMap<>(
                readItems(config.items(), source + ".items")
        );
        addDefaultItems(shape, items, defaultItems);
        addPageStateItems(items, defaultItems);

        String pagedItemId = config.pagedItem() == null ? "" : config.pagedItem();
        MenuItemDefinition pagedItem = paged ? items.get(pagedItemId) : null;
        if (paged && pagedItem == null) {
            throw new IllegalStateException(source + ".paged-item must reference a configured item");
        }
        if (paged && pagedItem.shapeCharacter() == null) {
            throw new IllegalStateException("The paged item '" + pagedItemId + "' needs a shape-character");
        }

        Map<Character, MenuItemDefinition> staticItems = new LinkedHashMap<>();
        for (MenuItemDefinition item : items.values()) {
            Character shapeCharacter = item.shapeCharacter();
            if (shapeCharacter == null || item == pagedItem || !contains(shape, shapeCharacter)) {
                continue;
            }
            MenuItemDefinition previous = staticItems.put(shapeCharacter, item);
            if (previous != null) {
                throw new IllegalStateException(
                        "Multiple menu items use the shape character '" + shapeCharacter + "' in '" + id + "'"
                );
            }
        }

        validateActions(items.values());
        validatePageStateItems(id, items);
        int clickRateLimit = config.clickRateLimit() == null ? 4 : config.clickRateLimit();
        String title = config.title() == null || config.title().isBlank() ? "<gold>Mes homes" : config.title();
        return new MenuDefinition(
                id,
                title,
                List.copyOf(shape),
                Math.max(0, clickRateLimit),
                pagedItem,
                Map.copyOf(items),
                Map.copyOf(staticItems),
                readFilters(config.filters())
        );
    }

    private static void validateMenuShape(String source, List<String> shape) {
        if (shape.isEmpty()) {
            throw new IllegalStateException(source + ".shape must contain at least one row");
        }
        if (shape.size() > MAX_MENU_ROWS) {
            throw new IllegalStateException(source + ".shape cannot contain more than six rows");
        }
        for (int rowIndex = 0; rowIndex < shape.size(); rowIndex++) {
            int columns = shape.get(rowIndex).replace(" ", "").length();
            if (columns != MENU_COLUMNS) {
                throw new IllegalStateException(
                        source + ".shape row " + (rowIndex + 1) + " must contain exactly nine slot characters"
                );
            }
        }
    }

    private static Map<String, MenuItemDefinition> readItems(Map<String, MenuItemConfig> configs, String source) {
        if (configs == null || configs.isEmpty()) {
            return Map.of();
        }

        Map<String, MenuItemDefinition> items = new LinkedHashMap<>();
        for (Map.Entry<String, MenuItemConfig> entry : configs.entrySet()) {
            String id = entry.getKey();
            MenuItemConfig config = entry.getValue();
            if (config != null) {
                items.put(id, readItem(id, config, source));
            }
        }
        return items;
    }

    private static MenuItemDefinition readItem(String id, MenuItemConfig config, String source) {
        String itemSource = source + " item '" + id + "'";
        String material = config.item() == null || config.item().isBlank() ? "PAPER" : config.item();
        if (!DYNAMIC_MATERIALS.contains(material.toLowerCase(Locale.ROOT))
                && Material.getMaterial(material.toUpperCase(Locale.ROOT)) == null) {
            throw new IllegalStateException(
                    itemSource + " references unknown material '" + material + "'"
            );
        }

        String shape = config.shapeCharacter();
        Character shapeCharacter = (shape == null || shape.isEmpty()) ? null : shape.charAt(0);
        ModelComponents modelComponents = readModelComponents(
                config.properties() != null ? config.properties().customModelData() : null
        );
        return new MenuItemDefinition(
                id,
                shapeCharacter,
                material,
                config.amount() == null ? 1 : Math.max(1, config.amount()),
                config.displayName() == null ? "" : config.displayName(),
                config.lore(),
                readProperties(config.properties(), modelComponents, itemSource),
                config.pageUnavailableItem() == null ? "" : config.pageUnavailableItem(),
                readActions(config.actions())
        );
    }

    private static ModelComponents readModelComponents(ModelComponentsConfig customModelData) {
        if (customModelData == null) {
            return new ModelComponents(List.of(), List.of(), List.of());
        }
        return new ModelComponents(
                customModelData.floats(),
                customModelData.strings(),
                customModelData.flags()
        );
    }

    private static ItemProperties readProperties(
            ItemPropertiesConfig properties,
            ModelComponents modelComponents,
            String source
    ) {
        if (properties == null) {
            return new ItemProperties(
                    false, null, false, "", "", false, null, "", null,
                    List.of(), Map.of(), List.of(), List.of(), modelComponents
            );
        }

        Boolean glint = properties.enchantmentGlintOverride();
        ItemProperties result = new ItemProperties(
                Boolean.TRUE.equals(properties.unbreakable()),
                glint,
                Boolean.TRUE.equals(properties.hideTooltip()),
                properties.itemModel() == null ? "" : properties.itemModel(),
                properties.tooltipStyle() == null ? "" : properties.tooltipStyle(),
                Boolean.TRUE.equals(properties.fireResistant()),
                properties.maxStackSize(),
                properties.rarity() == null ? "" : properties.rarity(),
                properties.enchantable(),
                properties.itemFlags(),
                validateEnchantments(properties.enchantments(), source + ".properties.enchantments"),
                materialList(properties.canBreak(), source + ".properties.can-break"),
                materialList(properties.canPlaceOn(), source + ".properties.can-place-on"),
                modelComponents
        );
        validateProperties(result, source);
        return result;
    }

    private static void validatePageStateItems(String menuId, Map<String, MenuItemDefinition> items) {
        for (MenuItemDefinition item : items.values()) {
            String unavailable = item.pageUnavailableItemId();
            if (!unavailable.isBlank() && !items.containsKey(unavailable)) {
                throw new IllegalStateException(
                        "Menu '" + menuId + "' item '" + item.id()
                                + "' references unknown page-unavailable-item '" + unavailable + "'"
                );
            }
        }
    }

    private static void validateProperties(ItemProperties properties, String source) {
        validateKey(properties.itemModel(), source + ".properties.item-model");
        validateKey(properties.tooltipStyle(), source + ".properties.tooltip-style");
        if (!properties.rarity().isBlank()) {
            try {
                ItemRarity.valueOf(properties.rarity().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(source + " has unknown rarity '" + properties.rarity() + "'", exception);
            }
        }
        for (String flag : properties.itemFlags()) {
            try {
                ItemFlag.valueOf(flag.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(source + " has unknown item flag '" + flag + "'", exception);
            }
        }
        for (String enchantment : properties.enchantments().keySet()) {
            NamespacedKey key = NamespacedKey.fromString(enchantment);
            if (key == null || RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key) == null) {
                throw new IllegalStateException(source + " has unknown enchantment '" + enchantment + "'");
            }
        }
    }

    private static void validateKey(String value, String source) {
        if (!value.isBlank() && NamespacedKey.fromString(value) == null) {
            throw new IllegalStateException(source + " must be a valid namespaced key, got '" + value + "'");
        }
    }

    private static Map<String, Integer> validateEnchantments(Map<String, Integer> raw, String source) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : raw.entrySet()) {
            Integer value = entry.getValue();
            if (value == null || value <= 0) {
                throw new IllegalStateException(source + " entry '" + entry.getKey() + "' must be a positive integer");
            }
            result.put(entry.getKey(), value);
        }
        return Map.copyOf(result);
    }

    private static List<String> materialList(List<String> materials, String source) {
        if (materials == null || materials.isEmpty()) {
            return List.of();
        }
        for (String material : materials) {
            Material resolved = Material.getMaterial(material.toUpperCase(Locale.ROOT));
            if (resolved == null || !resolved.isBlock()) {
                throw new IllegalStateException(source + " references unknown material '" + material + "'");
            }
        }
        return List.copyOf(materials);
    }

    private static Map<String, FilterDefinition> readFilters(Map<String, FilterConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return Map.of();
        }

        Map<String, FilterDefinition> filters = new LinkedHashMap<>();
        for (Map.Entry<String, FilterConfig> entry : configs.entrySet()) {
            String id = entry.getKey();
            FilterConfig filter = entry.getValue();
            List<FilterMode> modes = new ArrayList<>();
            if (filter != null) {
                for (FilterModeConfig mode : filter.modes()) {
                    if (mode != null) {
                        modes.add(new FilterMode(
                                mode.text() == null ? "" : mode.text(),
                                mode.streams()
                        ));
                    }
                }
            }
            String selected = (filter == null || filter.selectedEntry() == null) ? "<green>- %mode%" : filter.selectedEntry();
            String unselected = (filter == null || filter.unSelectedEntry() == null) ? "<gray>- %mode%" : filter.unSelectedEntry();
            filters.put(id, new FilterDefinition(
                    id,
                    selected,
                    unselected,
                    List.copyOf(modes)
            ));
        }
        return Map.copyOf(filters);
    }

    private static void addDefaultItems(
            List<String> shape,
            Map<String, MenuItemDefinition> items,
            Map<String, MenuItemDefinition> defaultItems
    ) {
        Map<Character, MenuItemDefinition> byCharacter = new LinkedHashMap<>();
        for (MenuItemDefinition item : items.values()) {
            if (item.shapeCharacter() != null) {
                byCharacter.put(item.shapeCharacter(), item);
            }
        }

        for (String row : shape) {
            for (char character : row.toCharArray()) {
                if (character == ' ' || byCharacter.containsKey(character)) {
                    continue;
                }
                MenuItemDefinition defaultItem = defaultItems.values().stream()
                        .filter(item -> Objects.equals(item.shapeCharacter(), character))
                        .findFirst()
                        .orElse(null);
                if (defaultItem != null) {
                    items.putIfAbsent(defaultItem.id(), defaultItem);
                    byCharacter.put(character, defaultItem);
                }
            }
        }
    }

    private static void addPageStateItems(
            Map<String, MenuItemDefinition> items,
            Map<String, MenuItemDefinition> defaultItems
    ) {
        for (MenuItemDefinition item : List.copyOf(items.values())) {
            String unavailableId = item.pageUnavailableItemId();
            if (!unavailableId.isBlank() && !items.containsKey(unavailableId)) {
                MenuItemDefinition unavailable = defaultItems.get(unavailableId);
                if (unavailable != null) {
                    items.put(unavailableId, unavailable);
                }
            }
        }
    }

    private static void validateActions(Collection<MenuItemDefinition> items) {
        for (MenuItemDefinition item : items) {
            for (MenuActionDefinition action : item.actions()) {
                for (String key : action.execute()) {
                    if (!isSupportedAction(key)) {
                        throw new IllegalStateException(
                                "Unsupported action '" + key + "' in item '" + item.id() + "'"
                        );
                    }
                }
            }
        }
    }

    private static boolean isSupportedAction(String action) {
        return SUPPORTED_ACTIONS.contains(action)
                || hasArgument(action, CHANGE_FILTER_MODE_PREFIX)
                || hasArgument(action, SWITCH_ITEM_PREFIX);
    }

    private static boolean hasArgument(String action, String prefix) {
        return action.startsWith(prefix) && action.length() > prefix.length();
    }

    private static List<MenuActionDefinition> readActions(List<ActionConfig> actionConfigs) {
        if (actionConfigs == null || actionConfigs.isEmpty()) {
            return List.of();
        }

        List<MenuActionDefinition> actions = new ArrayList<>();
        for (ActionConfig entry : actionConfigs) {
            if (entry != null) {
                actions.add(new MenuActionDefinition(
                        clickTypes(entry.clickTypes()),
                        entry.execute()
                ));
            }
        }
        return List.copyOf(actions);
    }

    private static Set<ClickType> clickTypes(List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return EnumSet.allOf(ClickType.class);
        }

        EnumSet<ClickType> clickTypes = EnumSet.noneOf(ClickType.class);
        for (String alias : aliases) {
            switch (alias.toUpperCase(Locale.ROOT)) {
                case "ALL" -> clickTypes.addAll(EnumSet.allOf(ClickType.class));
                case "ALL_SHIFT" -> clickTypes.addAll(EnumSet.of(ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT));
                case "ALL_LEFT" -> clickTypes.addAll(EnumSet.of(ClickType.LEFT, ClickType.SHIFT_LEFT));
                case "ALL_RIGHT" -> clickTypes.addAll(EnumSet.of(ClickType.RIGHT, ClickType.SHIFT_RIGHT));
                default -> {
                    try {
                        clickTypes.add(ClickType.valueOf(alias.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException exception) {
                        throw new IllegalStateException("Unknown click type alias '" + alias + "'", exception);
                    }
                }
            }
        }
        return clickTypes;
    }

    private static boolean contains(List<String> shape, char character) {
        return shape.stream().anyMatch(row -> row.indexOf(character) >= 0);
    }

    public MenuDefinition homesMenu() {
        return homesMenu;
    }

    public MenuDefinition editHomeMenu() {
        return editHomeMenu;
    }

    public MenuDefinition selectIconMenu() {
        return selectIconMenu;
    }

    public MenuDefinition arrangeHomesMenu() {
        return arrangeHomesMenu;
    }

    public Messages messages() {
        return messages;
    }

    // --- Domain Types ---

    public record MenuDefinition(
            String id,
            String title,
            List<String> shape,
            int clickRateLimit,
            MenuItemDefinition pagedItem,
            Map<String, MenuItemDefinition> items,
            Map<Character, MenuItemDefinition> staticItems,
            Map<String, FilterDefinition> filters
    ) {
        public MenuDefinition {
            shape = List.copyOf(shape);
            items = Map.copyOf(items);
            staticItems = Map.copyOf(staticItems);
            filters = Map.copyOf(filters);
        }

        @Override
        public List<String> shape() {
            return Collections.unmodifiableList(shape);
        }

        @Override
        public Map<String, MenuItemDefinition> items() {
            return Collections.unmodifiableMap(items);
        }

        @Override
        public Map<Character, MenuItemDefinition> staticItems() {
            return Collections.unmodifiableMap(staticItems);
        }

        @Override
        public Map<String, FilterDefinition> filters() {
            return Collections.unmodifiableMap(filters);
        }
    }

    public record MenuItemDefinition(
            String id,
            Character shapeCharacter,
            String material,
            int amount,
            String displayName,
            List<String> lore,
            ItemProperties properties,
            String pageUnavailableItemId,
            List<MenuActionDefinition> actions
    ) {
        public MenuItemDefinition {
            lore = List.copyOf(lore);
            actions = List.copyOf(actions);
        }

        @Override
        public List<String> lore() {
            return Collections.unmodifiableList(lore);
        }

        @Override
        public List<MenuActionDefinition> actions() {
            return Collections.unmodifiableList(actions);
        }
    }

    /**
     * Public Paper item components that can be safely expressed in YAML.
     */
    public record ItemProperties(
            boolean unbreakable,
            Boolean enchantmentGlintOverride,
            boolean hideTooltip,
            String itemModel,
            String tooltipStyle,
            boolean fireResistant,
            Integer maxStackSize,
            String rarity,
            Integer enchantable,
            List<String> itemFlags,
            Map<String, Integer> enchantments,
            List<String> canBreak,
            List<String> canPlaceOn,
            ModelComponents customModelData
    ) {
        public ItemProperties {
            itemFlags = List.copyOf(itemFlags);
            enchantments = Map.copyOf(enchantments);
            canBreak = List.copyOf(canBreak);
            canPlaceOn = List.copyOf(canPlaceOn);
        }

        @Override
        public List<String> itemFlags() {
            return Collections.unmodifiableList(itemFlags);
        }

        @Override
        public Map<String, Integer> enchantments() {
            return Collections.unmodifiableMap(enchantments);
        }

        @Override
        public List<String> canBreak() {
            return Collections.unmodifiableList(canBreak);
        }

        @Override
        public List<String> canPlaceOn() {
            return Collections.unmodifiableList(canPlaceOn);
        }
    }

    public record ModelComponents(List<Float> floats, List<String> strings, List<Boolean> flags) {
        public ModelComponents {
            floats = List.copyOf(floats);
            strings = List.copyOf(strings);
            flags = List.copyOf(flags);
        }

        public boolean isEmpty() {
            return floats.isEmpty() && strings.isEmpty() && flags.isEmpty();
        }

        @Override
        public List<Float> floats() {
            return Collections.unmodifiableList(floats);
        }

        @Override
        public List<String> strings() {
            return Collections.unmodifiableList(strings);
        }

        @Override
        public List<Boolean> flags() {
            return Collections.unmodifiableList(flags);
        }
    }

    public record MenuActionDefinition(Set<ClickType> clickTypes, List<String> execute) {
        public MenuActionDefinition {
            clickTypes = Set.copyOf(clickTypes);
            execute = List.copyOf(execute);
        }

        public boolean accepts(ClickType clickType) {
            return clickTypes.contains(clickType);
        }

        @Override
        public Set<ClickType> clickTypes() {
            return Collections.unmodifiableSet(clickTypes);
        }

        @Override
        public List<String> execute() {
            return Collections.unmodifiableList(execute);
        }
    }

    public record FilterDefinition(
            String id,
            String selectedEntry,
            String unselectedEntry,
            List<FilterMode> modes
    ) {
        public FilterDefinition {
            modes = List.copyOf(modes);
        }

        @Override
        public List<FilterMode> modes() {
            return Collections.unmodifiableList(modes);
        }
    }

    public record FilterMode(String text, List<String> streams) {
        public FilterMode {
            streams = List.copyOf(streams);
        }

        @Override
        public List<String> streams() {
            return Collections.unmodifiableList(streams);
        }
    }

    public record Messages(
            Component noPermission,
            Component reload,
            Component reloadFailed,
            Component noTeleportPermission,
            Component homeEditFailed,
            Component homeEditPermission,
            Map<String, Component> configured
    ) {
        public Messages {
            configured = Map.copyOf(configured);
        }

        public Component message(String key, Component fallback) {
            return configured.getOrDefault(key, fallback);
        }

        @Override
        public Map<String, Component> configured() {
            return Collections.unmodifiableMap(configured);
        }
    }

    // --- ConfigLib Serializable DTO Records ---

    @Configuration
    public record LocaleConfig(
            String noPermission,
            String reload,
            String reloadFailed,
            String noTeleportPermission,
            String homeEditFailed,
            String homeEditPermission,
            String dialogConfirm,
            String dialogCancel,
            String dialogRenameTitle,
            String dialogRenameLabel,
            String dialogDescriptionTitle,
            String dialogDescriptionLabel,
            String dialogRelocateTitle,
            String dialogRelocateMessage,
            String dialogCursorIconTitle,
            String dialogCursorIconMessage,
            String dialogIconTitle,
            String dialogIconMessage,
            String dialogSearchTitle,
            String dialogSearchLabel
    ) {
        public Messages toMessages() {
            Map<String, Component> map = new LinkedHashMap<>();
            put(map, "no-permission", noPermission, "<red>Vous n'avez pas la permission de faire cela.");
            put(map, "reload", reload, "<green>Configuration du GUI rechargée.");
            put(map, "reload-failed", reloadFailed, "<red>Le rechargement de la configuration a échoué. Consultez la console.");
            put(map, "no-teleport-permission", noTeleportPermission, "<red>Vous ne pouvez pas vous téléporter à ce home.");
            put(map, "home-edit-failed", homeEditFailed, "<red>HuskHomes a refusé cette modification.");
            put(map, "home-edit-permission", homeEditPermission, "<red>Vous ne pouvez pas modifier ce home.");
            put(map, "dialog-confirm", dialogConfirm, "<green>Confirmer");
            put(map, "dialog-cancel", dialogCancel, "<red>Annuler");
            put(map, "dialog-rename-title", dialogRenameTitle, "<gold>Renommer le home");
            put(map, "dialog-rename-label", dialogRenameLabel, "<yellow>Nom");
            put(map, "dialog-description-title", dialogDescriptionTitle, "<gold>Modifier la description");
            put(map, "dialog-description-label", dialogDescriptionLabel, "<yellow>Description");
            put(map, "dialog-relocate-title", dialogRelocateTitle, "<gold>Déplacer le home");
            put(map, "dialog-relocate-message", dialogRelocateMessage, "<gray>Utiliser votre position actuelle pour ce home ?");
            put(map, "dialog-cursor-icon-title", dialogCursorIconTitle, "<gold>Utiliser l'objet du curseur");
            put(map, "dialog-cursor-icon-message", dialogCursorIconMessage, "<gray>Conserver cet objet comme icône de <white>%home_name%<gray> ?");
            put(map, "dialog-icon-title", dialogIconTitle, "<gold>Choisir l'icône");
            put(map, "dialog-icon-message", dialogIconMessage, "<gray>Utiliser <white>%icon_name%<gray> comme icône de ce home ?");
            put(map, "dialog-search-title", dialogSearchTitle, "<gold>Rechercher une icône");
            put(map, "dialog-search-label", dialogSearchLabel, "<yellow>Nom du matériau");
            return new Messages(
                    map.get("no-permission"),
                    map.get("reload"),
                    map.get("reload-failed"),
                    map.get("no-teleport-permission"),
                    map.get("home-edit-failed"),
                    map.get("home-edit-permission"),
                    Map.copyOf(map)
            );
        }

        private static void put(Map<String, Component> map, String key, String value, String fallback) {
            map.put(key, MiniMessages.parse(value != null && !value.isBlank() ? value : fallback));
        }
    }

    @Configuration
    public record SharedItemsConfig(
            MenuItemConfig filler,
            MenuItemConfig previousPage,
            MenuItemConfig previousPageUnavailable,
            MenuItemConfig nextPage,
            MenuItemConfig nextPageUnavailable,
            MenuItemConfig close,
            MenuItemConfig back
    ) {
        public Map<String, MenuItemConfig> toMap() {
            Map<String, MenuItemConfig> map = new LinkedHashMap<>();
            if (filler != null) map.put("filler", filler);
            if (previousPage != null) map.put("previous-page", previousPage);
            if (previousPageUnavailable != null) map.put("previous-page-unavailable", previousPageUnavailable);
            if (nextPage != null) map.put("next-page", nextPage);
            if (nextPageUnavailable != null) map.put("next-page-unavailable", nextPageUnavailable);
            if (close != null) map.put("close", close);
            if (back != null) map.put("back", back);
            return Collections.unmodifiableMap(map);
        }
    }

    @Configuration
    public record MenusConfigFile(
            MenuConfig homes,
            MenuConfig editHome,
            MenuConfig selectIcon,
            MenuConfig arrangeHomes
    ) {
    }

    @Configuration
    public record MenuConfig(
            String title,
            Integer clickRateLimit,
            List<String> shape,
            String pagedItem,
            Map<String, FilterConfig> filters,
            Map<String, MenuItemConfig> items
    ) {
        public MenuConfig {
            shape = shape == null ? List.of() : List.copyOf(shape);
            filters = filters == null ? Map.of() : Map.copyOf(filters);
            items = items == null ? Map.of() : Map.copyOf(items);
        }

        @Override
        public List<String> shape() {
            return Collections.unmodifiableList(shape);
        }

        @Override
        public Map<String, FilterConfig> filters() {
            return Collections.unmodifiableMap(filters);
        }

        @Override
        public Map<String, MenuItemConfig> items() {
            return Collections.unmodifiableMap(items);
        }
    }

    @Configuration
    public record FilterConfig(
            String selectedEntry,
            String unSelectedEntry,
            List<FilterModeConfig> modes
    ) {
        public FilterConfig {
            modes = modes == null ? List.of() : List.copyOf(modes);
        }

        @Override
        public List<FilterModeConfig> modes() {
            return Collections.unmodifiableList(modes);
        }
    }

    @Configuration
    public record FilterModeConfig(
            String text,
            List<String> streams
    ) {
        public FilterModeConfig {
            streams = streams == null ? List.of() : List.copyOf(streams);
        }

        @Override
        public List<String> streams() {
            return Collections.unmodifiableList(streams);
        }
    }

    @Configuration
    public record MenuItemConfig(
            String shapeCharacter,
            String item,
            Integer amount,
            String displayName,
            List<String> lore,
            ItemPropertiesConfig properties,
            String pageUnavailableItem,
            List<ActionConfig> actions
    ) {
        public MenuItemConfig {
            lore = lore == null ? List.of() : List.copyOf(lore);
            actions = actions == null ? List.of() : List.copyOf(actions);
        }

        @Override
        public List<String> lore() {
            return Collections.unmodifiableList(lore);
        }

        @Override
        public List<ActionConfig> actions() {
            return Collections.unmodifiableList(actions);
        }
    }

    @Configuration
    public record ItemPropertiesConfig(
            Boolean unbreakable,
            Boolean enchantmentGlintOverride,
            Boolean hideTooltip,
            String itemModel,
            String tooltipStyle,
            Boolean fireResistant,
            Integer maxStackSize,
            String rarity,
            Integer enchantable,
            List<String> itemFlags,
            Map<String, Integer> enchantments,
            List<String> canBreak,
            List<String> canPlaceOn,
            ModelComponentsConfig customModelData
    ) {
        public ItemPropertiesConfig {
            itemFlags = itemFlags == null ? List.of() : List.copyOf(itemFlags);
            enchantments = enchantments == null ? Map.of() : Map.copyOf(enchantments);
            canBreak = canBreak == null ? List.of() : List.copyOf(canBreak);
            canPlaceOn = canPlaceOn == null ? List.of() : List.copyOf(canPlaceOn);
        }

        @Override
        public List<String> itemFlags() {
            return Collections.unmodifiableList(itemFlags);
        }

        @Override
        public Map<String, Integer> enchantments() {
            return Collections.unmodifiableMap(enchantments);
        }

        @Override
        public List<String> canBreak() {
            return Collections.unmodifiableList(canBreak);
        }

        @Override
        public List<String> canPlaceOn() {
            return Collections.unmodifiableList(canPlaceOn);
        }
    }

    @Configuration
    public record ModelComponentsConfig(
            List<Float> floats,
            List<String> strings,
            List<Boolean> flags
    ) {
        public ModelComponentsConfig {
            floats = floats == null ? List.of() : List.copyOf(floats);
            strings = strings == null ? List.of() : List.copyOf(strings);
            flags = flags == null ? List.of() : List.copyOf(flags);
        }

        @Override
        public List<Float> floats() {
            return Collections.unmodifiableList(floats);
        }

        @Override
        public List<String> strings() {
            return Collections.unmodifiableList(strings);
        }

        @Override
        public List<Boolean> flags() {
            return Collections.unmodifiableList(flags);
        }
    }

    public static final class ActionConfigSerializer implements Serializer<ActionConfig, Object> {
        @Override
        public Object serialize(ActionConfig action) {
            if (action == null) {
                return "";
            }
            if (action.clickTypes().isEmpty() && action.execute().size() == 1) {
                return action.execute().getFirst();
            }
            Map<String, Object> map = new LinkedHashMap<>();
            if (!action.clickTypes().isEmpty()) {
                map.put("click-types", action.clickTypes());
            }
            map.put("execute", action.execute());
            return map;
        }

        @Override
        public ActionConfig deserialize(Object element) {
            if (element == null) {
                return new ActionConfig(List.of(), List.of());
            }
            if (element instanceof String str) {
                return new ActionConfig(List.of(), List.of(str));
            }
            if (element instanceof Map<?, ?> map) {
                List<String> clickTypes = new ArrayList<>();
                Object ct = map.get("click-types");
                if (ct instanceof List<?> list) {
                    for (Object item : list) {
                        if (item != null) {
                            clickTypes.add(String.valueOf(item));
                        }
                    }
                } else if (ct != null) {
                    clickTypes.add(String.valueOf(ct));
                }

                List<String> execute = new ArrayList<>();
                Object ex = map.get("execute");
                if (ex instanceof List<?> list) {
                    for (Object item : list) {
                        if (item != null) {
                            execute.add(String.valueOf(item));
                        }
                    }
                } else if (ex != null) {
                    execute.add(String.valueOf(ex));
                }
                return new ActionConfig(clickTypes, execute);
            }
            return new ActionConfig(List.of(), List.of(String.valueOf(element)));
        }
    }

    @Configuration
    @SerializeWith(serializer = ActionConfigSerializer.class)
    public record ActionConfig(List<String> clickTypes, List<String> execute) {
        public ActionConfig {
            clickTypes = clickTypes == null ? List.of() : List.copyOf(clickTypes);
            execute = execute == null ? List.of() : List.copyOf(execute);
        }

        @Override
        public List<String> clickTypes() {
            return Collections.unmodifiableList(clickTypes);
        }

        @Override
        public List<String> execute() {
            return Collections.unmodifiableList(execute);
        }
    }
}
