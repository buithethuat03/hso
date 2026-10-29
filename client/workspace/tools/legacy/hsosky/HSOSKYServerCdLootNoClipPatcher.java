import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Builds the server-cooldown, instant-auto, remote-loot and adaptive no-clip edition. */
public final class HSOSKYServerCdLootNoClipPatcher implements Opcodes {
    private static final String INPUT = "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE.jar";
    private static final String OUTPUT = "HSOSKY_10SKILL_FASTCAST_AUTOFEED_60S_500MS_MODE345_RANGE120_PVPHELP_REMOTEZONE_SERVERCD_LOOTTP_NOCLIP.jar";
    private static final String RUNTIME = "HSOSKYRuntimeSupport";
    private static final String COMMAND = "HSOSKYPetFeedSupport";
    private static final String RANGE = "HSOSKYRangeSupport";
    private static final Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
    private static final Map<String, Long> times = new LinkedHashMap<String, Long>();

    private static ClassNode player, base, sender, receiver, map, ground, pathfinder;
    private static MethodNode update, autoScan, sendPickup, sendMove, senderGet, mapLoad;
    private static String idField, typeField, xField, yField, hpField, pathField, resetMethod;
    private static FieldInsnNode currentTarget, autoState;
    private static String notifyOwner, notifyName, notifyDesc;
    private static int instantHooks, pickupDistanceHooks, pickupAttemptHooks, dropCreateHooks;
    private static int dropRemoveHooks, killHooks, mapHooks, pathHooks, moveSendHooks, correctionHooks;

    public static void main(String[] args) throws Exception {
        File out = new File(OUTPUT);
        require(!out.exists(), "Refusing to overwrite existing output: " + OUTPUT);
        readJar(new File(INPUT));
        discoverCore();
        patchSenderMovement();
        patchRemotePickup();
        patchDropCreation();
        patchReceiver();
        patchInstantCombat();
        patchCollision();
        addRuntime();
        selfTestRuntime();

        require(instantHooks == 1, "instant combat hook count=" + instantHooks);
        require(pickupDistanceHooks == 1, "remote pickup distance hook count=" + pickupDistanceHooks);
        require(pickupAttemptHooks == 1, "pickup-attempt hook count=" + pickupAttemptHooks);
        require(dropCreateHooks == 1, "drop-create hook count=" + dropCreateHooks);
        require(dropRemoveHooks == 1, "drop-remove hook count=" + dropRemoveHooks);
        require(killHooks >= 1, "no local kill-result hook found");
        require(mapHooks == 2, "expected map-load + map-query hooks, got " + mapHooks);
        require(pathHooks >= 1, "no pathfinder collision hook found");
        require(moveSendHooks == 1, "move-send hook count=" + moveSendHooks);
        require(correctionHooks == 1, "server correction hook count=" + correctionHooks);

        save(player); save(sender); save(receiver); save(map); save(ground); save(pathfinder);
        writeJar(out);
        verify(out);
        System.out.println("[+] Built " + OUTPUT);
        System.out.println("    instant-auto hooks: " + instantHooks);
        System.out.println("    loot hooks: distance=" + pickupDistanceHooks + ", attempt=" + pickupAttemptHooks
                + ", own-kill=" + killHooks + ", remove=" + dropRemoveHooks);
        System.out.println("    no-clip hooks: map=" + mapHooks + ", path=" + pathHooks
                + ", movement=" + moveSendHooks + ", correction=" + correctionHooks);
        System.out.println("    cooldown: unchanged from REMOTEZONE (server metadata + original 2000 ms lock)");
    }

    private static void discoverCore() {
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode c = readClass(e.getValue());
            if (containsString(c, "Player vec7")) player = unique(player, c, "player");
            if (containsString(c, "ReadMessenge vec5")) receiver = unique(receiver, c, "receiver");
            if (containsString(c, "LoadMap vecMapItem")) map = unique(map, c, "map");
        }
        require(player != null && receiver != null && map != null, "Cannot locate core game classes");
        base = readClass(entries.get(player.superName + ".class"));
        require(base != null, "Cannot load actor base");

        for (MethodNode m : player.methods) {
            if (calls(m, COMMAND, "isAutoPlayersEnabled", "()Z")) autoScan = m;
        }
        require(autoScan != null, "Cannot locate PvP auto scan");
        for (MethodNode m : player.methods) {
            if ((m.access & ACC_STATIC) == 0 && "()V".equals(m.desc)
                    && calls(m, player.name, autoScan.name, autoScan.desc)) update = m;
        }
        require(update != null, "Cannot locate player update loop");

        int bestPathUses = -1;
        for (FieldNode f : base.fields) if ("[S".equals(f.desc) && (f.access & ACC_STATIC) == 0) {
            int uses = fieldCount(update, GETFIELD, base.name, f.name, "[S")
                    + fieldCount(update, PUTFIELD, base.name, f.name, "[S");
            if (uses > bestPathUses) { bestPathUses = uses; pathField = f.name; }
        }
        require(pathField != null, "Cannot map movement path field");

        discoverGroundAndFields();
        discoverReset();
        discoverCurrentTarget();
        discoverAutoState();
        discoverSender();
        discoverMapLoad();
        discoverPathfinder();
        discoverNotifier();
        validateServerCooldown();
    }

    private static void discoverGroundAndFields() {
        String ctorDesc = "(IBLjava/lang/String;IISB)V";
        MethodNode ctor = null;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode c = readClass(e.getValue());
            if (!base.name.equals(c.superName)) continue;
            MethodNode candidate = findMethod(c, "<init>", ctorDesc);
            if (candidate != null) { ground = unique(ground, c, "ground item"); ctor = candidate; }
        }
        require(ground != null && ctor != null, "Cannot locate ground item class");
        for (AbstractInsnNode n = ctor.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof FieldInsnNode) || n.getOpcode() != PUTFIELD) continue;
            FieldInsnNode f = (FieldInsnNode)n;
            if (!base.name.equals(f.owner)) continue;
            int v = nearestLoadedLocal(n, 7);
            if ("I".equals(f.desc) && v == 1) idField = f.name;
            if ("B".equals(f.desc) && v == 2) typeField = f.name;
            if ("I".equals(f.desc) && v == 4) xField = f.name;
            if ("I".equals(f.desc) && v == 5) yField = f.name;
        }
        require(idField != null && typeField != null && xField != null && yField != null,
                "Cannot map actor id/type/x/y from ground constructor: " + idField + "/" + typeField + "/" + xField + "/" + yField);
    }

    private static void discoverReset() {
        int bestScore = -1;
        for (MethodNode m : base.methods) {
            if ((m.access & ACC_STATIC) != 0 || !"()V".equals(m.desc)) continue;
            boolean readsX = fieldCount(m, GETFIELD, base.name, xField, "I") > 0;
            boolean readsY = fieldCount(m, GETFIELD, base.name, yField, "I") > 0;
            int zeroWrites = 0;
            for (AbstractInsnNode n=m.instructions.getFirst(); n!=null; n=n.getNext()) {
                if (n instanceof FieldInsnNode && n.getOpcode()==PUTFIELD && base.name.equals(((FieldInsnNode)n).owner)
                        && isInt(previousReal(n), 0)) zeroWrites++;
            }
            if (readsX && readsY && zeroWrites >= 2) {
                if (zeroWrites > bestScore) { bestScore = zeroWrites; resetMethod = m.name; }
            }
        }
        require(resetMethod != null, "Cannot locate actor movement reset method");
    }

    private static void discoverCurrentTarget() {
        for (AbstractInsnNode n=update.instructions.getFirst(); n!=null; n=n.getNext()) {
            if (!(n instanceof FieldInsnNode) || n.getOpcode()!=GETSTATIC) continue;
            FieldInsnNode f=(FieldInsnNode)n;
            if (!("L"+base.name+";").equals(f.desc)) continue;
            AbstractInsnNode a=nextReal(n);
            if (a instanceof FieldInsnNode && a.getOpcode()==GETFIELD && base.name.equals(((FieldInsnNode)a).owner)
                    && typeField.equals(((FieldInsnNode)a).name)) {
                currentTarget = f; break;
            }
        }
        require(currentTarget != null, "Cannot map selected-target static field");
    }

    private static void discoverAutoState() {
        for(AbstractInsnNode n=update.instructions.getFirst();n!=null;n=n.getNext()) {
            if(!(n instanceof FieldInsnNode)||n.getOpcode()!=GETFIELD) continue;
            FieldInsnNode f=(FieldInsnNode)n;
            if(!base.name.equals(f.owner)||!pathField.equals(f.name)||!"[S".equals(f.desc)) continue;
            AbstractInsnNode load=previousReal(n), jump=previousReal(load), one=previousReal(jump), state=previousReal(one);
            if(load instanceof VarInsnNode&&load.getOpcode()==ALOAD&&((VarInsnNode)load).var==0
                    && jump instanceof JumpInsnNode && isInt(one,1)
                    && state instanceof FieldInsnNode && state.getOpcode()==GETSTATIC) {
                autoState=(FieldInsnNode)state; break;
            }
        }
        require(autoState!=null,"Cannot map original auto-combat state");
    }

    private static void discoverSender() {
        for (Map.Entry<String,byte[]> e: entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode c=readClass(e.getValue());
            MethodNode p=null,mv=null;
            for (MethodNode m:c.methods) {
                if ("(SB)V".equals(m.desc) && containsInt(m,20)) p=m;
                if ("(SS)V".equals(m.desc) && containsInt(m,4)) mv=m;
            }
            if (p!=null && mv!=null) { sender=unique(sender,c,"packet sender"); sendPickup=p; sendMove=mv; }
        }
        require(sender!=null,"Cannot locate packet sender");
        for (AbstractInsnNode n=update.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof MethodInsnNode) {
            MethodInsnNode c=(MethodInsnNode)n;
            if(c.getOpcode()==INVOKESTATIC && sender.name.equals(c.owner) && ("()L"+sender.name+";").equals(c.desc)) {
                MethodNode candidate=findMethod(sender,c.name,c.desc);
                if(candidate!=null) senderGet=candidate;
            }
        }
        require(senderGet!=null,"Cannot map sender singleton getter");
    }

    private static void discoverMapLoad() {
        for (MethodNode m:map.methods) if (containsString(m,"LoadMap vecMapItem")) {
            require(mapLoad==null,"Multiple map loaders"); mapLoad=m;
        }
        require(mapLoad!=null,"Cannot locate map loader method");
    }

    private static void discoverPathfinder() {
        for (Map.Entry<String,byte[]> e:entries.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            ClassNode c=readClass(e.getValue());
            for (MethodNode m:c.methods) {
                if (!"(IIIII)[S".equals(m.desc)) continue;
                int arrays=opcodeCount(m,IALOAD);
                if (arrays>0 && containsInt(m,-1) && containsInt(m,1)) pathfinder=unique(pathfinder,c,"pathfinder");
            }
        }
        require(pathfinder!=null,"Cannot locate pathfinder");
    }

    private static void discoverNotifier() {
        ClassNode support=readClass(entries.get(COMMAND+".class"));
        MethodNode chat=findMethod(support,"handleChat","(Ljava/lang/String;)Z");
        require(chat!=null,"Cannot inspect help notification support");
        for(AbstractInsnNode n=chat.instructions.getFirst();n!=null;n=n.getNext()) {
            if(!(n instanceof LdcInsnNode)||!(((LdcInsnNode)n).cst instanceof String)
                    ||((String)((LdcInsnNode)n).cst).indexOf("LENH MOD")<0) continue;
            for(AbstractInsnNode c=nextReal(n);c!=null;c=nextReal(c)) if(c instanceof MethodInsnNode&&c.getOpcode()==INVOKESTATIC) {
                MethodInsnNode call=(MethodInsnNode)c;
                if("(Ljava/lang/String;)V".equals(call.desc)) {
                    notifyOwner=call.owner;notifyName=call.name;notifyDesc=call.desc;break;
                }
            }
        }
        require(notifyOwner!=null,"Cannot map on-screen notification method");
    }

    private static void validateServerCooldown() {
        require(entries.containsKey(COMMAND+".class") && entries.containsKey(RANGE+".class"), "Input lacks mode/help/range support");
        int twoSecond=0;
        for (MethodNode m:player.methods) if ("(II)V".equals(m.desc)) twoSecond += intCount(m,2000);
        require(twoSecond>=2,"Base does not contain original 2000 ms all-skill lock");
        ClassNode range=readClass(entries.get(RANGE+".class"));
        MethodNode adjust=findMethod(range,"adjustRange","(II)I");
        require(adjust!=null && returnsConstant(adjust,32767),"Base lost whole-map cast range");
    }

    private static void patchInstantCombat() {
        LabelNode skip=new LabelNode();
        InsnList h=new InsnList();
        h.add(new FieldInsnNode(GETSTATIC,autoState.owner,autoState.name,autoState.desc));
        h.add(new InsnNode(ICONST_1));
        h.add(new JumpInsnNode(IF_ICMPNE,skip));
        h.add(new FieldInsnNode(GETSTATIC,currentTarget.owner,currentTarget.name,currentTarget.desc));
        h.add(new JumpInsnNode(IFNULL,skip));
        h.add(new FieldInsnNode(GETSTATIC,currentTarget.owner,currentTarget.name,currentTarget.desc));
        h.add(new FieldInsnNode(GETFIELD,base.name,typeField,"B"));
        h.add(new MethodInsnNode(INVOKESTATIC,COMMAND,"isAutoTargetType","(I)Z",false));
        h.add(new JumpInsnNode(IFEQ,skip));
        h.add(new VarInsnNode(ALOAD,0)); h.add(new FieldInsnNode(GETFIELD,base.name,pathField,"[S"));
        h.add(new JumpInsnNode(IFNULL,skip));
        h.add(new VarInsnNode(ALOAD,0)); h.add(new InsnNode(ACONST_NULL));
        h.add(new FieldInsnNode(PUTFIELD,base.name,pathField,"[S"));
        h.add(new VarInsnNode(ALOAD,0)); h.add(new MethodInsnNode(INVOKEVIRTUAL,base.name,resetMethod,"()V",false));
        h.add(skip);
        h.add(new VarInsnNode(ALOAD,0)); h.add(new MethodInsnNode(INVOKESPECIAL,player.name,"hsoskyRuntimeTick","()V",false));
        update.instructions.insert(h); instantHooks++;
        addPlayerRuntimeMethod();
    }

    private static void addPlayerRuntimeMethod() {
        require(findMethod(player,"hsoskyRuntimeTick","()V")==null,"Player already has runtime helper");
        MethodNode m=new MethodNode(ACC_PRIVATE,"hsoskyRuntimeTick","()V",null,null);
        LabelNode retry=new LabelNode(), ret=new LabelNode(), done=new LabelNode();
        InsnList b=m.instructions;
        b.add(new VarInsnNode(ALOAD,0)); b.add(new FieldInsnNode(GETFIELD,base.name,xField,"I"));
        b.add(new VarInsnNode(ALOAD,0)); b.add(new FieldInsnNode(GETFIELD,base.name,yField,"I"));
        LabelNode alive=new LabelNode(), aliveDone=new LabelNode();
        b.add(new VarInsnNode(ALOAD,0)); b.add(new FieldInsnNode(GETFIELD,base.name,hpField,"I"));
        b.add(new JumpInsnNode(IFGT,alive)); b.add(new InsnNode(ICONST_0)); b.add(new JumpInsnNode(GOTO,aliveDone));
        b.add(alive); b.add(new InsnNode(ICONST_1)); b.add(aliveDone);
        b.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"pollLootAction","(IIZ)I",false));
        b.add(new VarInsnNode(ISTORE,1));
        b.add(new VarInsnNode(ILOAD,1)); b.add(new InsnNode(ICONST_2)); b.add(new JumpInsnNode(IF_ICMPEQ,retry));
        b.add(new VarInsnNode(ILOAD,1)); b.add(new InsnNode(ICONST_1)); b.add(new JumpInsnNode(IF_ICMPEQ,ret));
        b.add(new VarInsnNode(ILOAD,1)); b.add(new InsnNode(ICONST_3)); b.add(new JumpInsnNode(IF_ICMPNE,done));
        b.add(ret);
        b.add(new VarInsnNode(ALOAD,0)); b.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"getActionX","()I",false));
        b.add(new FieldInsnNode(PUTFIELD,base.name,xField,"I"));
        b.add(new VarInsnNode(ALOAD,0)); b.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"getActionY","()I",false));
        b.add(new FieldInsnNode(PUTFIELD,base.name,yField,"I"));
        b.add(new VarInsnNode(ALOAD,0)); b.add(new InsnNode(ACONST_NULL)); b.add(new FieldInsnNode(PUTFIELD,base.name,pathField,"[S"));
        b.add(new VarInsnNode(ALOAD,0)); b.add(new MethodInsnNode(INVOKEVIRTUAL,base.name,resetMethod,"()V",false));
        b.add(new MethodInsnNode(INVOKESTATIC,sender.name,senderGet.name,senderGet.desc,false));
        b.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"getActionX","()I",false)); b.add(new InsnNode(I2S));
        b.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"getActionY","()I",false)); b.add(new InsnNode(I2S));
        b.add(new MethodInsnNode(INVOKEVIRTUAL,sender.name,sendMove.name,sendMove.desc,false));
        b.add(new JumpInsnNode(GOTO,done));
        b.add(retry);
        b.add(new MethodInsnNode(INVOKESTATIC,sender.name,senderGet.name,senderGet.desc,false));
        b.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"getActionId","()I",false)); b.add(new InsnNode(I2S));
        b.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"getActionType","()I",false)); b.add(new InsnNode(I2B));
        b.add(new MethodInsnNode(INVOKEVIRTUAL,sender.name,sendPickup.name,sendPickup.desc,false));
        b.add(done); b.add(new InsnNode(RETURN));
        player.methods.add(m);
    }

    private static void patchSenderMovement() {
        InsnList h=new InsnList(); h.add(new VarInsnNode(ILOAD,1)); h.add(new VarInsnNode(ILOAD,2));
        h.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"recordMove","(II)V",false));
        sendMove.instructions.insert(h); moveSendHooks++;
    }

    private static void patchRemotePickup() {
        MethodInsnNode pickupCall=null;
        for (AbstractInsnNode n=update.instructions.getFirst();n!=null;n=n.getNext()) {
            if (n instanceof MethodInsnNode) {
                MethodInsnNode c=(MethodInsnNode)n;
                if (sender.name.equals(c.owner)&&sendPickup.name.equals(c.name)&&sendPickup.desc.equals(c.desc)) { pickupCall=c; break; }
            }
        }
        require(pickupCall!=null,"Cannot locate auto-pickup packet call");
        int itemVar=findObjectLocalForFields(pickupCall,ground.name,idField,typeField);
        require(itemVar>=0,"Cannot map ground-item local at pickup call");

        AbstractInsnNode start=findPreviousCall(pickupCall,sender.name,senderGet.name,senderGet.desc,25);
        require(start!=null,"Cannot find sender getter before pickup");
        InsnList note=new InsnList();
        note.add(new VarInsnNode(ALOAD,itemVar)); note.add(new FieldInsnNode(GETFIELD,base.name,idField,"I"));
        note.add(new VarInsnNode(ALOAD,itemVar)); note.add(new FieldInsnNode(GETFIELD,base.name,typeField,"B"));
        note.add(new VarInsnNode(ALOAD,itemVar)); note.add(new FieldInsnNode(GETFIELD,base.name,xField,"I"));
        note.add(new VarInsnNode(ALOAD,itemVar)); note.add(new FieldInsnNode(GETFIELD,base.name,yField,"I"));
        note.add(new VarInsnNode(ALOAD,0)); note.add(new FieldInsnNode(GETFIELD,base.name,xField,"I"));
        note.add(new VarInsnNode(ALOAD,0)); note.add(new FieldInsnNode(GETFIELD,base.name,yField,"I"));
        note.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"notePickupAttempt","(IIIIII)V",false));
        update.instructions.insertBefore(start,note); pickupAttemptHooks++;

        for (AbstractInsnNode n=update.instructions.getFirst();n!=null;n=n.getNext()) {
            if (!(n instanceof MethodInsnNode) || n.getOpcode()!=INVOKESTATIC || !"(IIII)I".equals(((MethodInsnNode)n).desc)) continue;
            AbstractInsnNode a=nextReal(n), b=nextReal(a), c=nextReal(b);
            if (a instanceof VarInsnNode && a.getOpcode()==ALOAD
                    && b instanceof FieldInsnNode && b.getOpcode()==GETFIELD && "I".equals(((FieldInsnNode)b).desc)
                    && c instanceof JumpInsnNode) {
                InsnList z=new InsnList(); z.add(new InsnNode(POP)); z.add(new InsnNode(POP)); z.add(new InsnNode(POP)); z.add(new InsnNode(POP)); z.add(new InsnNode(ICONST_0));
                update.instructions.insertBefore(n,z); update.instructions.remove(n); pickupDistanceHooks++; break;
            }
        }
    }

    private static void patchDropCreation() {
        MethodNode ctor=findMethod(ground,"<init>","(IBLjava/lang/String;IISB)V");
        for (AbstractInsnNode n=ctor.instructions.getFirst();n!=null;n=n.getNext()) if (n.getOpcode()==RETURN) {
            InsnList h=new InsnList();
            h.add(new VarInsnNode(ALOAD,0)); h.add(new FieldInsnNode(GETFIELD,base.name,idField,"I"));
            h.add(new VarInsnNode(ALOAD,0)); h.add(new FieldInsnNode(GETFIELD,base.name,typeField,"B"));
            h.add(new VarInsnNode(ALOAD,0)); h.add(new FieldInsnNode(GETFIELD,base.name,xField,"I"));
            h.add(new VarInsnNode(ALOAD,0)); h.add(new FieldInsnNode(GETFIELD,base.name,yField,"I"));
            h.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"onDropCreated","(IIII)V",false));
            ctor.instructions.insertBefore(n,h); dropCreateHooks++;
        }
    }

    private static void patchReceiver() {
        patchDropRemoved();
        patchKillResults();
        patchServerCorrection();
    }

    private static void patchDropRemoved() {
        for (MethodNode m:receiver.methods) {
            int[] readVars=firstReadPrimitiveVars(m,"readByte","()B","readShort","()S");
            if (readVars==null) continue;
            for (AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
                if (!(n instanceof MethodInsnNode)||n.getOpcode()!=INVOKESTATIC) continue;
                MethodInsnNode c=(MethodInsnNode)n;
                if (base.name.equals(c.owner)&&"(IB)L".concat(base.name).concat(";").equals(c.desc)) {
                    InsnList h=new InsnList(); h.add(new VarInsnNode(ILOAD,readVars[1])); h.add(new VarInsnNode(ILOAD,readVars[0]));
                    h.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"onDropRemoved","(II)V",false));
                    m.instructions.insertBefore(findArgumentStart(n,2),h); dropRemoveHooks++; return;
                }
            }
        }
        throw new IllegalStateException("Cannot locate ground-item removal response");
    }

    private static void patchKillResults() {
        for (MethodNode m:receiver.methods) {
            List<FieldInsnNode> hpWrites=new ArrayList<FieldInsnNode>();
            for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof FieldInsnNode&&n.getOpcode()==PUTFIELD) {
                FieldInsnNode f=(FieldInsnNode)n;
                AbstractInsnNode before=previousReal(n);
                if(base.name.equals(f.owner)&&"I".equals(f.desc)&&before instanceof MethodInsnNode
                        && "java/io/DataInputStream".equals(((MethodInsnNode)before).owner)
                        && "readInt".equals(((MethodInsnNode)before).name)) hpWrites.add(f);
            }
            // The own-combat result packet is the sole receiver branch that writes
            // seven actor int fields directly from readInt (HP/MP variants included).
            if(hpWrites.size()!=7) continue;
            int typeVar=-1;
            for(AbstractInsnNode n=m.instructions.getFirst();n!=null&&typeVar<0;n=n.getNext()) if(n instanceof MethodInsnNode) {
                MethodInsnNode c=(MethodInsnNode)n;
                if(!"java/io/DataInputStream".equals(c.owner)||!"readByte".equals(c.name)) continue;
                AbstractInsnNode s=nextReal(n);
                for(int k=0;k<3&&s!=null;k++,s=nextReal(s)) if(s instanceof VarInsnNode&&s.getOpcode()==ISTORE) {
                    typeVar=((VarInsnNode)s).var; break;
                }
            }
            int actorVar=-1;
            for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof MethodInsnNode&&n.getOpcode()==INVOKESTATIC) {
                MethodInsnNode c=(MethodInsnNode)n; AbstractInsnNode s=nextReal(n);
                if(c.desc.startsWith("(IB)L")) {
                    for(int k=0;k<3&&s!=null;k++,s=nextReal(s)) if(s instanceof VarInsnNode&&s.getOpcode()==ASTORE) {
                        actorVar=((VarInsnNode)s).var; break;
                    }
                    if(actorVar>=0) break;
                }
            }
            if(actorVar<0||typeVar<0) continue;
            FieldInsnNode hpWrite=hpWrites.get(1); hpField=hpWrite.name;
            InsnList h=new InsnList();
            h.add(new VarInsnNode(ALOAD,actorVar));h.add(new FieldInsnNode(GETFIELD,base.name,idField,"I"));
            h.add(new VarInsnNode(ILOAD,typeVar));
            h.add(new VarInsnNode(ALOAD,actorVar));h.add(new FieldInsnNode(GETFIELD,base.name,xField,"I"));
            h.add(new VarInsnNode(ALOAD,actorVar));h.add(new FieldInsnNode(GETFIELD,base.name,yField,"I"));
            h.add(new VarInsnNode(ALOAD,actorVar));h.add(new FieldInsnNode(GETFIELD,base.name,hpField,"I"));
            h.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"recordTargetResult","(IIIII)V",false));
            m.instructions.insert(hpWrite,h); killHooks++; return;
        }
    }

    private static void patchServerCorrection() {
        // Movement response: byte type, short auxiliary, short id, short x, short y, byte state.
        for (MethodNode m:receiver.methods) {
            List<Integer> vars=new ArrayList<Integer>();
            List<String> reads=new ArrayList<String>();
            for (AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
                if (n instanceof MethodInsnNode) {
                    MethodInsnNode c=(MethodInsnNode)n;
                    if ("java/io/DataInputStream".equals(c.owner) && ("readByte".equals(c.name)||"readShort".equals(c.name))) {
                        AbstractInsnNode s=nextReal(n); if (s instanceof VarInsnNode && s.getOpcode()==ISTORE) {
                            vars.add(Integer.valueOf(((VarInsnNode)s).var)); reads.add(c.name);
                            if (vars.size()==6) break;
                        }
                    }
                }
            }
            if (reads.size()<6 || !"readByte".equals(reads.get(0)) || !"readShort".equals(reads.get(1))
                    || !"readShort".equals(reads.get(2)) || !"readShort".equals(reads.get(3))
                    || !"readShort".equals(reads.get(4)) || !"readByte".equals(reads.get(5))) continue;
            for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) {
                if(!(n instanceof VarInsnNode)||n.getOpcode()!=ALOAD) continue;
                AbstractInsnNode f=nextReal(n), j=nextReal(f);
                if(!(f instanceof FieldInsnNode)||f.getOpcode()!=GETSTATIC
                        || !("L"+player.name+";").equals(((FieldInsnNode)f).desc)
                        || !(j instanceof JumpInsnNode)||j.getOpcode()!=IF_ACMPEQ) continue;
                InsnList h=new InsnList();
                h.add(new VarInsnNode(ILOAD,vars.get(3).intValue()));
                h.add(new VarInsnNode(ILOAD,vars.get(4).intValue()));
                h.add(new MethodInsnNode(INVOKESTATIC,RUNTIME,"onServerPosition","(II)Z",false));
                LabelNode quiet=new LabelNode();
                h.add(new JumpInsnNode(IFEQ,quiet));
                h.add(new LdcInsnNode("No-clip OFF: server tu choi toa do trong khu nay"));
                h.add(new MethodInsnNode(INVOKESTATIC,notifyOwner,notifyName,notifyDesc,false));
                h.add(quiet);
                m.instructions.insert(((JumpInsnNode)j).label,h);
                correctionHooks++; return;
            }
        }
        throw new IllegalStateException("Cannot locate movement response for correction tracking");
    }

    private static void patchCollision() {
        // Reset adaptive state on every actual map load.
        AbstractInsnNode first=mapLoad.instructions.getFirst();
        mapLoad.instructions.insertBefore(first,new MethodInsnNode(INVOKESTATIC,RUNTIME,"onMapLoaded","()V",false)); mapHooks++;
        // The public tile query retains its boundary return; only the final collision-array value is adjusted.
        for (MethodNode m:map.methods) if ("(II)I".equals(m.desc)) {
            if(opcodeCount(m,IALOAD)!=1 || opcodeCount(m,IRETURN)<1) continue;
            for (AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if (n.getOpcode()==IALOAD) {
                m.instructions.insert(n,new MethodInsnNode(INVOKESTATIC,RUNTIME,"adjustTile","(I)I",false)); mapHooks++;
            }
        }
        // Pathfinder reads the raw collision array, so adjust only values that are subsequently tested against both -1 and 1.
        for (MethodNode m:pathfinder.methods) if ("(IIIII)[S".equals(m.desc)) {
            for (AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if (n.getOpcode()==IALOAD) {
                AbstractInsnNode s=nextReal(n); if (!(s instanceof VarInsnNode)||s.getOpcode()!=ISTORE) continue;
                int v=((VarInsnNode)s).var;
                if (localComparedTo(m,s,v,-1,45)&&localComparedTo(m,s,v,1,45)) {
                    m.instructions.insert(n,new MethodInsnNode(INVOKESTATIC,RUNTIME,"adjustTile","(I)I",false)); pathHooks++;
                }
            }
            if(pathHooks==0) for (AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if(n.getOpcode()==IALOAD) {
                AbstractInsnNode a=nextReal(n);
                if(a!=null && (a.getOpcode()==I2B || isInt(a,1) || isInt(a,-1))) {
                    m.instructions.insert(n,new MethodInsnNode(INVOKESTATIC,RUNTIME,"adjustTile","(I)I",false)); pathHooks++;
                }
            }
        }
    }

    private static void addRuntime() throws IOException {
        byte[] raw=readAll(new FileInputStream(RUNTIME+".class"));
        ClassNode c=readClass(raw); c.version=V1_6;
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(w);
        entries.put(RUNTIME+".class",w.toByteArray()); times.put(RUNTIME+".class",Long.valueOf(System.currentTimeMillis()));
    }

    private static void selfTestRuntime() throws Exception {
        HSOSKYRuntimeSupport.onMapLoaded();
        require(HSOSKYRuntimeSupport.adjustTile(1)==0 && HSOSKYRuntimeSupport.adjustTile(-1)==0
                && HSOSKYRuntimeSupport.adjustTile(2)==2,"Runtime no-clip tile policy failed");
        HSOSKYRuntimeSupport.recordMove(100,100);
        require(!HSOSKYRuntimeSupport.onServerPosition(500,500),"No-clip disabled after one correction");
        require(!HSOSKYRuntimeSupport.onServerPosition(500,500),"No-clip disabled after two corrections");
        require(HSOSKYRuntimeSupport.onServerPosition(500,500),"No-clip did not disable after three corrections");
        require(HSOSKYRuntimeSupport.adjustTile(1)==1,"Disabled no-clip still bypasses walls");
        HSOSKYRuntimeSupport.onMapLoaded();
        require(HSOSKYRuntimeSupport.adjustTile(1)==0,"New map did not re-enable no-clip");

        HSOSKYRuntimeSupport.recordTargetResult(10,1,100,100,0);
        HSOSKYRuntimeSupport.onDropCreated(99,3,105,105);
        HSOSKYRuntimeSupport.notePickupAttempt(99,3,105,105,20,20);
        Thread.sleep(520L);
        require(HSOSKYRuntimeSupport.pollLootAction(20,20,true)==HSOSKYRuntimeSupport.ACTION_TELEPORT_ITEM,
                "Loot fallback did not start after 500 ms");
        Thread.sleep(220L);
        require(HSOSKYRuntimeSupport.pollLootAction(105,105,true)==HSOSKYRuntimeSupport.ACTION_RETRY_PICKUP,
                "Loot fallback did not retry after 200 ms");
        HSOSKYRuntimeSupport.onDropRemoved(99,3);
        require(HSOSKYRuntimeSupport.pollLootAction(105,105,true)==HSOSKYRuntimeSupport.ACTION_RETURN_ORIGIN,
                "Loot fallback did not return after confirmed pickup");
    }

    private static void verify(File out) throws Exception {
        JarFile j=new JarFile(out);
        try {
            require(j.getManifest()!=null,"Output lost manifest");
            require(j.getEntry(RUNTIME+".class")!=null,"Output lost runtime support");
            require(j.getEntry(COMMAND+".class")!=null && j.getEntry(RANGE+".class")!=null,"Output lost existing features");
            require(j.size()==entries.size(),"Entry count changed during write");
            for (Enumeration<JarEntry> en=j.entries();en.hasMoreElements();) {
                JarEntry e=en.nextElement(); if(e.getName().endsWith(".class")) new ClassReader(readAll(j.getInputStream(e)));
            }
            ClassNode p=readClass(readAll(j.getInputStream(j.getEntry(player.name+".class"))));
            require(findMethod(p,"hsoskyRuntimeTick","()V")!=null,"Player runtime helper missing");
            require(intCount(findCooldownMethod(p),2000)>=2,"Server cooldown/original 2000 ms lock was modified");
        } finally { j.close(); }
        URLClassLoader loader=new URLClassLoader(new URL[]{out.toURI().toURL(),new File("microemulator.jar").toURI().toURL()},
                HSOSKYServerCdLootNoClipPatcher.class.getClassLoader());
        try {
            Class.forName(RUNTIME,false,loader);
            Class.forName(player.name.replace('/','.'),false,loader);
            Class.forName(sender.name.replace('/','.'),false,loader);
            Class.forName(receiver.name.replace('/','.'),false,loader);
            Class.forName(map.name.replace('/','.'),false,loader);
            Class.forName(ground.name.replace('/','.'),false,loader);
            Class.forName(pathfinder.name.replace('/','.'),false,loader);
        } finally { loader.close(); }
    }

    private static MethodNode findCooldownMethod(ClassNode p) {
        for(MethodNode m:p.methods) if("(II)V".equals(m.desc)&&intCount(m,2000)>=2) return m;
        return null;
    }

    private static int[] firstReadPrimitiveVars(MethodNode m,String n1,String d1,String n2,String d2) {
        int first=-1,second=-1;
        for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof MethodInsnNode) {
            MethodInsnNode c=(MethodInsnNode)n; AbstractInsnNode s=nextReal(n);
            if(!(s instanceof VarInsnNode)||s.getOpcode()!=ISTORE) continue;
            if(first<0&&n1.equals(c.name)&&d1.equals(c.desc)) first=((VarInsnNode)s).var;
            else if(first>=0&&n2.equals(c.name)&&d2.equals(c.desc)) {second=((VarInsnNode)s).var;return new int[]{first,second};}
        }
        return null;
    }

    private static AbstractInsnNode findArgumentStart(AbstractInsnNode call,int args) {
        AbstractInsnNode n=call; for(int i=0;i<args;i++) n=previousReal(n); return n;
    }
    private static AbstractInsnNode findPreviousCall(AbstractInsnNode from,String owner,String name,String desc,int limit) {
        AbstractInsnNode n=from; for(int i=0;i<limit&&n!=null;i++,n=previousReal(n)) if(n instanceof MethodInsnNode) {
            MethodInsnNode c=(MethodInsnNode)n;if(owner.equals(c.owner)&&name.equals(c.name)&&desc.equals(c.desc))return n;
        } return null;
    }
    private static int findObjectLocalForFields(AbstractInsnNode from,String ignored,String f1,String f2) {
        Set<Integer> vars=new LinkedHashSet<Integer>(); AbstractInsnNode n=from;
        for(int i=0;i<30&&n!=null;i++,n=previousReal(n)) if(n instanceof FieldInsnNode&&n.getOpcode()==GETFIELD) {
            FieldInsnNode f=(FieldInsnNode)n;if(base.name.equals(f.owner)&&(f1.equals(f.name)||f2.equals(f.name))) {
                AbstractInsnNode a=previousReal(n);if(a instanceof VarInsnNode&&a.getOpcode()==ALOAD)vars.add(Integer.valueOf(((VarInsnNode)a).var));
            }
        }
        return vars.size()==1?vars.iterator().next().intValue():-1;
    }
    private static boolean localComparedTo(MethodNode m,AbstractInsnNode start,int var,int value,int limit) {
        AbstractInsnNode n=start;for(int i=0;i<limit&&n!=null;i++,n=nextReal(n)) if(n instanceof VarInsnNode&&n.getOpcode()==ILOAD&&((VarInsnNode)n).var==var) {
            AbstractInsnNode a=nextReal(n);if(isInt(a,value))return true;
        }return false;
    }
    private static int nearestLoadedLocal(AbstractInsnNode n,int limit) {
        for(int i=0;i<limit&&n!=null;i++){n=previousReal(n);if(n instanceof VarInsnNode&&n.getOpcode()==ILOAD)return ((VarInsnNode)n).var;}return -1;
    }
    private static int fieldCount(MethodNode m,int op,String owner,String name,String desc) {int c=0;for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof FieldInsnNode&&n.getOpcode()==op){FieldInsnNode f=(FieldInsnNode)n;if(owner.equals(f.owner)&&name.equals(f.name)&&desc.equals(f.desc))c++;}return c;}
    private static int opcodeCount(MethodNode m,int op){int c=0;for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())if(n.getOpcode()==op)c++;return c;}
    private static int intCount(MethodNode m,int v){if(m==null)return 0;int c=0;for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())if(isInt(n,v))c++;return c;}
    private static boolean containsInt(MethodNode m,int v){return intCount(m,v)>0;}
    private static boolean returnsConstant(MethodNode m,int v){AbstractInsnNode a=firstReal(m.instructions.getFirst());return isInt(a,v)&&nextReal(a)!=null&&nextReal(a).getOpcode()==IRETURN;}
    private static boolean calls(MethodNode m,String o,String n,String d){for(AbstractInsnNode x=m.instructions.getFirst();x!=null;x=x.getNext())if(x instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)x;if(o.equals(c.owner)&&n.equals(c.name)&&d.equals(c.desc))return true;}return false;}
    private static boolean containsString(ClassNode c,String s){for(MethodNode m:c.methods)if(containsString(m,s))return true;return false;}
    private static boolean containsString(MethodNode m,String s){for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof LdcInsnNode&&((LdcInsnNode)n).cst instanceof String&&((String)((LdcInsnNode)n).cst).indexOf(s)>=0)return true;return false;}
    private static MethodNode findMethod(ClassNode c,String n,String d){for(MethodNode m:c.methods)if(n.equals(m.name)&&d.equals(m.desc))return m;return null;}
    private static ClassNode unique(ClassNode old,ClassNode now,String what){require(old==null,"Multiple "+what+" classes");return now;}
    private static AbstractInsnNode firstReal(AbstractInsnNode n){while(n!=null&&n.getOpcode()<0)n=n.getNext();return n;}
    private static AbstractInsnNode nextReal(AbstractInsnNode n){n=n==null?null:n.getNext();while(n!=null&&n.getOpcode()<0)n=n.getNext();return n;}
    private static AbstractInsnNode previousReal(AbstractInsnNode n){n=n==null?null:n.getPrevious();while(n!=null&&n.getOpcode()<0)n=n.getPrevious();return n;}
    private static boolean isInt(AbstractInsnNode n,int v){if(n==null)return false;int o=n.getOpcode();if(o>=ICONST_M1&&o<=ICONST_5)return v==o-ICONST_0;if(n instanceof IntInsnNode)return ((IntInsnNode)n).operand==v;return n instanceof LdcInsnNode&&Integer.valueOf(v).equals(((LdcInsnNode)n).cst);}

    private static void readJar(File f)throws IOException{require(f.isFile(),"Missing input JAR: "+INPUT);JarFile j=new JarFile(f);try{for(Enumeration<JarEntry> en=j.entries();en.hasMoreElements();){JarEntry e=en.nextElement();if(!e.isDirectory()){entries.put(e.getName(),readAll(j.getInputStream(e)));times.put(e.getName(),Long.valueOf(e.getTime()));}}}finally{j.close();}}
    private static byte[] readAll(InputStream in)throws IOException{try{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int n;while((n=in.read(x))!=-1)b.write(x,0,n);return b.toByteArray();}finally{in.close();}}
    private static ClassNode readClass(byte[] b){if(b==null)return null;ClassNode c=new ClassNode();new ClassReader(b).accept(c,0);return c;}
    private static void save(ClassNode c){ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);entries.put(c.name+".class",w.toByteArray());}
    private static void writeJar(File f)throws IOException{File t=new File(f.getPath()+".tmp");JarOutputStream o=new JarOutputStream(new FileOutputStream(t));try{for(Map.Entry<String,byte[]> e:entries.entrySet()){String n=e.getKey();if(n.startsWith("META-INF/")&&(n.endsWith(".SF")||n.endsWith(".RSA")||n.endsWith(".DSA")))continue;JarEntry z=new JarEntry(n);Long tm=times.get(n);if(tm!=null&&tm.longValue()>=0)z.setTime(tm.longValue());o.putNextEntry(z);o.write(e.getValue());o.closeEntry();}}finally{o.close();}Files.move(t.toPath(),f.toPath(),StandardCopyOption.REPLACE_EXISTING);}
    private static void require(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
}
