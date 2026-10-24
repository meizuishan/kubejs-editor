package dev.kubejseditor.storage;

import com.google.gson.*;
import dev.kubejseditor.model.EditorProject;
import java.io.IOException;
import java.nio.file.*;

public final class ProjectStore {
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().registerTypeAdapter(dev.kubejseditor.model.StorageOptions.class,(JsonDeserializer<dev.kubejseditor.model.StorageOptions>)(value,type,context)->dev.kubejseditor.model.StorageOptions.read(value)).create();
    public static final String FILE = "kubejs/config/kubejs_editor/project.json";
    public static EditorProject read(Path root) throws IOException {
        Path file = root.resolve(FILE);
        if (!Files.exists(file)) return new EditorProject();
        EditorProject project = JSON.fromJson(Files.readString(file), EditorProject.class);
        if (project == null) throw new IOException("草稿为空");
        if(project.schemaVersion==1){
            Path legacy=file.resolveSibling("project.v1.backup.json");
            if(!Files.exists(legacy))Files.copy(file,legacy);
            project.schemaVersion=2;
        }
        project.validateStructure(); return project;
    }
    public static void saveDraft(Path root, EditorProject project) throws IOException {
        project.validateStructure();
        Path file = root.resolve(FILE); Files.createDirectories(file.getParent());
        if (Files.exists(file)) Files.copy(file, file.resolveSibling("project.previous.json"), StandardCopyOption.REPLACE_EXISTING);
        FileTransaction.atomicWrite(file, JSON.toJson(project).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
