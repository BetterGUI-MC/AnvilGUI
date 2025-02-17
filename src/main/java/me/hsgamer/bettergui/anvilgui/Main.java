package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.bettergui.api.addon.GetLogger;
import me.hsgamer.bettergui.builder.MenuBuilder;
import me.hsgamer.hscore.expansion.common.Expansion;
import me.hsgamer.hscore.expansion.extra.expansion.DataFolder;
import me.hsgamer.hscore.expansion.extra.expansion.GetClassLoader;
import me.hsgamer.hscore.logger.common.LogLevel;

public final class Main implements Expansion, GetLogger, GetClassLoader, DataFolder {
    @Override
    public boolean onLoad() {
        try {
            new LibLoader(this).loadLibrary();
            return true;
        } catch (Exception e) {
            getLogger().log(LogLevel.ERROR, "An error occurred while loading the library", e);
            return false;
        }
    }

    @Override
    public void onEnable() {
        MenuBuilder.INSTANCE.register(AnvilMenu::new, "anvil");
    }
}
