package dev.kubejseditor.runtime;

import java.util.*;
import java.util.function.Supplier;

/** Values belong to one owner and reload generation; UI invalidation need not redo source IO. */
public final class GenerationCache<K,V> {
    private Object owner;
    private long generation=Long.MIN_VALUE;
    private final Map<K,V> values=new HashMap<>();
    public V get(Object current,long epoch,K key,Supplier<V> compute){
        if(owner!=current||generation!=epoch){values.clear();owner=current;generation=epoch;}
        if(!values.containsKey(key))values.put(key,compute.get());
        return values.get(key);
    }
    public void clear(){values.clear();owner=null;generation=Long.MIN_VALUE;}
}
