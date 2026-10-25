package dev.kubejseditor.recipe.compat;

import com.google.gson.*;
import java.util.*;

/** Serializable, instance-local authoring semantics. It is never emitted into recipe JSON. */
public final class CompatDefinition {
    public int version=1;
    public String type,source="confirmed",modVersion="",shape="";
    /** Null retains legacy automatic augmentation; false preserves an explicit, possibly empty map. */
    public Boolean autoDiscover;
    public boolean automatic(){return autoDiscover==null||autoDiscover;}
    public List<Field> fields=new ArrayList<>();
    public enum Role { INPUT, OUTPUT, CENTER, CATALYST, RESOURCE, CONDITION, PARAMETER, RESULT;
        public String label(){return switch(this){case INPUT->"消耗输入";case OUTPUT->"产物";case CENTER->"中心原料";case CATALYST->"催化剂 / 增强器要求";case RESOURCE->"资源需求";case CONDITION->"加工条件";case PARAMETER->"参数";case RESULT->"结果行为";};}
    }
    public enum Kind { ITEM, FLUID, OTHER, NUMBER, BOOLEAN, STRING, JSON;
        public String label(){return switch(this){case ITEM->"物品 / 原料";case FLUID->"流体";case OTHER->"其他 JEI 原料";case NUMBER->"数值";case BOOLEAN->"开关";case STRING->"文字 / 注册 ID";case JSON->"原始结构（保留特殊原料和条件）";};}
    }
    public static final class Field {
        public String path,label,labelKey="",unit="",quantity="",probability="",expand="",registry="";
        public String valuePath="",quantityPath="",ingredientType="",readOnlyReason="";
        public Role role=Role.PARAMETER;
        public Kind kind=Kind.JSON;
        public boolean list,integer,required;
        /** False only when a verified category intentionally projects every ingredient at count one. */
        public boolean displayQuantity=true;
        public JsonElement fallback;
        public JsonElement template;
        public Field(){}
        public Field(String path,String label,Kind kind,Role role){this.path=path;this.label=label;this.kind=kind;this.role=role;}
        public boolean slot(){return kind==Kind.ITEM||kind==Kind.FLUID||kind==Kind.OTHER;}
    }
    public CompatDefinition(){}
    public CompatDefinition(String type,String source){this.type=type;this.source=source;}
    public Field add(String path,String label,Kind kind,Role role){var f=new Field(path,label,kind,role);fields.add(f);return f;}
    public void check(){if(version!=1||type==null||fields==null||fields.size()>256)throw new IllegalArgumentException("适配定义无效");var paths=new HashSet<String>();for(var f:fields){CompatJson.parts(f.path);if(f.path.equals("/type")||f.path.startsWith("/components")||f.path.contains("..")||f.role==null||f.kind==null||f.label==null||!paths.add(f.path))throw new IllegalArgumentException("适配字段无效或重复："+f.path);}}
}
