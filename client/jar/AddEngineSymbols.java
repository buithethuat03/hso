import org.objectweb.asm.*; import org.objectweb.asm.tree.*;
import java.io.*; import java.util.*; import java.util.jar.*;

/**
 * Do lai hai ky hieu cua ENGINE cho mot ban client khac.
 *
 * Ban Luna sach va ban mod cu KHONG cung mot bang lam roi ten: 107/112 ky hieu trung, con
 * lai 5 cai tren ENGINE thi lech. Doan nay do lai theo CAU TRUC, khong theo ten:
 *
 *   engine.field.INSTANCE  - field tinh DUY NHAT cua ENGINE co kieu chinh ENGINE (singleton).
 *   engine.field.autoHorse - field tinh boolean cua ENGINE duoc doc trong method co chuoi
 *                            nhan "Auto ... Ng?a: ON" (dong menu bat/tat Auto Len Ngua).
 *
 * javac -encoding UTF-8 -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AddEngineSymbols.java
 * java  -cp ".;asm-9.7.jar;asm-tree-9.7.jar" AddEngineSymbols [jar]
 */
public final class AddEngineSymbols {
  public static void main(String[] a) throws Exception {
    String jn = a.length>0? a[0] : "LUNA_CLEAN.jar";
    Properties sym=new Properties(); InputStream ps=new FileInputStream("autotrade-symbols.properties"); sym.load(ps); ps.close();
    String eng=sym.getProperty("class.ENGINE");
    if(eng==null) throw new RuntimeException("thieu class.ENGINE");

    ClassNode en=null;
    JarFile jar=new JarFile(jn);
    for(Enumeration<JarEntry> it=jar.entries(); it.hasMoreElements();){
      JarEntry e=it.nextElement(); if(!e.getName().endsWith(".class")) continue;
      ClassNode cn=new ClassNode(); InputStream in=jar.getInputStream(e); new ClassReader(in).accept(cn,0); in.close();
      if(cn.name.equals(eng)) en=cn; }
    jar.close();
    if(en==null) throw new RuntimeException("khong thay lop ENGINE trong "+jn);

    // ---- INSTANCE: field tinh duy nhat co kieu chinh no
    String self="L"+eng+";"; List<FieldNode> hits=new ArrayList<FieldNode>();
    for(FieldNode f:en.fields) if(f.desc.equals(self) && (f.access&Opcodes.ACC_STATIC)!=0) hits.add(f);
    if(hits.size()!=1) throw new RuntimeException("mong doi 1 singleton, thay "+hits.size());
    String instance=hits.get(0).name;
    boolean instPublic=(hits.get(0).access&Opcodes.ACC_PUBLIC)!=0;

    // ---- autoHorse: boolean tinh doc trong method co chuoi "Ng" + "a: ON"
    Map<String,Integer> votes=new LinkedHashMap<String,Integer>();
    for(MethodNode m:en.methods){
      boolean horseLabel=false;
      for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())
        if(n instanceof LdcInsnNode && ((LdcInsnNode)n).cst instanceof String){
          String s=(String)((LdcInsnNode)n).cst;
          if(s.indexOf("Ngựa: ON")>=0 || s.indexOf("Ngựa: [")>=0) horseLabel=true; }
      if(!horseLabel) continue;
      for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())
        if(n.getOpcode()==Opcodes.GETSTATIC){ FieldInsnNode f=(FieldInsnNode)n;
          if(f.owner.equals(eng)&&f.desc.equals("Z")){
            Integer v=votes.get(f.name); votes.put(f.name, Integer.valueOf(v==null?1:v.intValue()+1)); } }
    }
    if(votes.isEmpty()) throw new RuntimeException("khong thay method nao co nhan Auto Ngua");
    String horse=null; int best=-1;
    for(Map.Entry<String,Integer> e:votes.entrySet()) if(e.getValue().intValue()>best){ best=e.getValue().intValue(); horse=e.getKey(); }
    if(votes.size()>1) System.out.println("  (canh bao: "+votes.size()+" ung vien autoHorse, lay cai duoc doc nhieu nhat)");

    // ---- ghi de vao properties
    File p=new File("autotrade-symbols.properties");
    BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(p),"UTF-8"));
    StringBuilder sb=new StringBuilder(); String line;
    while((line=r.readLine())!=null){
      if(line.startsWith("engine.field.INSTANCE=")) line="engine.field.INSTANCE="+instance;
      else if(line.startsWith("engine.field.autoHorse=")) line="engine.field.autoHorse="+horse;
      sb.append(line).append('\n'); }
    r.close();
    Writer w=new OutputStreamWriter(new FileOutputStream(p),"UTF-8"); w.write(sb.toString()); w.close();
    System.out.println("engine.field.INSTANCE  = "+instance.length()+" ky tu  (public="+instPublic+")");
    System.out.println("engine.field.autoHorse = "+horse.length()+" ky tu  (doc "+best+" lan)");
    if(!instPublic) System.out.println("  -> INSTANCE la private: patcher phai mo quyen truoc khi nhung support class");
  }
}
