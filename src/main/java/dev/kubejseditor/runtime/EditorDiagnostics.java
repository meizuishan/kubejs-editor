package dev.kubejseditor.runtime;

import dev.kubejseditor.storage.FileTransaction;
import dev.kubejseditor.storage.ProjectStore;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Durable, bounded diagnostics for reload/transaction failures. The UI only kept the last
 * activation error in memory, so a failed reload that was later rolled back left no usable
 * evidence. This file is intentionally outside the managed workspace state: it is written on
 * failures and on compensated successes, and never changes the editor's generated scripts.
 */
public final class EditorDiagnostics {
    public static final String FILE="kubejs/config/kubejs_editor/last-diagnostic.json";
    private static final int MAX_STACK_LINES=40;
    private static volatile String lastMessage="";
    private static volatile String lastPath="";

    public static final class Diagnostic {
        public int schemaVersion=1;
        public String time=Instant.now().toString();
        public String operation="";
        public String phase="";
        /** success, compensated-success or failure. */
        public String outcome="";
        public String message="";
        public Error error;
        public List<String> recipeIds=new ArrayList<>();
        public List<String> serializers=new ArrayList<>();
        public Generations generations=new Generations();
        public List<String> kubeJsErrors=new ArrayList<>();
        public Compensation compensation=new Compensation();
        public Verification verification=new Verification();
        public boolean promoted;
        public String backup="";
        public List<String> notes=new ArrayList<>();
        /** Logged with the full stack, but never serialized into the diagnostic file. */
        public transient Throwable throwable;
    }

    public static final class Error {
        public String type="";
        public String message="";
        public List<String> stack=new ArrayList<>();
        public static Error of(Throwable throwable){
            Error error=new Error();
            if(throwable==null)return error;
            // The reload future usually wraps the real listener failure in CompletionException.
            // Keep the wrapper's full stack, but report the root cause as the primary error.
            Throwable root=throwable;
            while(root.getCause()!=null&&root.getCause()!=root)root=root.getCause();
            error.type=root.getClass().getName();
            error.message=root.getMessage()==null?"":root.getMessage();
            StringWriter writer=new StringWriter();
            throwable.printStackTrace(new PrintWriter(writer));
            List<String> lines=new ArrayList<>();
            for(String line:writer.toString().replace("\r","").split("\n")){
                if(lines.size()>=MAX_STACK_LINES)break;
                lines.add(line.length()>600?line.substring(0,600)+"…":line);
            }
            error.stack=lines;
            return error;
        }
    }

    public static final class Generations {
        public long before;
        public long after;
    }

    public static final class Compensation {
        public boolean attempted;
        public boolean tagsSent;
        public boolean recipesSent;
        public int players;
        public String error="";
    }

    public static final class Verification {
        public boolean passed;
        public String detail="";
    }

    public static synchronized void record(Path root, Diagnostic diagnostic){
        if(diagnostic==null)return;
        if(diagnostic.time==null||diagnostic.time.isBlank())diagnostic.time=Instant.now().toString();
        try{
            Path file=FileTransaction.checkedPath(root,FILE);
            // A later plain success must not silently erase the last exceptional result.
            if("success".equals(diagnostic.outcome)&&preservesPrevious(file)){
                System.out.println("[KubeJS Editor] 保留上次异常诊断："+file);
            }else{
                FileTransaction.atomicWrite(file,ProjectStore.JSON.toJson(diagnostic).getBytes(StandardCharsets.UTF_8));
                lastPath=file.toString();
                lastMessage=(diagnostic.outcome==null?"":diagnostic.outcome)+(diagnostic.message==null||diagnostic.message.isBlank()?"":" · "+diagnostic.message);
            }
        }catch(Exception writeError){
            System.err.println("[KubeJS Editor] 无法写入诊断文件："+writeError.getMessage());
            writeError.printStackTrace(System.err);
        }
        if("failure".equals(diagnostic.outcome)){
            System.err.println("[KubeJS Editor] "+diagnostic.operation+" "+diagnostic.phase+" 失败："+diagnostic.message);
            if(diagnostic.throwable!=null)diagnostic.throwable.printStackTrace(System.err);
        }else if("compensated-success".equals(diagnostic.outcome)){
            System.err.println("[KubeJS Editor] "+diagnostic.operation+" "+diagnostic.phase+" 已补偿成功："+diagnostic.message
                    +"（第三方异常："+(diagnostic.error==null?"":diagnostic.error.type)+": "+(diagnostic.error==null?"":diagnostic.error.message)+"）");
            if(diagnostic.throwable!=null)diagnostic.throwable.printStackTrace(System.err);
        }else{
            System.out.println("[KubeJS Editor] "+diagnostic.operation+" "+diagnostic.phase+"："+diagnostic.message);
        }
    }
    private static boolean preservesPrevious(Path file){
        try{
            if(!Files.exists(file))return false;
            var parsed=ProjectStore.JSON.fromJson(Files.readString(file,StandardCharsets.UTF_8),Diagnostic.class);
            return parsed!=null&&("failure".equals(parsed.outcome)||"compensated-success".equals(parsed.outcome));
        }catch(Exception ignored){return false;}
    }

    public static String read(Path root){
        try{
            Path file=FileTransaction.checkedPath(root,FILE);
            if(!Files.exists(file))return "尚无诊断记录。\n\n诊断会在重载失败、配置校正失败或补偿同步后写入：\n"+FILE;
            String text=Files.readString(file,StandardCharsets.UTF_8);
            return text.isBlank()?"诊断文件为空："+FILE:text;
        }catch(Exception error){
            return "无法读取诊断："+error.getMessage();
        }
    }

    public static String lastMessage(){return lastMessage;}
    public static String lastPath(){return lastPath;}
    public static String path(Path root){return root.resolve(FILE).toString();}
    private EditorDiagnostics(){}
}
