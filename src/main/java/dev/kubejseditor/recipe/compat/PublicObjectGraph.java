package dev.kubejseditor.recipe.compat;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;

/** Bounded traversal of public wrappers, with no mod class names and no private-field mutation. */
public final class PublicObjectGraph {
    private record Node(Object value,int depth){}
    private static final Set<String> ACCESSORS=Set.of("getRecipe","recipe","getOriginalRecipe","getOriginal","getDelegate","delegate","unwrap","getValue","value","getEntry");
    public static List<Object> scan(Object root,Predicate<Class<?>> nativeType){
        var queue=new ArrayDeque<Node>();if(root!=null)queue.add(new Node(root,0));var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());var result=new ArrayList<Object>();int inspected=0;
        while(!queue.isEmpty()&&inspected++<128&&result.size()<64){var node=queue.removeFirst();Object value=node.value;if(value==null||!seen.add(value))continue;result.add(value);if(node.depth>=5)continue;int next=node.depth+1;
            if(value instanceof Optional<?> optional){optional.ifPresent(v->queue.add(new Node(v,next)));continue;}if(value instanceof Map<?,?> map){int n=0;for(var v:map.values()){if(n++>=32)break;if(v!=null)queue.add(new Node(v,next));}continue;}if(value instanceof Collection<?> collection){int n=0;for(var v:collection){if(n++>=32)break;if(v!=null)queue.add(new Node(v,next));}continue;}
            Class<?> type=value.getClass();if(type.isArray()){for(int i=0;i<Math.min(32,Array.getLength(value));i++)queue.add(new Node(Array.get(value,i),next));continue;}
            if(type.getName().startsWith("java.")||type.isEnum()||type.isPrimitive())continue;
            Set<String> records=type.isRecord()?Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(java.util.stream.Collectors.toSet()):Set.of();
            for(var method:type.getMethods())if(!Modifier.isStatic(method.getModifiers())&&method.getParameterCount()==0&&method.getReturnType()!=void.class&&(records.contains(method.getName())||ACCESSORS.contains(method.getName())||nativeType.test(method.getReturnType())))try{var child=method.invoke(value);if(child!=null)queue.add(new Node(child,next));}catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
            for(var field:type.getFields())if(!Modifier.isStatic(field.getModifiers())&&(nativeType.test(field.getType())||type.isRecord()))try{var child=field.get(value);if(child!=null)queue.add(new Node(child,next));}catch(ReflectiveOperationException|RuntimeException|LinkageError ignored){}
        }
        return List.copyOf(result);
    }
    private PublicObjectGraph(){}
}
