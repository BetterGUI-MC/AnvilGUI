package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.bettergui.builder.MenuBuilder;
import me.hsgamer.hscore.expansion.common.Expansion;

public final class Main implements Expansion {

    @Override
    public void onEnable() {
        MenuBuilder.INSTANCE.register(AnvilMenu::new, "anvil");
    }
}
