package dev.kubejseditor.recipe.compat;

/** Field/slot evidence decides preference; identifiers and mod names never affect the score. */
public final class AdaptationQuality {
    public static int score(int matched,int total,int writable,int unresolved,int precise,int issues,int priority){return (total==0?0:matched*10000/total)+Math.min(240,writable)*5+Math.min(30,precise)*10-unresolved*50-issues*300+priority;}
    private AdaptationQuality(){}
}
