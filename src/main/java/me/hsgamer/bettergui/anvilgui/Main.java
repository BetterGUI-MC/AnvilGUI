package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.bettergui.api.addon.BetterGUIAddon;
import me.hsgamer.bettergui.builder.MenuBuilder;

public final class Main extends BetterGUIAddon {

    @Override
    public void onEnable() {
        MenuBuilder.INSTANCE.register(AnvilMenu::new, "anvil");
    }
}
