package fr.noltox.hcplugins.huskhomesgui.gui;

import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.position.Home;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Consumer;

/**
 * GUI-only data saved in HuskHomes' own home metadata.
 *
 * <p>Using namespaced tags keeps icon, favorite and manual-order preferences
 * with the home on every HuskHomes storage backend without adding a second
 * database or a cache.</p>
 */
final class HomePreferences {

    private static final String PREFIX = "hchuskhomesgui:";
    private static final String ICON = PREFIX + "icon";
    private static final String FAVORITE = PREFIX + "favorite";
    private static final String ORDER = PREFIX + "order";

    private HomePreferences() {
    }

    static ItemStack icon(Home home) {
        String encoded = home.getMeta().getTags().get(ICON);
        if (encoded == null || encoded.isBlank()) {
            return null;
        }

        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    static boolean favorite(Home home) {
        return Boolean.parseBoolean(home.getMeta().getTags().get(FAVORITE));
    }

    static int order(Home home) {
        String value = home.getMeta().getTags().get(ORDER);
        if (value == null) {
            return Integer.MAX_VALUE;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    static Comparator<Home> manualOrder() {
        return Comparator.comparingInt(HomePreferences::order)
                .thenComparing(home -> home.getMeta().getCreationTime())
                .thenComparing(Home::getName, String.CASE_INSENSITIVE_ORDER);
    }

    static void setIcon(HuskHomesAPI huskHomes, Home home, ItemStack icon) {
        if (icon == null || icon.getType().isAir()) {
            updateTags(huskHomes, home, tags -> tags.remove(ICON));
            return;
        }

        ItemStack storedIcon = icon.clone();
        storedIcon.setAmount(1);
        String encoded = Base64.getEncoder().encodeToString(storedIcon.serializeAsBytes());
        updateTags(huskHomes, home, tags -> tags.put(ICON, encoded));
    }

    static void setFavorite(HuskHomesAPI huskHomes, Home home, boolean favorite) {
        updateTags(huskHomes, home, tags -> tags.put(FAVORITE, Boolean.toString(favorite)));
    }

    static void swapOrder(HuskHomesAPI huskHomes, List<Home> homes, Home first, Home second) {
        List<Home> ordered = new ArrayList<>(homes);
        ordered.sort(manualOrder());
        int firstIndex = ordered.indexOf(first);
        int secondIndex = ordered.indexOf(second);
        if (firstIndex < 0 || secondIndex < 0 || firstIndex == secondIndex) {
            return;
        }

        ordered.set(firstIndex, second);
        ordered.set(secondIndex, first);
        saveOrder(huskHomes, ordered);
    }

    static void moveBy(HuskHomesAPI huskHomes, List<Home> homes, Home home, int offset) {
        List<Home> ordered = new ArrayList<>(homes);
        ordered.sort(manualOrder());
        int index = ordered.indexOf(home);
        int target = index + offset;
        if (index < 0 || target < 0 || target >= ordered.size()) {
            return;
        }
        swapOrder(huskHomes, ordered, home, ordered.get(target));
    }

    private static void saveOrder(HuskHomesAPI huskHomes, List<Home> homes) {
        for (int index = 0; index < homes.size(); index++) {
            int currentOrder = index;
            updateTags(huskHomes, homes.get(index), tags -> tags.put(ORDER, Integer.toString(currentOrder)));
        }
    }

    private static void updateTags(HuskHomesAPI huskHomes, Home home, Consumer<Map<String, String>> update) {
        Map<String, String> tags = new HashMap<>(home.getMeta().getTags());
        update.accept(tags);
        Map<String, String> savedTags = Map.copyOf(tags);
        home.getMeta().setTags(savedTags);
        huskHomes.setHomeMetaTags(home, savedTags);
    }
}
