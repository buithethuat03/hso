import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.microedition.lcdui.Image;
import org.microemu.MIDletBridge;

/** Exercises the real native menus, command callbacks, saved settings and menu paint. */
public final class HungEquipmentFiltersUiTest {
    static int checks;
    static ClassLoader loader;
    static String n(String tail){StringBuilder b=new StringBuilder();while(b.length()+tail.length()<100)b.append('i');return b.append(tail).toString();}
    static Class<?> cls(String name)throws Exception{return Class.forName(name.startsWith("~")?n(name.substring(1)):name,true,loader);}
    static Field field(Class<?> c,String name)throws Exception{
        String actual=name.startsWith("~")?n(name.substring(1)):name;
        for(Class<?> k=c;k!=null;k=k.getSuperclass())try{Field f=k.getDeclaredField(actual);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(actual);
    }
    static Object get(String owner,String name)throws Exception{return field(cls(owner),name).get(null);}
    static void set(String owner,String name,Object value)throws Exception{field(cls(owner),name).set(null,value);}
    static void check(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);System.out.println("PASS "+why);}
    static Object helper(String name,Class<?>[] types,Object...a)throws Exception{
        Method m=HungBySonExpInfoBinaryTest.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,a);
    }
    static Object menu()throws Exception{return get("~liilllii","~liiiliillll");}
    static Object rows()throws Exception{return field(menu().getClass(),"~lllliliiii").get(menu());}
    static int size(Object rows)throws Exception{return ((Integer)rows.getClass().getMethod("c").invoke(rows)).intValue();}
    static Object at(Object rows,int i)throws Exception{return rows.getClass().getMethod("a",Integer.TYPE).invoke(rows,i);}
    static Object row(int id)throws Exception{
        Object rows=rows();for(int i=0;i<size(rows);i++){Object r=at(rows,i);if(field(r.getClass(),"e").getByte(r)==id)return r;}
        throw new AssertionError("Missing native row "+id);
    }
    static String text(Object row)throws Exception{return (String)field(row.getClass(),"~lliiilli").get(row);}
    static void click(int id)throws Exception{row(id).getClass().getMethod("a").invoke(row(id));}
    static Object settings(String name,Class<?>[] types,Object...a)throws Exception{return cls("luna.features.EquipmentFilterSettings").getMethod(name,types).invoke(null,a);}
    static int mask(boolean sale)throws Exception{return ((Integer)settings("mask",new Class<?>[]{Boolean.TYPE},sale)).intValue();}
    static void paint(File dir,String name)throws Exception{
        Class<?> graphics=cls("~llilll");Image image=Image.createImage(240,320);javax.microedition.lcdui.Graphics g=image.getGraphics();g.setColor(0x354550);g.fillRect(0,0,240,320);
        Object nativeGraphics=helper("bindNativeGraphics",new Class<?>[]{Class.class,javax.microedition.lcdui.Graphics.class},graphics,g);
        menu().getClass().getMethod(n("lilil"),graphics).invoke(menu(),nativeGraphics);
        helper("writePng",new Class<?>[]{Image.class,File.class},image,new File(dir,name));
    }
    public static void main(String[] a){
        try{run(a);System.out.println("EQUIPMENT_FILTERS_UI_PASS checks="+checks);System.exit(0);}catch(Throwable t){t.printStackTrace();System.exit(1);}
    }
    static void run(String[] a)throws Exception{
        check(a.length==2,"candidate.jar screenshotsDir");File screens=new File(a[1]);screens.mkdirs();helper("startEmulator",new Class<?>[]{File.class},new File(a[0]));
        loader=MIDletBridge.getCurrentMIDlet().getClass().getClassLoader();check(loader!=null,"Real candidate MIDlet is running");
        set("~liilllii","~lllillll",240);set("~liilllii","~liiilliilii",120);set("~liilllii","~liiiiiil",320);
        check(mask(false)==15&&mask(true)==15,"All stat groups initially selected independently");
        check(!((Boolean)settings("lockedOnly",new Class<?>[0])).booleanValue(),"Locked-only starts off");
        cls("~lliiilli").getMethod(n("lilliiil")).invoke(null);
        check(size(rows())==5,"Pet parent has status, equipment quality, candy, stat groups, back");
        check(text(row(-118)).equals("Ăn trang bị thuộc loại >"),"Pet label is exact");paint(screens,"pet-parent.png");
        click(-118);check(size(rows())==5,"Pet stat submenu has four choices and back");
        check(text(row(-119)).equals("Sức mạnh [x]"),"Strength is checked");paint(screens,"pet-groups.png");
        click(-119);check(mask(false)==14,"Native command toggles pet strength");check(mask(true)==15,"Pet setting does not alter sale mask");
        check(text(row(-119)).equals("Sức mạnh [ ]"),"Pet checkbox redraws");click(-70);check(size(rows())==5,"Pet back returns to parent");
        cls("~lliiilll").getMethod(n("lilliiil")).invoke(null);
        check(size(rows())==5,"Sale parent has status, quality, locked-only, stat groups, back");
        check(text(row(-123)).equals("Chỉ bán đồ khóa [ ]"),"Locked-only unchecked");click(-123);
        check(((Boolean)settings("lockedOnly",new Class<?>[0])).booleanValue(),"Native locked-only command toggles");
        check(text(row(-123)).equals("Chỉ bán đồ khóa [x]"),"Locked-only check redraws");paint(screens,"sale-parent.png");
        click(-124);check(size(rows())==5,"Sale stat submenu opens");click(-126);
        check(mask(true)==13,"Sale dexterity toggles independently");check(mask(false)==14,"Sale does not alter pet mask");paint(screens,"sale-groups.png");
        click(-74);check(size(rows())==5,"Sale back returns to sale parent");
        Class<?> store=cls("luna.features.EquipmentFilterSettings");
        // Clear only in-memory fields, then read the actual MIDP record written by native UI clicks.
        field(store,"petMask").setInt(null,15);field(store,"saleMask").setInt(null,15);field(store,"lockedOnly").setBoolean(null,false);field(store,"loaded").setBoolean(null,false);
        check(mask(false)==14&&mask(true)==13&&((Boolean)settings("lockedOnly",new Class<?>[0])).booleanValue(),"MIDP RMS reload preserves both masks and locked-only");
        byte[] record=null;javax.microedition.rms.RecordStore rs=javax.microedition.rms.RecordStore.openRecordStore("hbs_equipment_filters_v1",false);
        try{javax.microedition.rms.RecordEnumeration e=rs.enumerateRecords(null,null,false);try{record=rs.getRecord(e.nextRecordId());}finally{e.destroy();}}finally{rs.closeRecordStore();}
        check(Arrays.equals(record,new byte[]{1,14,13,1}),"Saved RMS schema is exact");
        for(int i=0;i<4;i++)if((mask(true)&(1<<i))!=0)settings("toggleGroup",new Class<?>[]{Boolean.TYPE,Integer.TYPE},true,1<<i);
        check(mask(true)==0,"User can deselect every sale group");
    }
}
