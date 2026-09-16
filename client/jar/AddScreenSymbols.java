import org.objectweb.asm.*; import org.objectweb.asm.tree.*;
import java.io.*; import java.util.*; import java.util.jar.*;

/**
 * Them cac ky hieu MAN HINH va co Auto Danh, de HSOAutoTradeSupport thoi phu thuoc
 * HSOSKYPetFeedSupport (lop do khong co trong ban Luna sach).
 *
 * Neo: chinh HSOSKYPetFeedSupport trong ban mod cu - getCurrentScreen()/getGameScreen()/
 * getLoginScreen()/setModAutoAttack() moi ham chi doc-ghi DUNG MOT field, nen lay thang
 * field do ra. Sau do doi chieu: ten do phai ton tai trong ban Luna sach (cung bo lam roi
 * ten), neu khong thi dung lai chu khong doan.
 *
 * javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AddScreenSymbols.java
 * java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AddScreenSymbols [jarMod] [jarSach]
 */
public final class AddScreenSymbols {
  static Map<String,ClassNode> load(String jn) throws IOException {
    Map<String,ClassNode> cs=new LinkedHashMap<String,ClassNode>();
    JarFile jar=new JarFile(jn);
    for(Enumeration<JarEntry> it=jar.entries(); it.hasMoreElements();){
      JarEntry e=it.nextElement(); if(!e.getName().endsWith(".class")) continue;
      ClassNode cn=new ClassNode(); InputStream in=jar.getInputStream(e); new ClassReader(in).accept(cn,0); in.close();
      cs.put(cn.name, cn);
    }
    jar.close(); return cs;
  }
  /** Field duy nhat ma method do dung toi (GETSTATIC hoac PUTSTATIC). */
  static FieldInsnNode onlyField(ClassNode cn, String method) {
    for(MethodNode m:cn.methods){ if(!m.name.equals(method)) continue;
      FieldInsnNode hit=null; int n=0;
      for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext()){
        if(i.getOpcode()==Opcodes.GETSTATIC||i.getOpcode()==Opcodes.PUTSTATIC){ hit=(FieldInsnNode)i; n++; } }
      if(n!=1) throw new RuntimeException(method+": mong doi dung 1 field tinh, thay "+n);
      return hit; }
    throw new RuntimeException("khong thay method "+method);
  }
  public static void main(String[] a) throws Exception {
    String jarMod = a.length>0? a[0] : "HSOLUNA_TRUONGBK_PERFECT.jar";
    String jarSach= a.length>1? a[1] : "LUNA_CLEAN.jar";
    Map<String,ClassNode> mod=load(jarMod), sach=load(jarSach);
    ClassNode pet=mod.get("HSOSKYPetFeedSupport");
    if(pet==null) throw new RuntimeException("khong thay HSOSKYPetFeedSupport trong "+jarMod);

    LinkedHashMap<String,String[]> out=new LinkedHashMap<String,String[]>();  // key -> {owner, name, desc}
    String[][] want={
      {"getCurrentScreen","app.currentScreen"},
      {"getGameScreen",   "app.gameScreen"},
      {"getLoginScreen",  "app.loginScreen"},
      {"setModAutoAttack","engine.autoAttackFlag"},
    };
    for(String[] w:want){ FieldInsnNode f=onlyField(pet,w[0]);
      out.put(w[1], new String[]{f.owner,f.name,f.desc}); }

    // Lop chua ba man hinh phai la MOT lop duy nhat -> dat ten class.APP
    String app=out.get("app.currentScreen")[0];
    if(!app.equals(out.get("app.gameScreen")[0])||!app.equals(out.get("app.loginScreen")[0]))
      throw new RuntimeException("ba man hinh khong cung mot lop");

    // Doi chieu sang ban sach
    StringBuilder bad=new StringBuilder();
    ClassNode appSach=sach.get(app);
    if(appSach==null) bad.append(" class.APP");
    for(Map.Entry<String,String[]> e:out.entrySet()){
      String[] v=e.getValue(); ClassNode owner=sach.get(v[0]); boolean ok=false;
      if(owner!=null) for(FieldNode f:owner.fields) if(f.name.equals(v[1])&&f.desc.equals(v[2])) ok=true;
      if(!ok) bad.append(" "+e.getKey());
    }
    if(bad.length()>0) throw new RuntimeException("ban sach thieu:"+bad);

    // Ghi vao properties
    File p=new File("autotrade-symbols.properties");
    StringBuilder sb=new StringBuilder();
    BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(p),"UTF-8"));
    String line; Set<String> have=new HashSet<String>();
    while((line=r.readLine())!=null){ sb.append(line).append('\n');
      int i=line.indexOf('='); if(i>0) have.add(line.substring(0,i)); }
    r.close();
    int added=0;
    if(!have.contains("class.APP")){ sb.append("class.APP=").append(app).append('\n'); added++; }
    for(Map.Entry<String,String[]> e:out.entrySet()){
      if(have.contains(e.getKey())) continue;
      sb.append(e.getKey()).append('=').append(e.getValue()[1]).append('\n'); added++; }
    Writer w=new OutputStreamWriter(new FileOutputStream(p),"UTF-8"); w.write(sb.toString()); w.close();
    System.out.println("class.APP           = "+app.length()+" ky tu");
    for(Map.Entry<String,String[]> e:out.entrySet())
      System.out.println(String.format("%-22s= %s (%s)", e.getKey(), e.getValue()[1].length()+" ky tu", e.getValue()[2]));
    System.out.println("Da them "+added+" ky hieu; ca "+out.size()+" deu co trong "+jarSach);
  }
}
