package net.astronomy.kubejsrecipeeditor.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public interface RecipePopup {
    void render(GuiGraphics g, Font font, int mouseX, int mouseY);
    boolean mouseClicked(int mouseX, int mouseY);
    boolean keepOpen();

    /** Forwarded from the host screen so popups can host editable text fields. */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

    /** Forwarded from the host screen so popups can host editable text fields. */
    default boolean charTyped(char c, int modifiers) { return false; }
}
