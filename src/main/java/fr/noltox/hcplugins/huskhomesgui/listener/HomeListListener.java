package fr.noltox.hcplugins.huskhomesgui.listener;

import fr.noltox.hcplugins.huskhomesgui.gui.HomesMenu;
import net.william278.huskhomes.api.HuskHomesAPI;
import net.william278.huskhomes.event.HomeListEvent;
import net.william278.huskhomes.user.OnlineUser;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

public final class HomeListListener implements Listener {

    private final HuskHomesAPI huskHomes;
    private final HomesMenu homesMenu;

    public HomeListListener(HuskHomesAPI huskHomes, HomesMenu homesMenu) {
        this.huskHomes = huskHomes;
        this.homesMenu = homesMenu;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHomeList(HomeListEvent event) {
        if (event.getIsPublicHomeList()
                || event.getHomes().isEmpty()
                || !(event.getListViewer() instanceof OnlineUser viewer)
                || event.getHomes().stream()
                .anyMatch(home -> !home.getOwner().getUuid().equals(viewer.getUuid()))) {
            return;
        }

        Player player = this.huskHomes.getPlayer(viewer);
        this.homesMenu.open(player, viewer, List.copyOf(event.getHomes()));
        event.setCancelled(true);
    }
}
