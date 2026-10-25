package dev.kubejseditor.recipe.compat;

import com.google.gson.*;
import java.util.*;

/** Category-local rectangles, never screen pixels. Different shapes have separate mappings. */
public final class LayoutMappings {
    public static final class Profile {public String category,modVersion,shape;public int width,height;public List<Region> regions=new ArrayList<>();}
    public static final class Region {public String id,binding;public int x,y,width,height;public Region(){}public Region(String binding,int x,int y,int width,int height){this.binding=binding;this.x=x;this.y=y;this.width=width;this.height=height;}public String regionId(){return id==null||id.isBlank()?"legacy:"+binding+":"+x+","+y+","+width+","+height:id;}}
    public static void check(Profile profile){if(profile.category==null||profile.shape==null||profile.width<1||profile.height<1||profile.width>8192||profile.height>8192||profile.regions==null)throw new IllegalArgumentException("布局映射无效");Set<String> keys=new HashSet<>();for(var r:profile.regions){if(r.binding==null||!keys.add(r.regionId())||r.width<1||r.height<1||r.x<0||r.y<0||r.x+r.width>profile.width||r.y+r.height>profile.height)throw new IllegalArgumentException("标记区域越界、重复或为空");for(var other:profile.regions)if(other!=r&&r.x<other.x+other.width&&r.x+r.width>other.x&&r.y<other.y+other.height&&r.y+r.height>other.y)throw new IllegalArgumentException("标记区域互相重叠");}}
    public static boolean matches(Profile p,String category,String version,String shape,int width,int height){return Objects.equals(p.category,category)&&Objects.equals(p.modVersion,version)&&Objects.equals(p.shape,shape)&&p.width==width&&p.height==height;}
    private LayoutMappings(){}
}
