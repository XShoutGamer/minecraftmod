package io.github.xshoutgamer.minecraftmod.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.xshoutgamer.minecraftmod.CelestiaConfig;
import io.github.xshoutgamer.minecraftmod.hex.HexSpell;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class HexWheel extends Screen {
    private static final HexSpell[] SPELLS = HexSpell.values();
    private static final double STEP = Math.PI * 2 / SPELLS.length;
    private final KeyMapping mapping;
    private int hovered;
    private boolean commit = true;

    HexWheel(KeyMapping mapping) {
        super(Component.literal("Scarlet Hex"));
        this.mapping = mapping;
        hovered = HexClient.selected.ordinal();
    }

    static boolean physicallyHeld(KeyMapping mapping) {
        var game = Minecraft.getInstance();
        var key = mapping.getKey();
        long window = game.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM) return key.getValue() >= 0 && InputConstants.isKeyDown(window, key.getValue());
        return mapping.isDown();
    }

    boolean keyHeld() { return physicallyHeld(mapping); }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partial) {
        gui.fill(0, 0, width, height, 0xa510030a);
        int cx = width / 2, cy = (height - 20) / 2;
        float radius = Math.min(104, Math.min(height * .32F, width * .40F));
        double dx = mouseX - cx, dy = mouseY - cy;
        if (Math.hypot(dx, dy) > radius * .28 && Math.hypot(dx, dy) < radius * 1.55) {
            hovered = Math.floorMod((int) Math.round((Math.atan2(dy, dx) + Math.PI / 2) / STEP), SPELLS.length);
        }
        var pose = gui.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0);
        float time = CelestiaConfig.REDUCED_MOTION.get() ? 0 : (System.nanoTime() % 20_000_000_000L) / 1_000_000_000F;
        for (int i = 0; i < 120; i++) {
            double angle = i * Math.PI / 60;
            int x = (int) (Math.cos(angle) * (radius + 11));
            int y = (int) (Math.sin(angle) * (radius + 11));
            int pulse = (int) (139 + 90 * Math.sin(angle * 6 - time * 5));
            gui.fill(x - 1, y - 1, x + 2, y + 2, pulse << 24 | 0xf7245b);
            if (i % 5 == 0) {
                double inner = angle + time * .31;
                int ix = (int) (Math.cos(inner) * (radius * .37));
                int iy = (int) (Math.sin(inner) * (radius * .37));
                gui.fill(ix - 1, iy - 1, ix + 2, iy + 2, 0xffd6244f);
            }
        }
        boolean available = HexClient.canCast();
        for (int i = 0; i < SPELLS.length; i++) {
            double angle = -Math.PI / 2 + i * STEP;
            int x = (int) (Math.cos(angle) * radius), y = (int) (Math.sin(angle) * radius);
            int size = i == hovered ? 13 : 10;
            int tint = i == hovered ? 0xffffc1ca : available ? 0xfff24269 : 0xffa77084;
            for (int row = -size; row <= size; row++) {
                int span = size - Math.abs(row) / 2;
                gui.fill(x - span, y + row, x + span + 1, y + row + 1, i == hovered ? 0xdab61d48 : 0xd83d0c24);
            }
            icon(gui, x, y, i, tint);
        }
        pose.popPose();
        gui.drawCenteredString(font, title, cx, cy - 4, 0xffffdce1);
        var spell = SPELLS[hovered];
        gui.drawCenteredString(font, spell.title, cx, height - 39, 0xffffa5b8);
        gui.drawCenteredString(font, spell.description, cx, height - 25, 0xffead1d8);
        if (available) gui.drawCenteredString(font, "Assign hovered spell with " + HexClient.shortcutHint(),
                cx, height - 12, 0xffcf8ea0);
        if (!available) gui.drawCenteredString(font, "Available once the Hex establishes", cx, height - 12, 0xffcf8ea0);
    }

    private static final int[][][] ICONS = {
        {{-7,3,-4,0,-1,1,1,-5,3,0,5,-4,7,-1,5,5,0,7,-4,5,-7,3}},
        {{-7,-3,-2,-6,4,-5,7,0,5,6,-2,7,-6,4,-7,-3},{-2,-6,0,1,7,0},{0,1,-2,7}},
        {{0,-8,5,-5,7,0,5,5,0,8,-5,5,-7,0,-5,-5,0,-8},{-6,1,-2,-2,3,-1,6,2},{1,-4,-1,0,2,4}},
        {{-7,-5,0,-8,7,-5,6,3,0,8,-6,3,-7,-5},{0,-5,0,4},{-3,-2,3,-2}},
        {{-7,2,-6,-6,-2,-1,0,-8,3,-1,7,-6,7,2,-7,2},{-4,6,0,4,4,6}},
        {{-6,7,-1,2,-4,-1,2,-4,3,-8},{0,8,3,2,1,0,7,-4},{-7,-4,-5,-2,0,-3}},
        {{0,-8,7,-4,7,4,0,8,-7,4,-7,-4,0,-8},{0,-5,3,-1,0,5,-3,-1,0,-5},{-6,0,6,0}},
        {{-6,-7,6,-7,3,-3,-3,3,-6,7,6,7,3,3,-3,-3,-6,-7},{-3,5,3,5}},
        {{-7,5,-2,3,2,3,7,5},{-5,0,0,-4,5,0},{0,-7,0,3},{-2,-5,0,-7,2,-5}},
        {{-7,7,-7,2,-5,0,-5,-5,-2,-7,1,-5,1,0,4,3,4,7,-7,7},{2,-7,5,-5,5,0,7,2,7,6}},
        {{0,-8,7,-4,6,5,0,8,-6,5,-7,-4,0,-8},{0,8,0,1,-7,-4},{0,1,7,-4},{0,1,0,-8}},
        {{0,-8,2,-3,8,-3,4,1,6,7,0,4,-6,7,-4,1,-8,-3,-2,-3,0,-8},{0,-3,3,1,0,3,-3,1,0,-3}}
    };

    private static void icon(GuiGraphics gui, int x, int y, int spell, int color) {
        for (var line : ICONS[spell]) {
            for (int index = 2; index < line.length; index += 2) {
                int ax = line[index - 2], ay = line[index - 1], bx = line[index], by = line[index + 1];
                int steps = Math.max(Math.abs(ax - bx), Math.abs(ay - by));
                for (int i = 0; i <= steps; i++) {
                    float progress = i / (float) Math.max(1, steps);
                    int px = x + Math.round(ax + (bx - ax) * progress);
                    int py = y + Math.round(ay + (by - ay) * progress);
                    gui.fill(px, py, px + 1, py + 1, color);
                }
            }
        }
    }

    @Override
    public void onClose() {
        if (commit) HexClient.selected(SPELLS[hovered]);
        super.onClose();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        int slot = HexClient.slotFor(key, scanCode);
        if (slot >= 0) {
            HexClient.assign(slot, SPELLS[hovered]);
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) commit = false;
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            onClose();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
