import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.util.*;

/** Reads only the bundled language arrays to establish native equipment enums. */
public class ItemCatalogInspect {
    public static void main(String[] a)throws Exception {
        URLClassLoader l=new URLClassLoader(new URL[]{new File(a[0]).toURI().toURL(),new File(a[1]).toURI().toURL()},null);
        String name=HungAutoAttackNativeBuilder.n("lilliil");Class<?> c=Class.forName(name,true,l);c.getConstructor().newInstance();
        for(Field f:c.getDeclaredFields())if(f.getType().equals(String[].class)){f.setAccessible(true);System.out.println(f.getName().replaceAll("i{20,}","~")+" "+Arrays.toString((Object[])f.get(null)));}
        for(String tail:new String[]{"liililll","lill"}){
            c=Class.forName(HungAutoAttackNativeBuilder.n(tail),true,l);
            for(Field f:c.getDeclaredFields())if(Modifier.isStatic(f.getModifiers()) && (f.getType().equals(String[].class)||f.getType().equals(int[].class))){f.setAccessible(true);Object v=f.get(null);System.out.println(tail+"."+f.getName().replaceAll("i{20,}","~")+" "+(v instanceof int[]?Arrays.toString((int[])v):Arrays.toString((Object[])v)));}
        }
        l.close();
    }
}
