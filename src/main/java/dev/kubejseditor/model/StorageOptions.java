package dev.kubejseditor.model;

/** Container geometry and logical per-slot capacity, independent of ItemStack limits. */
public final class StorageOptions {
    public static StorageOptions read(com.google.gson.JsonElement json){
        if(!json.isJsonObject())throw new com.google.gson.JsonParseException("存储设置必须为对象");
        var result=new StorageOptions();var o=json.getAsJsonObject();
        try{if(o.has("rows"))result.rows=integer(o.get("rows"));if(o.has("columns"))result.columns=integer(o.get("columns"));if(o.has("slotCapacity"))result.slotCapacity=integer(o.get("slotCapacity"));result.validate();}
        catch(RuntimeException error){throw new com.google.gson.JsonParseException("存储设置无效："+error.getMessage(),error);}return result;
    }
    private static int integer(com.google.gson.JsonElement v){if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("请输入整数");return v.getAsBigDecimal().intValueExact();}
    public static final int MAX_ROWS=9,MAX_COLUMNS=12,MAX_CAPACITY=4096;
    public int rows=3,columns=9,slotCapacity=64;
    public void validate(){
        if(rows<1||rows>MAX_ROWS||columns<1||columns>MAX_COLUMNS||slotCapacity<1||slotCapacity>MAX_CAPACITY)
            throw new IllegalArgumentException("存储设置须为整数：行数 1～9、列数 1～12、单格容量 1～4096");
    }
}
