package dev.kubejseditor.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Review captures exact preimages; applying never silently overwrites an external edit. */
public final class FileTransaction {
    public record Change(Path path, byte[] before, byte[] after) {
        public String beforeText() { return before == null ? "" : new String(before, StandardCharsets.UTF_8); }
        public String afterText() { return after == null ? "[删除文件]" : new String(after, StandardCharsets.UTF_8); }
    }
    public record Review(Path root, List<Change> changes, boolean restartRequired, boolean resourcesChanged) {}
    private FileTransaction() {}

    /** Activation changes metadata after reload; keep those writes in the same rollback boundary. */
    public static void commitMetadata(Path root,Path backup,String relative,byte[] bytes)throws IOException{
        commitMetadata(root,backup,Map.of(relative,bytes));
    }
    public static void commitMetadata(Path root,Path backup,Map<String,byte[]> desired)throws IOException{
        Map<String,byte[]> before=new LinkedHashMap<>();Path manifest=backup.resolve("manifest.tsv"),hashes=backup.resolve("after-sha256.tsv");
        List<String> rows=new ArrayList<>(Files.readAllLines(manifest)),originalHashes=new ArrayList<>(Files.readAllLines(hashes)),values=new ArrayList<>(originalHashes);
        for(var entry:desired.entrySet()){
            String relative=entry.getKey();if(!relative.startsWith("kubejs/config/kubejs_editor/"))throw new IOException("无效事务元数据");
            Path file=checkedPath(root,relative);byte[] old=Files.exists(file)?Files.readAllBytes(file):null;before.put(relative,old);
            if(rows.stream().noneMatch(s->s.endsWith("\t"+relative))){
                rows.add((old==null?"NEW\t":"OLD\t")+relative);if(old!=null){Path saved=backup.resolve(relative);Files.createDirectories(saved.getParent());Files.write(saved,old);}
            }
            if(originalHashes.stream().noneMatch(s->s.endsWith("\t"+relative)))originalHashes.add((old==null?"-":hash(old))+"\t"+relative);
            values.removeIf(s->s.endsWith("\t"+relative));values.add(hash(entry.getValue())+"\t"+relative);
        }
        Files.write(manifest,rows);
        try{
            for(var entry:desired.entrySet())atomicWrite(checkedPath(root,entry.getKey()),entry.getValue());
            atomicWrite(hashes,String.join("\n",values).getBytes(StandardCharsets.UTF_8));
        }catch(IOException failure){
            for(var entry:before.entrySet())try{Path file=checkedPath(root,entry.getKey());if(entry.getValue()==null)Files.deleteIfExists(file);else atomicWrite(file,entry.getValue());}catch(IOException restore){failure.addSuppressed(restore);}
            try{Files.write(hashes,originalHashes);}catch(IOException restore){failure.addSuppressed(restore);}throw failure;
        }
    }

    public static Review review(Path root, Map<String, byte[]> desired, boolean restart, boolean resources) throws IOException {
        root = root.toAbsolutePath().normalize(); List<Change> changes = new ArrayList<>();
        for (var e : desired.entrySet()) {
            Path p = checkedPath(root, e.getKey()); byte[] before = Files.exists(p) ? Files.readAllBytes(p) : null;
            if (!Arrays.equals(before, e.getValue())) changes.add(new Change(p, before, e.getValue()));
        }
        return new Review(root, List.copyOf(changes), restart, resources);
    }
    public static Path apply(Review review) throws IOException {
        for (Change c : review.changes) {
            checkedPath(review.root, review.root.relativize(c.path).toString());
            byte[] current = Files.exists(c.path) ? Files.readAllBytes(c.path) : null;
            if (!Arrays.equals(current, c.before)) throw new IOException("预览后文件已被外部修改，请重新预览：" + c.path);
        }
        Path backup = review.root.resolve("kubejs/config/kubejs_editor/backups/" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID());
        Files.createDirectories(backup);
        List<String> manifest = new ArrayList<>(); int n = 0;
        for (Change c : review.changes) {
            String relative = review.root.relativize(c.path).toString().replace('\\','/');
            manifest.add((c.before == null ? "NEW\t" : "OLD\t") + relative);
            if (c.before != null) { Path old = backup.resolve(relative); Files.createDirectories(old.getParent()); Files.write(old, c.before); }
        }
        Files.write(backup.resolve("manifest.tsv"), manifest, StandardCharsets.UTF_8);
        List<String> afterHashes=new ArrayList<>();for(Change c:review.changes)afterHashes.add((c.after==null?"-":hash(c.after))+"\t"+review.root.relativize(c.path).toString().replace('\\','/'));
        Files.write(backup.resolve("after-sha256.tsv"),afterHashes,StandardCharsets.UTF_8);
        try {
            for (Change c : review.changes) { if (c.after == null) Files.deleteIfExists(c.path); else atomicWrite(c.path, c.after); n++; }
        } catch (IOException failure) {
            try { rollback(review.root, backup); } catch (IOException restore) { failure.addSuppressed(restore); }
            throw failure;
        }
        try {atomicWrite(review.root.resolve("kubejs/config/kubejs_editor/last-transaction.txt"),review.root.relativize(backup).toString().getBytes(StandardCharsets.UTF_8));}
        catch(IOException failure){try{rollback(review.root,backup);}catch(IOException restore){failure.addSuppressed(restore);}throw failure;}
        return backup;
    }
    public static void rollback(Path root, Path backup) throws IOException {
        Path backups = root.toAbsolutePath().normalize().resolve("kubejs/config/kubejs_editor/backups");
        if (!backup.toAbsolutePath().normalize().startsWith(backups)) throw new IOException("无效备份位置");
        checkedPath(root,root.toAbsolutePath().normalize().relativize(backup.toAbsolutePath().normalize()).toString());
        Map<String,String> afterHashes=new HashMap<>();Path hashes=backup.resolve("after-sha256.tsv");
        if(Files.exists(hashes))for(String row:Files.readAllLines(hashes)){String[] p=row.split("\t",2);if(p.length==2)afterHashes.put(p[1],p[0]);}
        for(String row:Files.readAllLines(backup.resolve("manifest.tsv"),StandardCharsets.UTF_8)){
            String[] p=row.split("\t",2);if(p.length!=2)throw new IOException("备份清单无效");Path path=checkedPath(root,p[1]);byte[] current=Files.exists(path)?Files.readAllBytes(path):null;
            byte[] original=p[0].equals("NEW")?null:Files.readAllBytes(backup.resolve(p[1]));String expected=afterHashes.get(p[1]);
            if(expected!=null&&!Arrays.equals(current,original)&&!expected.equals(current==null?"-":hash(current)))throw new IOException("应用后文件被外部修改，停止回滚以保留修改："+path);
        }
        for (String row : Files.readAllLines(backup.resolve("manifest.tsv"), StandardCharsets.UTF_8)) {
            String[] part = row.split("\t", 2); if (part.length != 2) throw new IOException("备份清单无效");
            Path p = checkedPath(root.toAbsolutePath().normalize(), part[1]);
            if (part[0].equals("NEW")) Files.deleteIfExists(p);
            else if (part[0].equals("OLD")) atomicWrite(p, Files.readAllBytes(backup.resolve(part[1])));
            else throw new IOException("备份操作无效");
        }
    }
    public static Path checkedPath(Path root, String relative) throws IOException {
        if (relative.indexOf('\t') >= 0 || relative.indexOf('\n') >= 0) throw new IOException("无效文件路径");
        root = root.toAbsolutePath().normalize(); Path p = root.resolve(relative).normalize();
        if (!p.startsWith(root) || p.equals(root)) throw new IOException("文件超出当前实例：" + relative);
        Path cursor = p;
        while (cursor != null && !cursor.equals(root)) { if (Files.isSymbolicLink(cursor)) throw new IOException("输出路径不能经过符号链接：" + relative); cursor = cursor.getParent(); }
        return p;
    }
    public static void atomicWrite(Path file, byte[] content) throws IOException {
        Files.createDirectories(file.getParent()); Path tmp = Files.createTempFile(file.getParent(), ".kubejs-editor-", ".tmp");
        try {
            Files.write(tmp, content);
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
    private static String hash(byte[] bytes){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
