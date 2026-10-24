package dev.kubejseditor.client;

import com.google.gson.*;
import java.nio.file.*;

/** Per-instance view settings, separate from gameplay operations and generated scripts. */
public final class PanelPreferences {
    private static final String FILE="kubejs/config/kubejs_editor/ui.json";
    public double left=-1,right=-1;
    public static PanelPreferences read(Path root){var value=new PanelPreferences();try{var json=JsonParser.parseString(Files.readString(root.resolve(FILE))).getAsJsonObject();value.left=ratio(json,"left");value.right=ratio(json,"right");}catch(Exception ignored){}return value;}
    private static double ratio(JsonObject json,String key){double n=json.get(key).getAsDouble();return Double.isFinite(n)&&n>=.1&&n<=.4?n:-1;}
    public void save(Path root)throws java.io.IOException{JsonObject json=new JsonObject();json.addProperty("left",left);json.addProperty("right",right);Path file=root.resolve(FILE);Files.createDirectories(file.getParent());Files.writeString(file,json.toString());}
}
