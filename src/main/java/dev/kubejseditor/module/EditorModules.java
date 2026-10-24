package dev.kubejseditor.module;

import java.util.*;

public final class EditorModules {
    private static final Map<String,EditorModule> MODULES=new LinkedHashMap<>();
    static {register(new EditorModule(){public String id(){return "create";}public String title(){return "机械动力";}public boolean ownsConfig(String mod){return mod.contains("create")||Set.of("vintageimprovements","aeronautics","simulated","offroad","sable").contains(mod);}});}
    private EditorModules(){}
    public static void register(EditorModule module){MODULES.put(module.id(),module);}
    public static Collection<EditorModule> all(){return List.copyOf(MODULES.values());}
    public static boolean ownsCreateConfig(String id){return MODULES.get("create").ownsConfig(id);}
}
