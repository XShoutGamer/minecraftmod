package io.github.xshoutgamer.minecraftmod.client;

import io.github.xshoutgamer.minecraftmod.hex.PersonalMagic.Power;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class GrimoireScreen extends Screen {
    private final boolean dark;

    GrimoireScreen(boolean dark) {
        super(Component.literal(dark ? "Darkhold" : "Independent Chaos Magic"));
        this.dark = dark;
    }

    @Override
    protected void init() {
        int start = dark ? Power.DREAM.ordinal() : 0;
        int end = dark ? Power.values().length : Power.DREAM.ordinal();
        int pageWidth = Math.min(158, (width - 24) / 2);
        for (int i = start; i < end; i++) {
            var power = Power.values()[i];
            int slot = i - start;
            addRenderableWidget(Button.builder(Component.literal(power.title), button -> {
                MagicKeys.select(dark, power);
                onClose();
            }).bounds(width / 2 - pageWidth + slot / 5 * (pageWidth + 8), height / 2 - 58 + slot % 5 * 22,
                    pageWidth - 8, 19).build(builder -> new SpellButton(builder, power, dark)));
        }
    }

    @Override
    public void renderBackground(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        int x = width / 2, y = height / 2;
        int page = Math.min(164, (width - 12) / 2);
        gui.fill(0, 0, width, height, 0x85100712);
        gui.fill(x - page - 4, y - 103, x + page + 4, y + 77, 0xff180c14);
        gui.fillGradient(x - page, y - 99, x - 2, y + 73, 0xff653e3d, 0xff291923);
        gui.fillGradient(x + 2, y - 99, x + page, y + 73, 0xff653e3d, 0xff291923);
        gui.drawCenteredString(font, title, x, y - 88, 0xffff9679);
        var selected = MagicKeys.selection(dark);
        gui.drawCenteredString(font, Component.literal(selected.title), x, y + 59, 0xffffbca5);
        double clock = (System.nanoTime() % 12000000000L) / 1e9;
        if (io.github.xshoutgamer.minecraftmod.CelestiaConfig.REDUCED_MOTION.get()) clock = 0;
        for (int i = 0; i < 52; i++) {
            double a = i * Math.PI / 26 + clock * .37;
            int px = x + (int) (Math.cos(a) * 22), py = y + 59 + (int) (Math.sin(a) * 13);
            if (i % 4 == 0 && Math.abs(px - x) > 15) gui.fill(px, py, px + 1, py + 1, 0xffb84948);
        }
    }

    private static final class SpellButton extends Button {
        private final Power power;
        private final boolean dark;

        SpellButton(Button.Builder builder, Power power, boolean dark) {
            super(builder);
            this.power = power;
            this.dark = dark;
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partial) {
            boolean highlight = isHoveredOrFocused();
            boolean current = MagicKeys.selection(dark) == power;
            int x = getX(), y = getY(), end = x + getWidth();
            int accent = highlight ? 0xffffae8e : current ? 0xfff27265 : 0xffab6059;
            gui.fill(x, y, end, y + getHeight(), highlight ? 0xff7c303f : 0xff351b27);
            gui.fill(x, y, x + 2, y + getHeight(), accent);
            gui.fill(x + 5, y + 8, x + 8, y + 11, accent);
            if (power.ordinal() % 2 == 0) gui.fill(x + 6, y + 5, x + 7, y + 14, accent);
            if (power.ordinal() % 3 == 0) gui.fill(x + 3, y + 9, x + 10, y + 10, accent);
            gui.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(),
                    x + getWidth() / 2 + 3, y + 5, highlight ? 0xffffead3 : 0xffffbea2);
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
