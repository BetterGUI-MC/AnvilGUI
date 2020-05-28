package me.hsgamer.bettergui.anvilgui;

import me.hsgamer.bettergui.builder.MenuBuilder;
import me.hsgamer.bettergui.object.addon.Addon;

public final class Main extends Addon {

  @Override
  public void onEnable() {
    MenuBuilder.register("anvil", AnvilMenu.class);
  }
}
