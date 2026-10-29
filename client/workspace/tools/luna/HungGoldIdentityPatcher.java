import java.io.*;
import java.util.*;

/** Scoped repair of the two invalid positive-ID guards in the released gold bridge. */
public final class HungGoldIdentityPatcher {
    static final String INPUT="855A7F66891DFBC0F6A64ABC00A148BB2BBE90D57EF9C416D1C3D85EB1F13A58";
    static void check(boolean value,String text){if(!value)throw new IllegalStateException(text);}
    public static void main(String[] args)throws Exception{
        check(args.length==6,"input support micro work output catalogPrefix");
        File input=new File(args[0]),output=new File(args[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned economy release");
        check(!output.exists(),"Fresh candidate");
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-economy-info-20261029-b/auto-pk.properties");
        try{p.load(cfg);}finally{cfg.close();}
        check(INPUT.equals(p.getProperty("output.sha256")),"Pinned economy symbols");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        HungGoldTrackingPatch.fixSignedIds(out,p);
        String changed=p.getProperty("gold.bridge.owner")+".class";int differences=0,classes=0;
        for(String entry:out.keySet()){
            if(entry.endsWith(".class"))classes++;
            if(!Arrays.equals(before.get(entry),out.get(entry))){
                check(entry.equals(changed),"Only the generated gold bridge may change");differences++;
            }
        }
        check(differences==1&&classes==287&&out.size()==before.size(),"One changed class; no additions/resources");
        HungEconomyInfoPatcher.writeJar(out,output);
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("auto.pk.changed.classes",changed);
        OutputStream config=new FileOutputStream(args[5]+".properties");
        try{p.store(config,"Gold identity accepts the native signed-short character ID");}finally{config.close();}
        PrintWriter report=new PrintWriter(args[5]+"-report.txt","UTF-8");
        try{report.println("PASS two invalid ID sign guards removed; one bridge class changed; resources/manifest unchanged");report.println("CHANGED="+changed);report.println("CLASSES="+classes);report.println("SHA256="+p.getProperty("output.sha256"));}finally{report.close();}
        System.out.println("PASS signed gold identity "+p.getProperty("output.sha256"));
    }
}
