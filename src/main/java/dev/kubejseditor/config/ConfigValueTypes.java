package dev.kubejseditor.config;

import java.util.*;

/** A custom numeric validator may declare Object.class; its default still defines the value type. */
public final class ConfigValueTypes {
    public static Class<?> resolve(Class<?> range,Class<?> declared,Object defaults,Object current){
        Class<?> type=range!=null?range:declared;
        if(type==null||type==Object.class||type==Number.class)type=defaults!=null?defaults.getClass():current!=null?current.getClass():Object.class;
        if(type==int.class)return Integer.class;if(type==long.class)return Long.class;if(type==double.class)return Double.class;if(type==float.class)return Float.class;if(type==boolean.class)return Boolean.class;
        return type;
    }
    public static boolean supported(Class<?> t){return t==Integer.class||t==Long.class||t==Double.class||t==Float.class||t==Boolean.class||t==String.class||t.isEnum()||List.class.isAssignableFrom(t);}
    public static Object scalar(Class<?> type,String text){
        if(type==Integer.class)return Integer.valueOf(text);
        if(type==Long.class)return Long.valueOf(text);
        if(type==Double.class){double n=Double.parseDouble(text);if(!Double.isFinite(n))throw new IllegalArgumentException("配置数值必须为有限数");return n;}
        if(type==Float.class){float n=Float.parseFloat(text);if(!Float.isFinite(n))throw new IllegalArgumentException("配置数值必须为有限数");return n;}
        if(type==Boolean.class){if(!Set.of("true","false").contains(text))throw new IllegalArgumentException("请输入 true 或 false");return Boolean.valueOf(text);}
        if(type==String.class)return text;
        if(type.isEnum())return Arrays.stream(type.getEnumConstants()).filter(e->((Enum<?>)e).name().equals(text)).findFirst().orElseThrow(()->new IllegalArgumentException("无效枚举值"));
        throw new IllegalArgumentException("暂不支持此配置类型："+type.getSimpleName());
    }
    private ConfigValueTypes(){}
}
