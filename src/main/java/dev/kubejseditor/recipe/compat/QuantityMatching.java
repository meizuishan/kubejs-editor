package dev.kubejseditor.recipe.compat;

import java.util.*;

/** Repeated identities cannot use displayed count-one icons to choose between different native quantities. */
public final class QuantityMatching {
    public static boolean ambiguous(List<String> nativeValues,List<String> displayedValues,boolean equivalent){return nativeValues.size()>1&&!equivalent&&!nativeValues.stream().sorted().toList().equals(displayedValues.stream().sorted().toList());}
    private QuantityMatching(){}
}
