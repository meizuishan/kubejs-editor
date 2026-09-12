package net.astronomy.kubejsrecipeeditor.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class FluidSelectionPopup implements RecipePopup {
    private static final int W             = 210;
    private static final int PAD           = 4;
    private static final int AMOUNT_ROW_H  = 20;
    private static final int FLUID_ROW_H   = 12;
    private static final int MODE_ROW_H    = 14;
    private static final int CLEAR_BTN_H   = 14;
    private static final int CLEAR_BTN_W   = 38;
    private static final long MAX_AMOUNT   = 128000L;

    private final SlotData slot;
    private final int popX, popY, popH;
    private final int minusX, plusX;
    private final int amtBtnY;
    // Editable amount field — lets the user type any value (e.g. 33, 250), not just step multiples.
    private final EditBox amountBox;
    // Mode toggle button: switches between "fluid id" and "fluid tag" modes
    private final int modeBtnX, modeBtnY, modeBtnW;
    // Clear button: clears fluid
    private final int clearBtnX, clearBtnY;
    private boolean keepOpen = false;
    // Guards against the responder clobbering the field while we programmatically set its value.
    private boolean suppressResponder = false;

    public FluidSelectionPopup(SlotData slot, int screenW, int screenH) {
        this.slot = slot;
        // Rows: amount row, separator, fluid-name row, separator, mode-toggle row, separator, clear-button row
        popH = PAD + AMOUNT_ROW_H + 2 + FLUID_ROW_H + 2 + MODE_ROW_H + 2 + CLEAR_BTN_H + PAD;
        popX = Math.min(slot.x + slot.w + 2, screenW - W - 4);
        popY = Math.min(slot.y, screenH - popH - 4);

        int amtY = popY + PAD;
        amtBtnY = amtY + 3;
        minusX = popX + 60;
        int boxX = minusX + 16;
        int boxW = 46;
        plusX  = boxX + boxW + 2;

        amountBox = new EditBox(Minecraft.getInstance().font, boxX, amtBtnY, boxW, 12, Component.empty());
        amountBox.setMaxLength(6);
        amountBox.setBordered(true);
        amountBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,6}"));
        amountBox.setResponder(txt -> {
            if (suppressResponder || txt.isEmpty()) return;
            try {
                long v = Long.parseLong(txt);
                slot.fluidAmount = Math.max(1L, Math.min(MAX_AMOUNT, v));
            } catch (NumberFormatException ignored) { /* keep previous value */ }
        });
        setBoxValue(slot.fluidAmount);
        amountBox.setFocused(true);

        // Mode toggle — right-aligned inside popup
        modeBtnW = 60;
        modeBtnX = popX + W - PAD - modeBtnW;
        modeBtnY = popY + PAD + AMOUNT_ROW_H + 2 + FLUID_ROW_H + 4;

        // Clear button — left-aligned in last row
        clearBtnX = popX + PAD;
        clearBtnY = popY + PAD + AMOUNT_ROW_H + 2 + FLUID_ROW_H + 2 + MODE_ROW_H + 4;
    }

    private void setBoxValue(long value) {
        suppressResponder = true;
        amountBox.setValue(String.valueOf(value));
        suppressResponder = false;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        // Background + border
        g.fill(popX - 1, popY - 1, popX + W + 1, popY + popH + 1, 0xFF555555);
        g.fill(popX, popY, popX + W, popY + popH, 0xFF1A1A1A);

        // ── Amount row ──────────────────────────────────────────────────────
        int amtY = popY + PAD;
        g.drawString(font, "Amount (mB):", popX + PAD, amtY + 6, 0xFFAAAAAA, false);

        boolean minusHover = mouseX >= minusX && mouseX < minusX + 14 && mouseY >= amtBtnY && mouseY < amtBtnY + 12;
        g.fill(minusX, amtBtnY, minusX + 14, amtBtnY + 12, minusHover ? 0xFF666666 : 0xFF444444);
        g.drawString(font, "-", minusX + 4, amtY + 5, 0xFFFFFFFF, false);

        // Editable amount field (type any value)
        amountBox.render(g, mouseX, mouseY, 0f);

        boolean plusHover = mouseX >= plusX && mouseX < plusX + 14 && mouseY >= amtBtnY && mouseY < amtBtnY + 12;
        g.fill(plusX, amtBtnY, plusX + 14, amtBtnY + 12, plusHover ? 0xFF666666 : 0xFF444444);
        g.drawString(font, "+", plusX + 3, amtY + 5, 0xFFFFFFFF, false);

        // ── Separator ────────────────────────────────────────────────────────
        g.fill(popX + PAD, popY + PAD + AMOUNT_ROW_H,
               popX + W - PAD, popY + PAD + AMOUNT_ROW_H + 1, 0xFF444444);

        // ── Fluid name ───────────────────────────────────────────────────────
        int nameY = popY + PAD + AMOUNT_ROW_H + 2;
        String fluidName;
        if (slot.useFluidTag) {
            fluidName = slot.selectedFluidTag != null ? "#" + slot.selectedFluidTag : "(tag mode — no tag set)";
        } else {
            fluidName = slot.fluidId != null ? slot.fluidId.toString() : "(no fluid set)";
        }
        // Truncate if too wide
        String displayName = fluidName;
        int maxNameW = W - PAD * 2;
        while (font.width(displayName) > maxNameW && displayName.length() > 4) {
            displayName = displayName.substring(0, displayName.length() - 4) + "...";
        }
        g.drawString(font, displayName, popX + PAD, nameY + 1, 0xFF55BBFF, false);

        // ── Separator ────────────────────────────────────────────────────────
        g.fill(popX + PAD, nameY + FLUID_ROW_H + 1,
               popX + W - PAD, nameY + FLUID_ROW_H + 2, 0xFF444444);

        // ── Mode toggle button ───────────────────────────────────────────────
        String modeLbl = slot.useFluidTag ? "→ Fluid ID" : "→ Fluid Tag";
        boolean modeHover = mouseX >= modeBtnX && mouseX < modeBtnX + modeBtnW
                && mouseY >= modeBtnY && mouseY < modeBtnY + MODE_ROW_H;
        g.fill(modeBtnX, modeBtnY, modeBtnX + modeBtnW, modeBtnY + MODE_ROW_H,
                modeHover ? 0xFF556688 : 0xFF334455);
        g.drawCenteredString(font, modeLbl, modeBtnX + modeBtnW / 2, modeBtnY + 3, 0xFFCCDDFF);

        String modeLabel = slot.useFluidTag ? "Tag mode" : "ID mode";
        g.drawString(font, modeLabel, popX + PAD, modeBtnY + 3, 0xFFAAAAAA, false);

        // ── Separator ────────────────────────────────────────────────────────
        g.fill(popX + PAD, clearBtnY - 2,
               popX + W - PAD, clearBtnY - 1, 0xFF444444);

        // ── Clear button ─────────────────────────────────────────────────────
        boolean clearHover = mouseX >= clearBtnX && mouseX < clearBtnX + CLEAR_BTN_W
                && mouseY >= clearBtnY && mouseY < clearBtnY + CLEAR_BTN_H;
        g.fill(clearBtnX, clearBtnY, clearBtnX + CLEAR_BTN_W, clearBtnY + CLEAR_BTN_H,
                clearHover ? 0xFF884444 : 0xFF553333);
        g.drawCenteredString(font, "Clear", clearBtnX + CLEAR_BTN_W / 2, clearBtnY + 3, 0xFFFFAAAA);

        // Hint
        int hintX = clearBtnX + CLEAR_BTN_W + 6;
        g.drawString(font, "Type amount, or ± (Ctrl=10 Shift=1000)", hintX, clearBtnY + 3, 0xFF666666, false);
    }

    public boolean mouseClicked(int mouseX, int mouseY) {
        keepOpen = false;
        boolean shift = Screen.hasShiftDown();
        boolean ctrl  = Screen.hasControlDown();
        long step = shift ? 1000L : ctrl ? 10L : 100L;

        // Amount text field — click to focus and edit directly
        if (amountBox.isMouseOver(mouseX, mouseY)) {
            amountBox.setFocused(true);
            amountBox.mouseClicked(mouseX, mouseY, 0);
            keepOpen = true;
            return true;
        }

        // Minus button
        if (mouseX >= minusX && mouseX < minusX + 14 && mouseY >= amtBtnY && mouseY < amtBtnY + 12) {
            slot.fluidAmount = Math.max(1L, slot.fluidAmount - step);
            setBoxValue(slot.fluidAmount);
            keepOpen = true;
            return true;
        }
        // Plus button
        if (mouseX >= plusX && mouseX < plusX + 14 && mouseY >= amtBtnY && mouseY < amtBtnY + 12) {
            slot.fluidAmount = Math.min(MAX_AMOUNT, slot.fluidAmount + step);
            setBoxValue(slot.fluidAmount);
            keepOpen = true;
            return true;
        }
        // Mode toggle button
        if (mouseX >= modeBtnX && mouseX < modeBtnX + modeBtnW
                && mouseY >= modeBtnY && mouseY < modeBtnY + MODE_ROW_H) {
            slot.useFluidTag = !slot.useFluidTag;
            // When switching to tag mode, try to derive a generic tag name from the fluidId
            if (slot.useFluidTag && slot.selectedFluidTag == null && slot.fluidId != null) {
                // Default heuristic: forge/c fluid tags often match the fluid namespace:path
                slot.selectedFluidTag = ResourceLocation.tryParse(
                        "c:" + slot.fluidId.getPath());
            }
            // When switching back to id mode, keep fluidId as-is
            keepOpen = true;
            return true;
        }
        // Clear button
        if (mouseX >= clearBtnX && mouseX < clearBtnX + CLEAR_BTN_W
                && mouseY >= clearBtnY && mouseY < clearBtnY + CLEAR_BTN_H) {
            // Clear fluid but keep the slot in fluid mode (isFluid stays true)
            slot.fluidId = null;
            slot.ingredient = net.minecraft.world.item.ItemStack.EMPTY;
            slot.useFluidTag = false;
            slot.selectedFluidTag = null;
            slot.fluidAmount = 1000L;
            keepOpen = false; // close popup after clearing
            return true;
        }
        return isInside(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (amountBox.isFocused()) {
            // Commit an empty field back to a valid minimum before Enter closes editing.
            if ((keyCode == 257 || keyCode == 335) && amountBox.getValue().isEmpty()) {
                slot.fluidAmount = 1L;
                setBoxValue(slot.fluidAmount);
                keepOpen = true;
                return true;
            }
            if (amountBox.keyPressed(keyCode, scanCode, modifiers)) {
                keepOpen = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (amountBox.isFocused() && amountBox.charTyped(c, modifiers)) {
            keepOpen = true;
            return true;
        }
        return false;
    }

    public boolean keepOpen() { return keepOpen; }

    public boolean isInside(int x, int y) {
        return x >= popX && x < popX + W && y >= popY && y < popY + popH;
    }
}
