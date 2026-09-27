package fr.noltox.hcplugins.huskhomesgui;

import fr.noltox.hcplugins.core.api.HCPluginsCore;
import fr.noltox.hcplugins.core.api.command.CoreCommandRegistration;
import fr.noltox.hcplugins.huskhomesgui.command.HomesCommand;
import fr.noltox.hcplugins.huskhomesgui.gui.HomesMenu;
import fr.noltox.hcplugins.huskhomesgui.listener.HomeListListener;
import net.william278.huskhomes.api.HuskHomesAPI;
import org.bukkit.plugin.java.JavaPlugin;

public final class HCHuskHomesGUI extends JavaPlugin {

    private CoreCommandRegistration commandRegistration;
    private HomesMenu homesMenu;

    @Override
    public void onEnable() {
        HuskHomesAPI huskHomes = HuskHomesAPI.getInstance();
        homesMenu = new HomesMenu(this, huskHomes);

        getServer().getPluginManager().registerEvents(
                new HomeListListener(huskHomes, homesMenu),
                this
        );

        HomesCommand commands = new HomesCommand(this, HCPluginsCore.translations(this), homesMenu, getLogger());
        commandRegistration = HCPluginsCore.require(this).register(
                this,
                "huskhomesgui",
                "Interface des homes HuskHomes",
                java.util.List.of(),
                commands
        );
    }

    @Override
    public void onDisable() {
        try {
            if (commandRegistration != null) {
                commandRegistration.close();
            }
        } finally {
            if (homesMenu != null) {
                homesMenu.shutdown();
            }
        }
    }
}
