import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.tree.*;

/** Five-minute rolling rate and a HUD numerator matching the interpolated percentage. */
public final class HungExpRollingPatcher {
    static final String INPUT="9C45CB4C061E1A358FFA2A4C5A8BD90E055A075B70BE791F2C97190600CAEDE2";
    static final String OWNER="luna/features/ExpRateSupport";
    static final String HUD="luna/features/ExpEstimateSupport";
    static void check(boolean b,String message){if(!b)throw new IllegalStateException(message);}
    public static void main(String[] a)throws Exception{
        check(a.length==6,"input support micro work output catalogPrefix");
        File input=new File(a[0]),output=new File(a[4]);
        check(INPUT.equals(HungBySonExpInfoPack.hash(input)),"Pinned current selection-objects release");check(!output.exists(),"Fresh output");
        Map<String,byte[]> before=HungBySonExpInfoPack.read(input),out=new LinkedHashMap<String,byte[]>(before);
        ClassNode old=HungBySonExpInfoPack.node(before.get(OWNER+".class"));
        ClassNode fresh=HungBySonExpInfoPack.node(Files.readAllBytes(new File(a[1],OWNER+".class").toPath()));
        Set<String> addedFields=new HashSet<String>(Arrays.asList("RATE_BUCKETS","RATE_BUCKET_MS","bucketGain","hasBucketClock","lastBucketSecond","currentBucket"));
        check(fresh.fields.size()==old.fields.size()+addedFields.size(),"Only rolling-window fields added");
        for(FieldNode f:fresh.fields){
            FieldNode previous=null;for(FieldNode x:old.fields)if(x.name.equals(f.name))previous=x;
            if(previous!=null)check(previous.desc.equals(f.desc)&&previous.access==f.access&&Objects.equals(previous.value,f.value),"Preserved field "+f.name);
            else check(addedFields.remove(f.name),"Expected private rolling field "+f.name);
        }
        check(addedFields.isEmpty(),"All rolling fields present");
        Set<String> changed=new HashSet<String>(Arrays.asList("gain","rate","observeClock","clearAt","clearState"));
        int same=0;
        for(MethodNode m:old.methods){
            MethodNode next=HungBySonCopySpecialPatcher.method(fresh,m.name,m.desc);
            check(m.access==next.access,"Preserved method ABI "+m.name);
            boolean equal=HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(next));
            if(changed.contains(m.name))check(!equal,"Rolling method changes "+m.name);
            else{check(equal,"Unchanged formatting/ETA arithmetic "+m.name);same++;}
        }
        check(fresh.methods.size()==old.methods.size()+2,"Only advanceWindow and array initializer added");
        check(HungBySonCopySpecialPatcher.method(fresh,"advanceWindow","(J)V")!=null&&HungBySonCopySpecialPatcher.method(fresh,"<clinit>","()V")!=null,"Rolling helpers exist");
        fresh.version=old.version;out.put(OWNER+".class",HungBySonCopySpecialPatcher.write(fresh));
        HungBySonExpInfoPack.screen(OWNER,out.get(OWNER+".class"));
        ClassNode hud=HungBySonExpInfoPack.node(before.get(HUD+".class"));
        ClassNode hudNew=HungBySonExpInfoPack.node(Files.readAllBytes(new File(a[1],HUD+".class").toPath()));
        check(hudNew.fields.size()==hud.fields.size()+1&&hudNew.methods.size()==hud.methods.size()+1,"HUD adds only fine-line cache and multiply helper");
        FieldNode fineCache=null;
        for(FieldNode f:hudNew.fields){
            FieldNode previous=null;for(FieldNode x:hud.fields)if(x.name.equals(f.name))previous=x;
            if(previous==null){check(f.name.equals("cachedLineFine")&&f.desc.equals("I"),"Expected HUD fine cache");fineCache=f;}
            else check(previous.desc.equals(f.desc)&&previous.access==f.access&&Objects.equals(previous.value,f.value),"Preserved HUD field "+f.name);
        }
        check(fineCache!=null,"Fine cache present");
        MethodNode init=HungBySonCopySpecialPatcher.method(hudNew,"<clinit>","()V");
        MethodNode inverse=new MethodNode(init.access,init.name,init.desc,init.signature,null);init.accept(inverse);
        int cacheInit=0;
        for(AbstractInsnNode x=inverse.instructions.getFirst();x!=null;){
            AbstractInsnNode next=x.getNext();
            if(x instanceof FieldInsnNode&&x.getOpcode()==org.objectweb.asm.Opcodes.PUTSTATIC&&((FieldInsnNode)x).name.equals("cachedLineFine")){
                AbstractInsnNode value=x.getPrevious();while(value!=null&&value.getOpcode()<0)value=value.getPrevious();
                check(value instanceof LdcInsnNode&&Integer.valueOf(Integer.MIN_VALUE).equals(((LdcInsnNode)value).cst),"Fine cache initializer");
                inverse.instructions.remove(value);inverse.instructions.remove(x);cacheInit++;
            }x=next;
        }
        check(cacheInit==1&&HungBySonExpInfoPack.canonical(inverse).equals(HungBySonExpInfoPack.canonical(HungBySonCopySpecialPatcher.method(hud,"<clinit>","()V"))),"All original HUD initialization preserved");
        int hudSame=0;
        for(int i=0;i<hud.methods.size();i++){
            MethodNode m=hud.methods.get(i),next=HungBySonCopySpecialPatcher.method(hudNew,m.name,m.desc);
            check(m.access==next.access,"Preserved HUD method ABI "+m.name);
            boolean equal=HungBySonExpInfoPack.canonical(m).equals(HungBySonExpInfoPack.canonical(next));
            if(m.name.equals("line")||m.name.equals("invalidateLine")||m.name.equals("<clinit>")){check(!equal,"Fine HUD change "+m.name);hud.methods.set(i,next);}
            else{check(equal,"Unchanged estimator method "+m.name);hudSame++;}
        }
        hud.fields.add(fineCache);hud.methods.add(HungBySonCopySpecialPatcher.method(hudNew,"multiplyFineProgress","(JI)J"));
        out.put(HUD+".class",HungBySonCopySpecialPatcher.write(hud));HungBySonExpInfoPack.screen(HUD,out.get(HUD+".class"));
        int classes=0,edits=0;for(String name:out.keySet()){
            if(name.endsWith(".class"))classes++;
            if(!Arrays.equals(before.get(name),out.get(name))){check(name.equals(OWNER+".class")||name.equals(HUD+".class"),"Only EXP rate and fine HUD change");edits++;}
        }
        check(edits==2,"Two intended class edits");
        JarOutputStream jar=new JarOutputStream(new FileOutputStream(output));
        try{for(Map.Entry<String,byte[]> e:out.entrySet()){JarEntry z=new JarEntry(e.getKey());z.setTime(0);jar.putNextEntry(z);jar.write(e.getValue());jar.closeEntry();}}finally{jar.close();}
        Properties p=new Properties();InputStream cfg=new FileInputStream("build/hung-selection-objects-20261028-b/auto-pk.properties");try{p.load(cfg);}finally{cfg.close();}
        check(INPUT.equals(p.getProperty("output.sha256")),"Pinned source catalog");
        p.setProperty("input.sha256",INPUT);p.setProperty("output.sha256",HungBySonExpInfoPack.hash(output));
        p.setProperty("exp.rate.window.seconds","300");p.setProperty("exp.rate.bucket.ms","1000");
        p.setProperty("exp.hud.fine.numerator","true");
        p.setProperty("auto.pk.class.count",String.valueOf(classes));p.setProperty("auto.pk.changed.classes",OWNER+".class,"+HUD+".class");
        OutputStream cat=new FileOutputStream(a[5]+".properties");try{p.store(cat,"Five-minute rolling EXP rate");}finally{cat.close();}
        PrintWriter log=new PrintWriter(a[5]+"-report.txt","UTF-8");try{
            log.println("PASS rolling rate plus fine HUD numerator; "+same+" rate methods and "+hudSame+" estimator methods unchanged; inverse HUD initialization passed; all other classes, resources and manifest unchanged");
            log.println("CHANGED="+p.getProperty("auto.pk.changed.classes"));log.println("SHA256="+p.getProperty("output.sha256"));
        }finally{log.close();}
        System.out.println("PASS rolling EXP "+p.getProperty("output.sha256"));
    }
}
