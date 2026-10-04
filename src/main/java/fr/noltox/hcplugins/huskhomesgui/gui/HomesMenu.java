package fr.noltox.hcplugins.huskhomesgui.gui;

import fr.noltox.hcplugins.core.api.message.MiniMessages;
import fr.noltox.hcplugins.huskhomesgui.config.GuiConfiguration;
import net.kyori.adventure.text.Component;
import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.position.Home;
import net.william278.huskhomes.user.OnlineUser;
import net.william278.huskhomes.util.TransactionResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.xenondevs.invui.Click;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.Markers;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.item.BoundItem;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.window.Window;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static fr.noltox.hcplugins.huskhomesgui.config.MenuActions.*;

/**
 * Coordinates the configurable InvUI menus for the homes supplied by HuskHomes.
 *
 * <p>The initial list always comes from {@link net.william278.huskhomes.event.HomeListEvent}:
 * no homes are queried again and no second cache is maintained.</p>
 */
public final class HomesMenu {

    private static final List<Material> ICON_MATERIALS = Arrays.stream(Material.values())
            .filter(Material::isItem)
            .filter(material -> !material.isAir())
            .filter(material -> !material.isLegacy())
            .sorted(Comparator.comparing(Material::name))
            .toList();
    private static final Set<String> ORE_MATERIALS = Set.of(
            "AMETHYST_SHARD", "COAL", "DIAMOND", "EMERALD", "LAPIS_LAZULI", "QUARTZ", "REDSTONE"
    );

    private final JavaPlugin plugin;
    private final HuskHomesAPI huskHomes;
    private final Map<UUID, Integer> lastClickTicks = new ConcurrentHashMap<>();
    private final Map<UUID, OpenWindow> openWindows = new ConcurrentHashMap<>();
    private volatile GuiConfiguration configuration;
    private volatile boolean active = true;
    private long generation;

    public HomesMenu(JavaPlugin plugin, HuskHomesAPI huskHomes) {
        this.plugin = plugin;
        this.huskHomes = huskHomes;
        this.configuration = GuiConfiguration.load(plugin);
    }

    private static void executeActions(
            GuiConfiguration.MenuItemDefinition item,
            Click click,
            Consumer<String> execute
    ) {
        for (GuiConfiguration.MenuActionDefinition action : item.actions()) {
            if (action.accepts(click.clickType())) {
                action.execute().forEach(execute);
            }
        }
    }

    private static boolean pageAction(String action, PagedGui<?> gui) {
        switch (action) {
            case PREVIOUS_PAGE -> {
                if (gui.getPage() > 0) {
                    gui.setPage(gui.getPage() - 1);
                }
                return true;
            }
            case NEXT_PAGE -> {
                if (gui.getPage() + 1 < gui.getPageCount()) {
                    gui.setPage(gui.getPage() + 1);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private static GuiConfiguration.MenuItemDefinition pageStateItem(
            GuiConfiguration.MenuDefinition menu,
            GuiConfiguration.MenuItemDefinition definition,
            PagedGui<?> gui
    ) {
        String unavailableId = definition.pageUnavailableItemId();
        if (unavailableId.isBlank() || hasAvailablePageAction(definition, gui)) {
            return definition;
        }
        return menu.items().getOrDefault(unavailableId, definition);
    }

    private static boolean hasAvailablePageAction(
            GuiConfiguration.MenuItemDefinition definition,
            PagedGui<?> gui
    ) {
        for (GuiConfiguration.MenuActionDefinition action : definition.actions()) {
            if (action.execute().contains(PREVIOUS_PAGE) && gui.getPage() > 0) {
                return true;
            }
            if (action.execute().contains(NEXT_PAGE) && gui.getPage() + 1 < gui.getPageCount()) {
                return true;
            }
        }
        return false;
    }

    private static Stream<Material> filterMaterials(
            GuiConfiguration.MenuDefinition menu,
            FilterState filters,
            String search
    ) {
        Stream<Material> stream = ICON_MATERIALS.stream();
        Comparator<Material> comparator = Comparator.comparing(Material::name);
        for (GuiConfiguration.FilterDefinition filter : menu.filters().values()) {
            for (String configuredStream : filters.mode(filter).streams()) {
                switch (configuredStream.toLowerCase(Locale.ROOT)) {
                    case "blocks_only" -> stream = stream.filter(Material::isBlock);
                    case "tools_only" -> stream = stream.filter(HomesMenu::isToolOrArmor);
                    case "ores_only" -> stream = stream.filter(HomesMenu::isOre);
                    case "wood_only" -> stream = stream.filter(HomesMenu::isWood);
                    case "alphabetical_desc_sort" -> comparator = Comparator.comparing(Material::name).reversed();
                    case "alphabetical_asc_sort" -> comparator = Comparator.comparing(Material::name);
                    default -> {
                        // A menu may contain filter streams only meaningful for another menu.
                    }
                }
            }
        }
        if (!search.isBlank()) {
            String term = search.toUpperCase(Locale.ROOT);
            stream = stream.filter(material -> material.name().contains(term));
        }
        return stream.sorted(comparator);
    }

    private static boolean isToolOrArmor(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_PICKAXE") || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL") || name.endsWith("_HOE") || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")
                || switch (material) {
                    case ELYTRA, SHIELD, BOW, CROSSBOW, TRIDENT, MACE -> true;
                    default -> false;
                };
    }

    private static boolean isOre(Material material) {
        String name = material.name();
        return name.endsWith("_ORE") || name.startsWith("RAW_") || name.endsWith("_INGOT")
                || name.endsWith("_NUGGET") || ORE_MATERIALS.contains(name);
    }

    private static boolean isWood(Material material) {
        String name = material.name();
        return name.contains("LOG") || name.contains("WOOD") || name.contains("PLANKS")
                || name.contains("STEM") || name.contains("HYPHAE") || name.contains("BAMBOO")
                || name.contains("MANGROVE") || name.contains("CHERRY");
    }

    private static double distanceSquared(Player player, Home home) {
        org.bukkit.World world = Bukkit.getWorld(home.getWorld().getUuid());
        if (world == null || !world.equals(player.getWorld())) {
            return Double.MAX_VALUE;
        }
        var location = player.getLocation();
        double x = location.getX() - home.getX();
        double y = location.getY() - home.getY();
        double z = location.getZ() - home.getZ();
        return x * x + y * y + z * z;
    }

    private static boolean isOwner(Session session, Home home) {
        return home.getOwner().getUuid().equals(session.viewer().getUuid());
    }

    private static String translationTag(ItemStack itemStack) {
        return "<lang:" + itemStack.getType().translationKey() + ">";
    }

    private static Component translatedMaterialName(ItemStack itemStack) {
        return MiniMessages.parse(translationTag(itemStack));
    }

    public GuiConfiguration configuration() {
        return configuration;
    }

    /**
     * Replaces the active configuration only after every file parsed successfully.
     */
    public void reload() {
        if (!active) {
            throw new IllegalStateException("Le gestionnaire de homes est arrêté.");
        }
        GuiConfiguration candidate = GuiConfiguration.load(plugin);
        generation++;
        closeWindows();
        this.configuration = candidate;
    }

    public void open(Player player, OnlineUser viewer, List<Home> homes) {
        if (!active || !player.isOnline()) {
            return;
        }
        openHomes(new Session(player, viewer, List.copyOf(homes)), FilterState.empty());
    }

    /**
     * Closes retained InvUI windows and invalidates their callbacks during plugin shutdown.
     */
    public void shutdown() {
        active = false;
        generation++;
        closeWindows();
    }

    private void closeWindows() {
        List<UUID> viewers = List.copyOf(openWindows.keySet());
        openWindows.clear();
        lastClickTicks.clear();
        viewers.stream()
                .map(Bukkit::getPlayer)
                .filter(java.util.Objects::nonNull)
                .filter(Player::isOnline)
                .forEach(Player::closeInventory);
    }

    private void openHomes(Session session, FilterState filters) {
        GuiConfiguration.MenuDefinition menu = configuration.homesMenu();
        List<Item> content = filterHomes(session.homes(), session.player(), menu, filters)
                .map(home -> homeItem(menu, session.player(), home, action -> {
                    switch (action) {
                        case "teleport_home" -> teleport(session, home);
                        case "edit_home" -> openEdit(session, home);
                        case "set_favorite" -> {
                            HomePreferences.setFavorite(huskHomes, home, !HomePreferences.favorite(home));
                            openHomes(session, filters);
                        }
                        default -> {
                            // Static-only actions do not apply to a home card.
                        }
                    }
                }))
                .toList();

        PagedGui<Item> gui = pagedGui(menu, content, (action, click, pagedGui) -> {
            if (pageAction(action, pagedGui)) {
                return;
            }
            if (CLOSE_INVENTORY.equals(action)) {
                click.player().closeInventory();
                return;
            }
            if (action.startsWith(CHANGE_FILTER_MODE_PREFIX)) {
                String filterId = action.substring(CHANGE_FILTER_MODE_PREFIX.length());
                openHomes(session, filters.next(menu, filterId, !click.clickType().isRightClick()));
            }
        }, filterPlaceholders(menu, filters));
        openWindow(session.player(), menu.title(), null, gui, filterPlaceholders(menu, filters), null);
    }

    private void openEdit(Session session, Home home) {
        if (!isOwner(session, home)) {
            session.player().sendMessage(configuration.messages().homeEditPermission());
            return;
        }

        GuiConfiguration.MenuDefinition menu = configuration.editHomeMenu();
        Map<String, String> placeholders = Map.of(
                "home_icon_name", translationTag(MenuItemRenderer.homeIcon(home)),
                "home_favorite_instruction", HomePreferences.favorite(home)
                        ? "<green>Ce home est en favori"
                        : "<gray>Ce home n'est pas en favori"
        );
        Gui gui = normalGui(
                menu,
                (action, click) -> executeEditAction(action, session, home, click),
                session.player(),
                home,
                placeholders
        );
        openWindow(session.player(), menu.title(), home, gui, placeholders,
                () -> openHomes(session, FilterState.empty()));
    }

    private void executeEditAction(String action, Session session, Home home, Click click) {
        Player player = click.player();
        switch (action) {
            case "change_icon" -> changeIcon(session, home, player);
            case "reset_icon" -> {
                HomePreferences.setIcon(huskHomes, home, null);
                openEdit(session, home);
            }
            case "change_name" -> HomeDialogs.text(
                    player,
                    dialogActive(),
                    message("dialog-rename-title", "Renommer le home"),
                    message("dialog-rename-label", "Nom"),
                    dialogConfirm(),
                    dialogCancel(),
                    home.getName(),
                    16,
                    value -> renameHome(session, home, value)
            );
            case "change_description" -> HomeDialogs.text(
                    player,
                    dialogActive(),
                    message("dialog-description-title", "Modifier la description"),
                    message("dialog-description-label", "Description"),
                    dialogConfirm(),
                    dialogCancel(),
                    home.getMeta().getDescription() == null ? "" : home.getMeta().getDescription(),
                    100,
                    value -> setDescription(session, home, value)
            );
            case "change_location" -> HomeDialogs.confirm(
                    player,
                    dialogActive(),
                    message("dialog-relocate-title", "Déplacer le home"),
                    message("dialog-relocate-message", "Utiliser votre position actuelle pour ce home ?"),
                    new ItemStack(Material.COMPASS),
                    dialogConfirm(),
                    dialogCancel(),
                    () -> relocateHome(session, home)
            );
            case "set_favorite" -> {
                HomePreferences.setFavorite(huskHomes, home, !HomePreferences.favorite(home));
                openEdit(session, home);
            }
            case "move_home_up" -> {
                HomePreferences.moveBy(huskHomes, session.homes(), home, -1);
                openEdit(session, home);
            }
            case "move_home_down" -> {
                HomePreferences.moveBy(huskHomes, session.homes(), home, 1);
                openEdit(session, home);
            }
            case "arrange_homes" -> openArrangeHomes(session, home, null);
            case OPEN_LAST_MENU -> openHomes(session, FilterState.empty());
            case CLOSE_INVENTORY -> player.closeInventory();
            default -> {
                // This menu intentionally has no implicit command or player action support.
            }
        }
    }

    private void changeIcon(Session session, Home home, Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType().isAir()) {
            openIconPicker(session, home, FilterState.empty(), "");
            return;
        }

        ItemStack chosen = cursor.clone();
        chosen.setAmount(1);
        HomeDialogs.confirm(
                player,
                dialogActive(),
                message("dialog-cursor-icon-title", "Utiliser l'objet du curseur"),
                message("dialog-cursor-icon-message", "Conserver cet objet comme icône de %home_name% ?")
                        .replaceText(builder -> builder.matchLiteral("%home_name%").replacement(home.getName())),
                chosen,
                dialogConfirm(),
                dialogCancel(),
                () -> {
                    HomePreferences.setIcon(huskHomes, home, chosen);
                    openEdit(session, home);
                }
        );
    }

    private void renameHome(Session session, Home home, String input) {
        String name = input.trim().replace(' ', '_');
        if (name.isEmpty()) {
            session.player().sendMessage(configuration.messages().homeEditFailed());
            return;
        }
        huskHomes.renameHome(home, name);
        refreshAfterHuskHomesEdit(session, home);
    }

    private void setDescription(Session session, Home home, String description) {
        huskHomes.setHomeDescription(home, description.trim());
        refreshAfterHuskHomesEdit(session, home);
    }

    private void relocateHome(Session session, Home home) {
        huskHomes.relocateHome(home, huskHomes.adaptPosition(session.player().getLocation()));
        refreshAfterHuskHomesEdit(session, home);
    }

    private void openIconPicker(Session session, Home home, FilterState filters, String search) {
        GuiConfiguration.MenuDefinition menu = configuration.selectIconMenu();
        List<Item> content = filterMaterials(menu, filters, search)
                .map(material -> iconItem(menu, session.player(), material, action -> {
                    if (!"select_icon".equals(action)) {
                        return;
                    }
                    ItemStack selected = new ItemStack(material);
                    HomeDialogs.confirm(
                            session.player(),
                            dialogActive(),
                            message("dialog-icon-title", "Choisir l'icône"),
                            message("dialog-icon-message", "Utiliser %icon_name% comme icône de ce home ?")
                                    .replaceText(builder -> builder.matchLiteral("%icon_name%")
                                            .replacement(translatedMaterialName(selected))),
                            selected,
                            dialogConfirm(),
                            dialogCancel(),
                            () -> {
                                HomePreferences.setIcon(huskHomes, home, selected);
                                openEdit(session, home);
                            }
                    );
                }))
                .toList();

        Map<String, String> placeholders = filterPlaceholders(menu, filters);
        placeholders = new HashMap<>(placeholders);
        placeholders.put("icon_search", search.isBlank() ? "Tous les objets" : search);
        Map<String, String> finalPlaceholders = Map.copyOf(placeholders);
        PagedGui<Item> gui = pagedGui(menu, content, (action, click, pagedGui) -> {
            if (pageAction(action, pagedGui)) {
                return;
            }
            switch (action) {
                case OPEN_LAST_MENU -> openEdit(session, home);
                case CLOSE_INVENTORY -> click.player().closeInventory();
                case "clear_search" -> openIconPicker(session, home, filters, "");
                case "search_icon" -> HomeDialogs.text(
                        click.player(),
                        dialogActive(),
                        message("dialog-search-title", "Rechercher une icône"),
                        message("dialog-search-label", "Nom du matériau"),
                        dialogConfirm(),
                        dialogCancel(),
                        search,
                        32,
                        value -> openIconPicker(session, home, filters, value.trim().toUpperCase(Locale.ROOT))
                );
                default -> {
                    if (action.startsWith(CHANGE_FILTER_MODE_PREFIX)) {
                        String filterId = action.substring(CHANGE_FILTER_MODE_PREFIX.length());
                        openIconPicker(session, home, filters.next(menu, filterId, !click.clickType().isRightClick()), search);
                    }
                }
            }
        }, finalPlaceholders);
        openWindow(session.player(), menu.title(), home, gui, finalPlaceholders, () -> openEdit(session, home));
    }

    private void openArrangeHomes(Session session, Home editedHome, Home selected) {
        GuiConfiguration.MenuDefinition menu = configuration.arrangeHomesMenu();
        List<Item> content = session.homes().stream()
                .sorted(HomePreferences.manualOrder())
                .map(home -> homeItem(menu, session.player(), home, action -> {
                    if (!"select_order_home".equals(action)) {
                        return;
                    }
                    if (selected == null || selected.equals(home)) {
                        openArrangeHomes(session, editedHome, home);
                        return;
                    }
                    HomePreferences.swapOrder(huskHomes, session.homes(), selected, home);
                    openArrangeHomes(session, editedHome, null);
                }, home.equals(selected)))
                .toList();

        Map<String, String> placeholders = Map.of(
                "order_selected", selected == null ? "<gray>Sélectionnez un home, puis sa position cible." :
                        "<yellow>Sélectionné : <white>" + selected.getName()
        );
        PagedGui<Item> gui = pagedGui(menu, content, (action, click, pagedGui) -> {
            if (pageAction(action, pagedGui)) {
                return;
            }
            if (OPEN_LAST_MENU.equals(action)) {
                openEdit(session, editedHome);
            } else if (CLOSE_INVENTORY.equals(action)) {
                click.player().closeInventory();
            }
        }, placeholders);
        openWindow(session.player(), menu.title(), null, gui, placeholders, () -> openEdit(session, editedHome));
    }

    private Item homeItem(
            GuiConfiguration.MenuDefinition menu,
            Player player,
            Home home,
            Consumer<String> execute
    ) {
        return homeItem(menu, player, home, execute, false);
    }

    private Item homeItem(
            GuiConfiguration.MenuDefinition menu,
            Player player,
            Home home,
            Consumer<String> execute,
            boolean selected
    ) {
        GuiConfiguration.MenuItemDefinition item = menu.pagedItem();
        Map<String, String> placeholders = Map.of(
                "favorite_instruction", HomePreferences.favorite(home)
                        ? "<yellow>Maj-clic : retirer des favoris"
                        : "<yellow>Maj-clic : ajouter aux favoris",
                "order_selected", selected ? "<green>Sélectionné" : ""
        );
        return Item.builder()
                .setItemProvider(MenuItemRenderer.render(
                        player,
                        item,
                        home,
                        MenuItemRenderer.homeIcon(home),
                        placeholders,
                        selected
                ))
                .addClickHandler((clickedItem, click) -> {
                    if (!allowsClick(click.player(), menu.clickRateLimit())) {
                        return;
                    }
                    executeActions(item, click, execute);
                })
                .build();
    }

    private Item iconItem(
            GuiConfiguration.MenuDefinition menu,
            Player player,
            Material material,
            Consumer<String> execute
    ) {
        GuiConfiguration.MenuItemDefinition item = menu.pagedItem();
        ItemStack icon = new ItemStack(material);
        Map<String, String> placeholders = Map.of(
                "icon_name", translationTag(icon),
                "material_name", translationTag(icon)
        );
        return Item.builder()
                .setItemProvider(MenuItemRenderer.render(player, item, null, icon, placeholders, false))
                .addClickHandler((clickedItem, click) -> {
                    if (!allowsClick(click.player(), menu.clickRateLimit())) {
                        return;
                    }
                    executeActions(item, click, execute);
                })
                .build();
    }

    private PagedGui<Item> pagedGui(
            GuiConfiguration.MenuDefinition menu,
            List<Item> content,
            PagedStaticAction handler,
            Map<String, String> placeholders
    ) {
        PagedGui.Builder<Item> builder = PagedGui.itemsBuilder()
                .setStructure(menu.shape().toArray(String[]::new))
                .addIngredient(menu.pagedItem().shapeCharacter(), Markers.CONTENT_LIST_SLOT_HORIZONTAL);
        for (Map.Entry<Character, GuiConfiguration.MenuItemDefinition> entry : menu.staticItems().entrySet()) {
            builder.addIngredient(entry.getKey(), BoundItem.pagedBuilder()
                    .setItemProvider((player, gui) -> MenuItemRenderer.render(
                            player, pageStateItem(menu, entry.getValue(), gui), null, null, placeholders, false
                    ))
                    .addClickHandler((item, gui, click) -> {
                        if (allowsClick(click.player(), menu.clickRateLimit())) {
                            executeActions(entry.getValue(), click, action -> handler.execute(action, click, gui));
                        }
                    })
                    .build());
        }
        return builder.setContent(content).build();
    }

    private Gui normalGui(
            GuiConfiguration.MenuDefinition menu,
            StaticAction handler,
            Player player,
            Home home,
            Map<String, String> placeholders
    ) {
        Gui.Builder<?, ?> builder = Gui.builder().setStructure(menu.shape().toArray(String[]::new));
        for (Map.Entry<Character, GuiConfiguration.MenuItemDefinition> entry : menu.staticItems().entrySet()) {
            GuiConfiguration.MenuItemDefinition definition = entry.getValue();
            builder.addIngredient(entry.getKey(), Item.builder()
                    .setItemProvider(MenuItemRenderer.render(
                            player,
                            definition,
                            home,
                            MenuItemRenderer.homeIcon(home),
                            placeholders,
                            false
                    ))
                    .addClickHandler((item, click) -> {
                        if (allowsClick(click.player(), menu.clickRateLimit())) {
                            executeActions(definition, click, action -> handler.execute(action, click));
                        }
                    })
                    .build());
        }
        return builder.build();
    }

    private Stream<Home> filterHomes(
            List<Home> homes,
            Player player,
            GuiConfiguration.MenuDefinition menu,
            FilterState filters
    ) {
        Stream<Home> stream = homes.stream();
        Comparator<Home> comparator = HomePreferences.manualOrder();
        for (GuiConfiguration.FilterDefinition filter : menu.filters().values()) {
            for (String configuredStream : filters.mode(filter).streams()) {
                String key = configuredStream.toLowerCase(Locale.ROOT);
                if (key.startsWith("world_filter:")) {
                    String world = configuredStream.substring("world_filter:".length());
                    stream = stream.filter(home -> home.getWorld().getName().equalsIgnoreCase(world));
                    continue;
                }
                switch (key) {
                    case "newest_sort" -> comparator = Comparator.comparing(
                            (Home home) -> home.getMeta().getCreationTime()).reversed();
                    case "oldest_sort" -> comparator = Comparator.comparing(home -> home.getMeta().getCreationTime());
                    case "closest_sort" ->
                            comparator = Comparator.comparingDouble(home -> distanceSquared(player, home));
                    case "favorites_sort" -> comparator = Comparator.comparing(HomePreferences::favorite).reversed()
                            .thenComparing(HomePreferences.manualOrder());
                    case "alphabetical_asc_sort" ->
                            comparator = Comparator.comparing(Home::getName, String.CASE_INSENSITIVE_ORDER);
                    case "alphabetical_desc_sort" ->
                            comparator = Comparator.comparing(Home::getName, String.CASE_INSENSITIVE_ORDER)
                                    .reversed();
                    case "manual_sort" -> comparator = HomePreferences.manualOrder();
                    default -> {
                        // Unknown filter streams are intentionally ignored so configuration remains forward-compatible.
                    }
                }
            }
        }
        return stream.sorted(comparator);
    }

    private Map<String, String> filterPlaceholders(
            GuiConfiguration.MenuDefinition menu,
            FilterState filters
    ) {
        Map<String, String> placeholders = new HashMap<>();
        for (GuiConfiguration.FilterDefinition filter : menu.filters().values()) {
            GuiConfiguration.FilterMode selected = filters.mode(filter);
            String entries = filter.modes().stream()
                    .map(mode -> (mode == selected ? filter.selectedEntry() : filter.unselectedEntry())
                            .replace("%mode%", mode.text()))
                    .reduce((first, second) -> first + "\n" + second)
                    .orElse("<gray>None");
            placeholders.put("filter_lore:" + filter.id(), entries);
        }
        return placeholders;
    }

    private void openWindow(
            Player player,
            String title,
            Home home,
            Gui gui,
            Map<String, String> placeholders,
            Runnable onEscape
    ) {
        long openedGeneration = generation;
        OpenWindow openWindow = new OpenWindow();
        openWindows.put(player.getUniqueId(), openWindow);
        Window.builder()
                .setTitle(MenuItemRenderer.text(player, title, home, placeholders))
                .setUpperGui(gui)
                .addCloseHandler(reason -> {
                    if (!openWindows.remove(player.getUniqueId(), openWindow)) {
                        return;
                    }
                    lastClickTicks.remove(player.getUniqueId());
                    if (active && generation == openedGeneration && reason == InventoryCloseEvent.Reason.PLAYER
                            && onEscape != null) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (active && generation == openedGeneration && player.isOnline()) {
                                onEscape.run();
                            }
                        });
                    }
                })
                .open(player);
    }

    private void teleport(Session session, Home home) {
        if (!isOwner(session, home) || !session.player().hasPermission("huskhomes.command.home")) {
            session.player().sendMessage(configuration.messages().noTeleportPermission());
            return;
        }

        session.player().closeInventory();
        huskHomes.teleportBuilder()
                .executor(session.viewer())
                .teleporter(session.viewer())
                .target(home)
                .actions(TransactionResolver.Action.HOME_TELEPORT)
                .buildAndComplete(true, session.viewer().getName());
    }

    private boolean allowsClick(Player player, int minimumTickInterval) {
        if (!active || !player.isOnline()) {
            return false;
        }
        if (minimumTickInterval == 0) {
            return true;
        }
        int currentTick = Bukkit.getCurrentTick();
        Integer previousTick = lastClickTicks.put(player.getUniqueId(), currentTick);
        return previousTick == null || currentTick - previousTick >= minimumTickInterval;
    }

    private Component message(String key, String fallback) {
        return configuration.messages().message(key, Component.text(fallback));
    }

    private Component dialogConfirm() {
        return message("dialog-confirm", "Confirmer");
    }

    private Component dialogCancel() {
        return message("dialog-cancel", "Annuler");
    }

    private void refreshAfterHuskHomesEdit(Session session, Home home) {
        long requestedGeneration = generation;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (active && generation == requestedGeneration && session.player().isOnline()) {
                openEdit(session, home);
            }
        }, 2L);
    }

    private BooleanSupplier dialogActive() {
        long openedGeneration = generation;
        return () -> active && generation == openedGeneration;
    }

    @FunctionalInterface
    private interface StaticAction {
        void execute(String action, Click click);
    }

    @FunctionalInterface
    private interface PagedStaticAction {
        void execute(String action, Click click, PagedGui<?> gui);
    }

    private record Session(Player player, OnlineUser viewer, List<Home> homes) {
    }

    private static final class OpenWindow {
    }

    private record FilterState(Map<String, Integer> selectedIndices) {
        static FilterState empty() {
            return new FilterState(Map.of());
        }

        GuiConfiguration.FilterMode mode(GuiConfiguration.FilterDefinition filter) {
            if (filter.modes().isEmpty()) {
                return new GuiConfiguration.FilterMode("", List.of());
            }
            int index = selectedIndices.getOrDefault(filter.id(), 0);
            return filter.modes().get(Math.floorMod(index, filter.modes().size()));
        }

        FilterState next(GuiConfiguration.MenuDefinition menu, String filterId, boolean forward) {
            GuiConfiguration.FilterDefinition filter = menu.filters().get(filterId);
            if (filter == null || filter.modes().isEmpty()) {
                return this;
            }
            Map<String, Integer> next = new HashMap<>(selectedIndices);
            int current = next.getOrDefault(filterId, 0);
            next.put(filterId, Math.floorMod(current + (forward ? 1 : -1), filter.modes().size()));
            return new FilterState(Map.copyOf(next));
        }
    }
}
