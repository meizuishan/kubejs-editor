package dev.kubejseditor.recipe;

/** Shared coordinate transform for painting, tooltips, clicks and JEI drag targets. */
public record CanvasFit(int x,int y,double scale) {
    public static CanvasFit fit(int x,int y,int availableWidth,int availableHeight,int contentWidth,int contentHeight){return new CanvasFit(x,y,Math.min(1,Math.min(Math.max(1,availableWidth)/(double)Math.max(1,contentWidth),Math.max(1,availableHeight)/(double)Math.max(1,contentHeight))));}
    public static CanvasFit centered(int x,int y,int width,int height,int contentWidth,int contentHeight,double maximum){
        double scale=Math.min(maximum,Math.min(Math.max(1,width)/(double)Math.max(1,contentWidth),Math.max(1,height)/(double)Math.max(1,contentHeight)));
        return new CanvasFit(x+(int)((width-contentWidth*scale)/2),y+(int)((height-contentHeight*scale)/2),scale);
    }
    public int px(int local){return x+(int)Math.floor(local*scale);}
    public int py(int local){return y+(int)Math.floor(local*scale);}
    public int size(int n){return Math.max(1,(int)Math.ceil(n*scale));}
    public double localX(double screen){return (screen-x)/scale;}
    public double localY(double screen){return (screen-y)/scale;}
}
