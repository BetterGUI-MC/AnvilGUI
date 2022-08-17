package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.bettergui.builder.MenuBuilder;
import me.hsgamer.hscore.bukkit.addon.PluginAddon;

public final class Main extends PluginAddon {

    @Override
    public void onEnable() {
        MenuBuilder.INSTANCE.register(AnvilMenu::new, "anvil");
    }
}
