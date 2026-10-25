package dev.kubejseditor.client;

import dev.kubejseditor.recipe.compat.CompatDefinition;
import dev.kubejseditor.config.ConfigNames;
import net.minecraft.client.resources.language.I18n;

public final class CompatLabels {
    public static String name(CompatDefinition.Field field){if(field.labelKey!=null&&!field.labelKey.isBlank()&&I18n.exists(field.labelKey))return I18n.get(field.labelKey);if(I18n.exists(field.label))return I18n.get(field.label);String leaf=field.label.substring(field.label.lastIndexOf('/')+1);String translated=ConfigNames.words(leaf);if(translated.codePoints().anyMatch(c->c>=0x3400&&c<=0x9fff))return translated;return dev.kubejseditor.recipe.compat.CompatFields.basicRole(field)+" · "+leaf;}
    private CompatLabels(){}
}
