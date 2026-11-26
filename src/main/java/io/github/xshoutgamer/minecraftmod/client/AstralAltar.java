package io.github.xshoutgamer.minecraftmod.client;

import io.github.xshoutgamer.minecraftmod.hex.PersonalMagic.Attunement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class AstralAltar extends Screen {
    private final Attunement current;

    AstralAltar(Attunement current) {
        super(Component.literal("Astral Library"));
        this.current = current;
    }

    @Override
    protected void init() {
        int y = height / 2 - 53;
        for (var option : Attunement.values()) {
            addRenderableWidget(Button.builder(Component.literal(option.title), button -> {
                MagicKeys.attune(option);
                onClose();
            }).bounds(width / 2 - 73, y, 146, 19).tooltip(Tooltip.create(Component.literal(switch (option) {
                case REACH -> "Increase spell reach while the sanctuary lasts.";
                case DURATION -> "Extend newly cast effects by 30%.";
                case RECOVERY -> "Shorten the delay between regular casts.";
                case RENEWAL -> "Restore up to 64 nearby recorded blocks per altar use.";
                case NONE -> "Remove the current attunement.";
            }))).build());
            y += 22;
        }
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        int x = width / 2, y = height / 2;
        gui.fill(0, 0, width, height, 0xb50d0918);
        gui.fillGradient(x - 112, y - 85, x + 112, y + 76, 0xff51223a, 0xff170d21);
        gui.drawCenteredString(font, title, x, y - 76, 0xffffc49c);
        gui.drawCenteredString(font, "Current attunement · " + current.title, x, y + 63, 0xffefaebe);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
