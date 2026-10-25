package dev.kubejseditor.client;

import com.google.gson.*;
import dev.kubejseditor.runtime.EditorRuntime;
import dev.kubejseditor.storage.*;
import java.nio.file.*;

public final class CompatUiPreferences {
    private static Path file(){return EditorRuntime.root().resolve("kubejs/config/kubejs_editor/compat-ui.json");}
    public static boolean advanced(){try{var value=JsonParser.parseString(Files.readString(file())).getAsJsonObject();return value.has("advanced")&&value.get("advanced").getAsBoolean();}catch(Exception ignored){return false;}}
    public static void advanced(boolean enabled){try{JsonObject value=Files.exists(file())?JsonParser.parseString(Files.readString(file())).getAsJsonObject():new JsonObject();value.addProperty("advanced",enabled);FileTransaction.atomicWrite(file(),ProjectStore.JSON.toJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException("无法保存模式设置",e);}}
    private CompatUiPreferences(){}
}
