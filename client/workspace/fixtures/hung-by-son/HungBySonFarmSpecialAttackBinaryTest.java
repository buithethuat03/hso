import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

/** Exercises Farm's real native special chooser through the candidate bridge. */
public final class HungBySonFarmSpecialAttackBinaryTest {
    private static final String SPECIAL_HOST = "HungBySonSpecialAttackBinaryTest";
    private static final String AUTO_HOST = "HungBySonAutoAttackBinaryTest";
    private static int checks;

    private HungBySonFarmSpecialAttackBinaryTest() { }

    public static void main(String[] args) throws Exception {
        require(args.length == 3, "candidate.jar auto-attack.properties farm-special-attack.properties");
        File candidate = new File(args[0]);
        Properties catalog = load(new File(args[1]));
        catalog.putAll(load(new File(args[2])));
        require(candidate.isFile(), "Farm Special candidate exists");
        require(hash(candidate).equalsIgnoreCase(required(catalog, "output.sha256")),
                "Candidate bytes match the Farm Special catalog");
        verifyActualFarmCast(candidate, catalog);
        System.out.println("HungBySonFarmSpecialAttackBinaryTest PASS checks=" + checks
                + "; native Farm special chooser, shared 600ms gate, callback cancellation and scoped context");
    }

    private static void verifyActualFarmCast(File candidate, Properties p) throws Exception {
        Class<?> fixtureType = Class.forName(SPECIAL_HOST + "$Fixture");
        Constructor<?> fixtureCtor = fixtureType.getDeclaredConstructor(File.class, Properties.class);
        fixtureCtor.setAccessible(true);
        Object fixture = fixtureCtor.newInstance(candidate, p);
        try {
            call(fixture, "installReadyWorld");
            ClassLoader loader = (ClassLoader)get(fixture, "loader");
            Class<?> playerType = loader.loadClass(required(p, "native.player.owner").replace('/', '.'));
            Class<?> entityType = loader.loadClass(required(p, "native.entity.owner").replace('/', '.'));
            Class<?> castType = loader.loadClass(required(p, "native.cast.owner").replace('/', '.'));
            Object world = get(fixture, "world");
            Object player = call(fixture, "worldPlayer");
            Object bridge = get(fixture, "bridge");
            Class<?> runtime = (Class<?>)get(fixture, "runtime");

            clearPackets();
            seedNativeFontMetrics(loader);
            Object definition = seedNativeSkill(loader, playerType, p);
            configureSpecialSkill(fixtureType, fixture, p, playerType, definition);
            addReadyZeroSkillDefinition(fixture, loader, definition, p);
            int zeroSlot = addReadyZeroHotbarSlot(fixture, playerType);
            set(world, world.getClass(), "runNativeSkill", Boolean.TRUE);

            Object monster = call(fixture, "entity", Integer.valueOf(100), Byte.valueOf((byte)1),
                    Integer.valueOf(2), Integer.valueOf(0), Integer.valueOf(0),
                    Integer.valueOf(20), Integer.valueOf(30));
            set(monster, entityType, "cu", Integer.valueOf(41));
            call(fixture, "replaceEntities", (Object)new Object[]{monster});
            Class<?> targetOwner = loader.loadClass(required(p, "native.target.owner").replace('/', '.'));
            setStatic(targetOwner, required(p, "native.target.field"), monster);
            set(player, playerType, "cA", Integer.valueOf(-1));
            set(player, playerType, "cG", Integer.valueOf(0));
            set(player, playerType, "ef", Byte.valueOf((byte)1));
            set(player, playerType, "cw", Byte.valueOf((byte)1));
            set(player, playerType, "bu", Integer.valueOf(100));
            setStatic(playerType, "e", Integer.valueOf(1));

            invoke(runtime.getMethod("handleAction", Integer.TYPE), null,
                    Integer.valueOf(-116));
            require(bool(runtime, "specialEnabled") && !bool(runtime, "enabled"),
                    "Shared Special mode is ON while Auto Attack remains OFF");
            require(Boolean.TRUE.equals(bridge.getClass().getMethod("farmSpecialEnabled").invoke(bridge)),
                    "The real native Farm enabled flag and shared Special toggle open Farm eligibility");
            require(!Boolean.TRUE.equals(bridge.getClass().getMethod("specialContext").invoke(bridge)),
                    "Farm context stays closed outside its wrapped chooser call");

            int slotCount = ((Integer)bridge.getClass().getMethod("specialSlotCount").invoke(bridge)).intValue();
            int skillSlot = -1;
            for (int slot = 0; slot < slotCount; slot++) {
                int skill = ((Integer)bridge.getClass().getMethod("specialSkillAt", Integer.TYPE)
                        .invoke(bridge, Integer.valueOf(slot))).intValue();
                if (skill == 7) skillSlot = slot;
            }
            require(skillSlot >= 0, "Native selected hotbar exposes the configured skill 7 slot");
            require(zeroSlot >= 0 && ((Integer)bridge.getClass().getMethod("specialSkillAt", Integer.TYPE)
                            .invoke(bridge, Integer.valueOf(zeroSlot))).intValue() == 0,
                    "The same native hotbar exposes ready skill 0 below skill 7");
            require(Boolean.TRUE.equals(bridge.getClass().getMethod("specialSkillReady", Integer.TYPE)
                            .invoke(bridge, Integer.valueOf(7))),
                    "Native base eligibility accepts learned skill 7 before the separate 600ms gate");
            require(Boolean.TRUE.equals(bridge.getClass().getMethod("specialSkillReady", Integer.TYPE)
                            .invoke(bridge, Integer.valueOf(0))),
                    "Native base eligibility also accepts skill 0, so the Farm fixture has a lower ready choice");
            Method farmChoose = playerType.getMethod(required(p, "native.farmChoose.method"),
                    Integer.TYPE, Boolean.TYPE);
            int start = packetCount();
            setNativeRequestClock(loader, p, System.currentTimeMillis() + 60000L, (short)5000);
            set(fixture, fixtureType, "clockNow", Long.valueOf(1L));
            invoke(farmChoose, player, Integer.valueOf(0), Boolean.FALSE);
            require(packetCount() == start + 1 && packetHasOpcode(9),
                    "The actual native Farm wrapper dispatches the selected skill and emits opcode 9; packets="
                    + packetSnapshot() + " farmReady=" + bridge.getClass().getMethod("farmSpecialEnabled").invoke(bridge)
                    + " skillReady=" + bridge.getClass().getMethod("specialSkillReady", Integer.TYPE)
                            .invoke(bridge, Integer.valueOf(7)) + " nativeResult=" + call(fixture, "worldSkillResult")
                    + " failure=" + call(fixture, "worldSkillFailure"));
            require(field(playerType, required(p, "special.farm.cursor.field")).getByte(null) == skillSlot,
                    "Successful Farm dispatch commits the chosen skill 7 cursor, not skill 0");
            require(Boolean.TRUE.equals(bridge.getClass().getMethod("farmSpecialEnabled").invoke(bridge))
                            && !bool(runtime, "enabled"),
                    "Farm special dispatch leaves native Farm on and Auto Attack off");
            require(!Boolean.TRUE.equals(bridge.getClass().getMethod("specialContext").invoke(bridge)),
                    "The thread-scoped Farm special context closes after dispatch");

            Object cast = call(fixture, "nativeCast", p);
            String sentName = required(p, "special.sent.field");
            String tagName = required(p, "request.tag.field");
            require(field(castType, sentName).getBoolean(cast),
                    "Farm's successful direct send marks the cast sent");
            require(field(castType, tagName).getInt(cast) == -1,
                    "Farm's direct send cancels the animation request callback");
            String snapshot = packetSnapshot();
            Method callback = castType.getMethod(required(p, "request.method"));
            invoke(callback, cast);
            require(packetCount() == start + 1 && snapshot.equals(packetSnapshot()),
                    "The delayed native callback cannot duplicate Farm's direct packet");

            resetNativeCooldown(playerType, fixture, p, 7);
            set(player, playerType, "bu", Integer.valueOf(100));
            set(fixture, fixtureType, "clockNow", Long.valueOf(601L));
            int at600 = packetCount();
            invoke(farmChoose, player, Integer.valueOf(0), Boolean.FALSE);
            require(packetCount() == at600,
                    "Farm special waits at exactly 600ms even though native eligibility is ready");

            resetNativeCooldown(playerType, fixture, p, 7);
            set(player, playerType, "bu", Integer.valueOf(100));
            set(fixture, fixtureType, "clockNow", Long.valueOf(602L));
            invoke(farmChoose, player, Integer.valueOf(0), Boolean.FALSE);
            require(packetCount() == at600 + 1 && packetHasOpcode(9),
                    "The actual Farm wrapper casts skill 7 immediately at 601ms with one opcode-9 packet");
            require(field(playerType, required(p, "special.farm.cursor.field")).getByte(null) == skillSlot,
                    "The post-cooldown Farm cast continues to use the higher ready skill 7");

            if ("true".equals(p.getProperty("quiet.farm.local")))
                verifyQuietVisuals(fixture, fixtureType, playerType, entityType, castType, player, bridge, runtime, p);
            if ("true".equals(p.getProperty("quiet.farm.render")))
                verifyRenderOnlyRecovery(fixture, fixtureType, playerType, entityType, castType,
                        player, bridge, runtime, loader, farmChoose, p, candidate);
            verifyManualAndRemoteScope(fixture, fixtureType, playerType, entityType, castType,
                    player, bridge, p, packetCount());
            if ("true".equals(p.getProperty("farm.spot.enabled")))
                verifyFarmSpot(fixture, fixtureType, loader, playerType, entityType, player, bridge, runtime, p);
        } finally {
            call(fixture, "close");
        }
    }

    private static void verifyRenderOnlyRecovery(Object fixture, Class<?> fixtureType,
            Class<?> playerType, Class<?> entityType, Class<?> castType, Object player, Object bridge,
            Class<?> runtime, ClassLoader loader, Method farmChoose, Properties p, File candidate) throws Exception {
        Method quiet = bridge.getClass().getMethod("farmQuietRender", entityType);
        String[] pose = {"cG", "cf", "cQ", "dX", "A", "bb"};
        int[] actual = {2, 5, 6, 9, 4, 3};
        int x=field(entityType,"aZ").getInt(player), y=field(entityType,"ba").getInt(player);
        short[] route = new short[]{1,2,3};
        set(player,playerType,"cO",route); set(player,playerType,"bg",Integer.valueOf(x+100));
        set(player,playerType,"bh",Integer.valueOf(y+100)); set(player,playerType,"bc",Integer.valueOf(3));
        set(player,playerType,"bd",Integer.valueOf(4)); set(player,playerType,"cA",Integer.valueOf(7));
        set(player,playerType,"cR",Integer.valueOf(12));
        Object cast=call(fixture,"nativeCast",p);
        Object targets=field(castType,required(p,"cast.targets.field")).get(cast);
        set(player,playerType,"cS",targets);
        for(int i=0;i<pose.length;i++) set(player,playerType,pose[i],Integer.valueOf(actual[i]));
        require(Boolean.TRUE.equals(invoke(quiet,null,player)),"Local Farm attack pose is visually quiet");
        for(int i=0;i<pose.length;i++) {
            Method read=bridge.getClass().getMethod(required(p,"quiet.render.field."+pose[i]),entityType);
            require(((Integer)invoke(read,null,player)).intValue()==(pose[i].equals("dX")?-1:0)
                            && field(entityType,pose[i]).getInt(player)==actual[i],
                    "Render getter hides "+pose[i]+" without changing native simulation state");
        }
        String graphics=required(p,"quiet.render.graphics");
        Class<?> graphicsType=loader.loadClass(graphics.substring(1,graphics.length()-1).replace('/','.'));
        for(String layer:new String[]{"d","e"}) {
            // No graphics/resources are needed: the real renderer exits before its cast overlay.
            Method draw=entityType.getMethod(layer,graphicsType,Integer.TYPE);
            invoke(draw,player,(Object)null,Integer.valueOf(100));
            require(field(entityType,"dX").getInt(player)==9 && field(entityType,"cQ").getInt(player)==6,
                    "Actual "+layer+" cast-overlay renderer exits without mutating native animation timing");
        }
        set(player,playerType,"cH",Integer.valueOf(0)); set(player,playerType,"s",Byte.valueOf((byte)0));
        require(((Integer)entityType.getMethod("q").invoke(player)).intValue()==0
                        && field(entityType,"cf").getInt(player)==5,
                "Actual sprite atlas helper selects idle while the native attack frame remains intact");
        require(field(entityType,"cO").get(player)==route && field(entityType,"bg").getInt(player)==x+100
                        && field(entityType,"bh").getInt(player)==y+100 && field(entityType,"bc").getInt(player)==3
                        && field(entityType,"bd").getInt(player)==4 && field(entityType,"cA").getInt(player)==7
                        && field(entityType,"cR").getInt(player)==12 && field(entityType,"cS").get(player)==targets,
                "Rendering preserves Farm path, destination, velocity and native cast targets/cursors");

        // Exercise actual native movement after a simulated arrival/revival in the village.
        set(player,playerType,"cG",Integer.valueOf(4));
        require(Boolean.FALSE.equals(invoke(quiet,null,player)),"Death remains entirely native");
        set(player,playerType,"cG",Integer.valueOf(0)); set(player,playerType,"bs",Integer.valueOf(100));
        for(String list:new String[]{"dv","dw","k"}) if(field(entityType,list).get(player)==null)
            set(player,playerType,list,loader.loadClass("et").getConstructor().newInstance());
        set(player,playerType,"bI",Boolean.FALSE); set(player,playerType,"ea",Boolean.FALSE);
        set(player,playerType,"ef",Byte.valueOf((byte)-1));
        set(player,playerType,"bj",Integer.valueOf(4)); set(player,playerType,"cO",null);
        set(player,playerType,"bg",Integer.valueOf(x+100)); set(player,playerType,"bh",Integer.valueOf(y));
        entityType.getMethod("h").invoke(player);
        require(field(entityType,"cG").getInt(player)==1 && field(entityType,"bc").getInt(player)>0
                        && field(entityType,"bg").getInt(player)==x+100,
                "Native automatic return movement can enter walking state and keep its destination");
        set(player,playerType,"cO",route);
        require(Boolean.FALSE.equals(invoke(quiet,null,player))
                        && ((Integer)bridge.getClass().getMethod(required(p,"quiet.render.field.cG"),entityType)
                                .invoke(null,player)).intValue()==1 && field(entityType,"cO").get(player)==route,
                "Walking back to the farm bypasses the quiet pose and retains the route");
        set(player,playerType,"ef",Byte.valueOf((byte)1));

        // Incoming/local fallback casts must still initialize and progress their real callbacks.
        set(player,playerType,"cO",null); set(player,playerType,"cG",Integer.valueOf(0));
        set(player,playerType,"cA",Integer.valueOf(-1)); set(player,playerType,"bc",Integer.valueOf(0));
        set(player,playerType,"bd",Integer.valueOf(0)); set(player,playerType,"bg",Integer.valueOf(x));
        Method init=castType.getDeclaredMethod(required(p,"cast.init.method"),entityType,
                targets.getClass(),Integer.TYPE,Byte.TYPE); init.setAccessible(true);
        invoke(init,cast,player,targets,Integer.valueOf(7),Byte.valueOf((byte)-1));
        require(field(entityType,"cG").getInt(player)==2
                        && !field(castType,required(p,"special.sent.field")).getBoolean(cast),
                "The real local cast initializer and fallback helper run while quiet rendering is ON");
        int tick=field(entityType,"cQ").getInt(player);
        Method nativeAnimation=field(entityType,"dh").get(player).getClass().getDeclaredMethod(
                required(p,"request.animation.method"),entityType); nativeAnimation.setAccessible(true);
        invoke(nativeAnimation,field(entityType,"dh").get(player),player);
        require(field(entityType,"cQ").getInt(player)==tick+1,
                "Native animation timing progresses instead of being reset or returned before callbacks");
        bridge.getClass().getMethod("cancelCast",castType).invoke(null,cast);
        set(player,playerType,"cG",Integer.valueOf(0)); set(player,playerType,"cA",Integer.valueOf(-1));
        set(player,playerType,"bc",Integer.valueOf(0)); set(player,playerType,"bd",Integer.valueOf(0));
        set(player,playerType,"cQ",Integer.valueOf(0)); set(player,playerType,"bh",Integer.valueOf(y));

        int start=packetCount();
        set(player,entityType,"f",new byte[]{0}); set(player,entityType,"cg",new byte[]{0});
        for(int i=0;i<40;i++) {
            resetNativeCooldown(playerType,fixture,p,7); set(player,playerType,"bu",Integer.valueOf(100));
            set(fixture,fixtureType,"clockNow",Long.valueOf(2000L+i*601L));
            invoke(farmChoose,player,Integer.valueOf(0),Boolean.FALSE);
            require(packetCount()==start+i+1,"Farm still sends exactly once at elapsed "+(i*601)+"ms");
            entityType.getMethod("J").invoke(player);
            for(String field:pose) bridge.getClass().getMethod(required(p,"quiet.render.field."+field),entityType)
                    .invoke(null,player);
        }
        require(39*601>15000 && packetCount()==start+40,
                "Direct Farm sends and native update/callback cycles continue beyond the reported 15 seconds");
        // Reuse the normal-request fixture setup for the loader's redirected skill lookup/target builder.
        Object request=host(AUTO_HOST,"prepareNativeRequest",new Class<?>[]{
                Class.forName(AUTO_HOST+"$NativeProbeLoader"),File.class,Properties.class,
                Object.class,Object.class,Integer.TYPE,Byte.TYPE},loader,candidate,p,player,
                field(castType,required(p,"cast.target.field")).get(cast),Integer.valueOf(0),Byte.valueOf((byte)1));
        Object ordinary=get(request,"cast"); int ordinaryPackets=packetCount();
        set(player,playerType,"cG",Integer.valueOf(2));
        setNativeRequestClock(loader,p,4000000L,(short)0);
        setStatic(Class.forName(AUTO_HOST),"nativeRequestNow",Long.valueOf(4000651L));
        bridge.getClass().getMethod(required(p,"quiet.render.field.cQ"),entityType).invoke(null,player);
        invoke(castType.getMethod(required(p,"request.method")),ordinary);
        require(packetCount()==ordinaryPackets+1,
                "An ordinary native local callback still sends at its real frame while the pose is hidden");
        set(player,playerType,"cG",Integer.valueOf(0));
        Object remote=unsafeAllocate(playerType); initializeActor(remote,playerType,p);
        require(Boolean.FALSE.equals(invoke(quiet,null,remote)),"Remote rendering stays native");
        runtime.getMethod("handleAction",Integer.TYPE).invoke(null,Integer.valueOf(-116));
        set(player,playerType,"cG",Integer.valueOf(2)); set(player,playerType,"cf",Integer.valueOf(5));
        require(Boolean.FALSE.equals(invoke(quiet,null,player))
                        && ((Integer)bridge.getClass().getMethod(required(p,"quiet.render.field.cf"),entityType)
                                .invoke(null,player)).intValue()==5,"Special OFF restores the original visible pose");
        set(player,playerType,"cG",Integer.valueOf(0)); set(player,playerType,"cA",Integer.valueOf(-1));
        runtime.getMethod("handleAction",Integer.TYPE).invoke(null,Integer.valueOf(-116));
    }

    /** Farm + Special at its saved spot: stand still and send like Auto Attack; native trip otherwise. */
    private static void verifyFarmSpot(Object fixture, Class<?> fixtureType, ClassLoader loader,
            Class<?> playerType, Class<?> entityType, Object player, Object bridge, Class<?> runtime,
            Properties p) throws Exception {
        Class<?> farmType = loader.loadClass(required(p, "native.farm.enabled.owner").replace('/', '.'));
        Class<?> targetOwner = loader.loadClass(required(p, "native.target.owner").replace('/', '.'));
        String targetField = required(p, "native.target.field");
        String armed = required(p, "farm.spot.armed.field"), latch = required(p, "farm.spot.latch.field");
        String counter = required(p, "farm.spot.counter.field");
        Method spot = bridge.getClass().getMethod("farmSpot");
        require(bool(runtime, "specialEnabled") && !bool(runtime, "enabled")
                        && Boolean.TRUE.equals(bridge.getClass().getMethod("farmSpecialEnabled").invoke(bridge)),
                "Farm spot checks start with Farm and Special ON, Auto Attack OFF");

        Object monster = call(fixture, "entity", Integer.valueOf(100), Byte.valueOf((byte)1),
                Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(220), Integer.valueOf(30));
        Object filtered = call(fixture, "entity", Integer.valueOf(100), Byte.valueOf((byte)1),
                Integer.valueOf(0), Integer.valueOf(1), Integer.valueOf(0), Integer.valueOf(5), Integer.valueOf(5));
        call(fixture, "replaceEntities", (Object)new Object[]{filtered, monster});
        setStatic(targetOwner, targetField, null);
        int x = field(entityType, "aZ").getInt(player), y = field(entityType, "ba").getInt(player);
        set(player, playerType, "cO", null); set(player, playerType, "cG", Integer.valueOf(0));
        set(player, playerType, "cA", Integer.valueOf(-1)); set(player, playerType, "bs", Integer.valueOf(100));
        setStatic(playerType, armed, Byte.valueOf((byte)1)); setStatic(playerType, latch, Boolean.TRUE);
        setStatic(farmType, counter, Integer.valueOf(399));
        resetNativeCooldown(playerType, fixture, p, 7); set(player, playerType, "bu", Integer.valueOf(100));
        set(fixture, fixtureType, "clockNow", Long.valueOf(100000L));
        int start = packetCount();
        require(Boolean.TRUE.equals(invoke(spot, null)), "Farm Special owns the tick at the saved spot");
        require(packetCount() == start + 1 && packetHasOpcode(9),
                "The spot tick sends one direct special packet to the monster; packets=" + packetSnapshot());
        require(field(targetOwner, targetField).get(null) == monster,
                "The spot picks the monster accepted by Farm's selection, not the excluded closer one");
        require(field(playerType, armed).getByte(null) == -1 && !field(playerType, latch).getBoolean(null),
                "The legacy walking auto is disarmed while standing at the spot");
        require(field(farmType, counter).getInt(null) == 0,
                "A sent attack resets Farm's no-attack village-scroll counter");
        require(field(entityType, "cO").get(player) == null && field(entityType, "aZ").getInt(player) == x
                        && field(entityType, "ba").getInt(player) == y && field(entityType, "cG").getInt(player) != 2
                        && field(entityType, "cA").getInt(player) == -1,
                "Standing attack builds no path, keeps position and starts no attack animation");

        setStatic(farmType, counter, Integer.valueOf(7));
        resetNativeCooldown(playerType, fixture, p, 7); set(player, playerType, "bu", Integer.valueOf(100));
        set(fixture, fixtureType, "clockNow", Long.valueOf(100600L));
        boolean guard = "true".equals(p.getProperty("farm.spot.guard"));
        require(Boolean.TRUE.equals(invoke(spot, null)) && packetCount() == start + 1
                        && field(farmType, counter).getInt(null) == (guard ? 0 : 7),
                guard ? "The shared 600ms gate waits; waiting is progress, not stuck"
                        : "The shared 600ms gate waits and a wait does not count as Farm activity");
        set(fixture, fixtureType, "clockNow", Long.valueOf(100601L));
        invoke(spot, null);
        require(packetCount() == start + 2 && field(farmType, counter).getInt(null) == 0,
                "The spot sends again after 600ms");

        set(monster, entityType, "cy", Byte.valueOf((byte)2));
        setStatic(targetOwner, targetField, null);
        resetNativeCooldown(playerType, fixture, p, 7); set(fixture, fixtureType, "clockNow", Long.valueOf(200000L));
        invoke(spot, null);
        require(packetCount() == start + 2 && field(targetOwner, targetField).get(null) == monster,
                "A ghost is only selected so Farm's native captcha branch handles it");
        set(monster, entityType, "cy", Byte.valueOf((byte)0));

        // The actual patched Farm.update: stand mode at the saved spot.
        Object farm = unsafeAllocate(farmType);
        Method update = farmType.getMethod("update");
        Object map = field(loader.loadClass(required(p, "native.map.owner").replace('/', '.')), required(p, "native.map.field")).get(null);
        int mapId = field(map.getClass(), "d").getInt(map);
        byte zone = field(map.getClass(), "u").getByte(null);
        Object[][] statics = {
            {"lliiliil", "lilliililll", Boolean.TRUE}, {"lliiliil", "lilliililli", Boolean.FALSE},
            {"lliiiili", "liliiilllii", Integer.valueOf(0)}, {"lliiiili", "lililliiiil", Boolean.FALSE},
            {"lillllll", "liliililiil", Boolean.FALSE}, {"lliilill", "liilliliili", Boolean.FALSE},
            {"lliiiiil", "lililiillii", Boolean.TRUE}, {"lliiiiil", "lililiillil", Boolean.FALSE},
            {"lliiiiil", "lililiillli", Boolean.FALSE}, {"lliiiiil", "lilililiill", Boolean.FALSE},
            {"lliiiiil", "lililillill", Boolean.FALSE}, {"lliiiiil", "lililiillll", Integer.valueOf(0)},
            {"lliiiiil", "liliiiliiii", Integer.valueOf(mapId)}, {"lliiiiil", "liliiiliill", Integer.valueOf(zone)},
            {"lliiiiil", "liliiiliiil", Integer.valueOf(x)}, {"lliiiiil", "liliiiliili", Integer.valueOf(y)}};
        Object[] saved = new Object[statics.length];
        Field[] fields = new Field[statics.length];
        for (int i = 0; i < statics.length; i++) {
            Class<?> owner = loader.loadClass(((String)call(fixture, "encoded", statics[i][0])).replace('/', '.'));
            fields[i] = field(owner, (String)call(fixture, "encoded", statics[i][1]));
            saved[i] = fields[i].get(null);
            fields[i].set(null, statics[i][2]);
        }
        Field state = fields[11];
        try {
            setStatic(playerType, armed, Byte.valueOf((byte)1)); setStatic(playerType, latch, Boolean.TRUE);
            int before = packetCount(), maxCounter = 0;
            for (int tick = 0; tick < 450; tick++) {
                resetNativeCooldown(playerType, fixture, p, 7); set(player, playerType, "bu", Integer.valueOf(100));
                set(fixture, fixtureType, "clockNow", Long.valueOf(300000L + tick * 50L));
                invoke(update, farm);
                maxCounter = Math.max(maxCounter, field(farmType, counter).getInt(null));
                require(state.getInt(null) == 0 && field(playerType, armed).getByte(null) == -1
                                && field(entityType, "cO").get(player) == null
                                && field(entityType, "aZ").getInt(player) == x && field(entityType, "ba").getInt(player) == y,
                        "Tick " + tick + " stands at the spot with the legacy walking auto held");
            }
            int sent = packetCount() - before;
            require(sent >= 30 && sent <= 40 && maxCounter < 400,
                    "450 real Farm ticks over 22.5s keep sending every ~600ms without the village scroll; sent="
                            + sent + " maxCounter=" + maxCounter);
            require(field(playerType, "bi").getInt(player) == 120, "Native stand mode range is preserved");

            if (p.getProperty("special.farm.ready.method") != null) {
                // Guild member list and similar UI: another screen plus an open dialog.
                Class<?> uiOwner = loader.loadClass(required(p, "ui.currentScreen.owner").replace('/', '.'));
                Field screen = field(uiOwner, required(p, "ui.currentScreen.field"));
                Field dialog = field(loader.loadClass(required(p, "ui.activeDialog.owner").replace('/', '.')),
                        required(p, "ui.activeDialog.field"));
                Object screenBefore = screen.get(null), dialogBefore = dialog.get(null);
                try {
                    screen.set(null, null);
                    dialog.set(null, unsafeAllocate(dialog.getType()));
                    require(!Boolean.TRUE.equals(bridge.getClass().getMethod("ready").invoke(bridge)),
                            "The fixture really leaves the world screen with a dialog open");
                    before = packetCount(); maxCounter = 0;
                    for (int tick = 0; tick < 450; tick++) {
                        resetNativeCooldown(playerType, fixture, p, 7); set(player, playerType, "bu", Integer.valueOf(100));
                        set(fixture, fixtureType, "clockNow", Long.valueOf(400000L + tick * 50L));
                        invoke(update, farm);
                        maxCounter = Math.max(maxCounter, field(farmType, counter).getInt(null));
                        require(state.getInt(null) == 0, "Tick " + tick + " with UI open keeps Farm at its spot");
                    }
                    sent = packetCount() - before;
                    require(sent >= 30 && maxCounter < 400,
                            "With a UI screen/dialog open Farm Special keeps sending and never reaches the village scroll; sent="
                                    + sent + " maxCounter=" + maxCounter);
                } finally { screen.set(null, screenBefore); dialog.set(null, dialogBefore); }

            }

            if (guard) {
                // Every send refused (connection wait): Farm keeps its counter at 0 for 15s real
                // time, then hands the native 400-tick village scroll back.
                Field wait = field(loader.loadClass(required(p, "native.connection.wait.owner").replace('/', '.')),
                        required(p, "native.connection.wait.field"));
                Object waitBefore = wait.get(null);
                try {
                    wait.set(null, Boolean.TRUE);
                    before = packetCount();
                    long base = 600000L; int lastZero = -1, counterAfter = 0;
                    for (int tick = 0; tick < 380; tick++) {
                        resetNativeCooldown(playerType, fixture, p, 7); set(player, playerType, "bu", Integer.valueOf(100));
                        set(fixture, fixtureType, "clockNow", Long.valueOf(base + tick * 50L));
                        invoke(update, farm);
                        int value = field(farmType, counter).getInt(null);
                        if (value == 0) lastZero = tick; else counterAfter = value;
                        require(state.getInt(null) == 0, "Tick " + tick + " refused sends keep Farm at its spot");
                    }
                    require(packetCount() == before, "Refused sends emit nothing");
                    require(lastZero * 50 >= 14800 && lastZero * 50 < 15200 && counterAfter > 50,
                            "Refusals keep the counter at 0 for ~15s real time, then the native counter runs; lastZero="
                                    + (lastZero * 50) + "ms counter=" + counterAfter);
                } finally { wait.set(null, waitBefore); }
                setStatic(farmType, counter, Integer.valueOf(0));

                // A manual local cast pending at the spot is cancelled after 1.5s, then attacks resume.
                long base = 700000L;
                set(player, playerType, "cA", Integer.valueOf(7));
                for (int tick = 0; tick < 40 && field(entityType, "cA").getInt(player) != -1; tick++) {
                    set(fixture, fixtureType, "clockNow", Long.valueOf(base + tick * 50L));
                    invoke(update, farm);
                    require(tick * 50 >= 1500 || field(entityType, "cA").getInt(player) == 7,
                            "A pending cast is respected for 1.5s; tick " + tick);
                }
                require(field(entityType, "cA").getInt(player) == -1, "A cast pending 1.5s at the spot is cancelled");
                before = packetCount();
                resetNativeCooldown(playerType, fixture, p, 7); set(player, playerType, "bu", Integer.valueOf(100));
                set(fixture, fixtureType, "clockNow", Long.valueOf(base + 5000L));
                invoke(update, farm);
                require(packetCount() == before + 1, "Attacks resume after the stuck cast is cancelled");
            }

            set(player, playerType, "cG", Integer.valueOf(4));
            before = packetCount();
            invoke(update, farm);
            require(packetCount() == before && state.getInt(null) == 0,
                    "Death returns before the spot mode; native revive/return logic is untouched");
            set(player, playerType, "cG", Integer.valueOf(0));

            fields[12].set(null, Integer.valueOf(mapId + 1));
            invoke(update, farm);
            require(state.getInt(null) == 1 && packetCount() == before,
                    "Away from the saved map Farm starts its native return trip instead of attacking");
            fields[12].set(null, Integer.valueOf(mapId)); state.setInt(null, 0);

            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-116));
            require(Boolean.FALSE.equals(invoke(spot, null)), "Special OFF hands the spot back to native Farm");
            setStatic(playerType, armed, Byte.valueOf((byte)-1));
            setStatic(targetOwner, targetField, null);
            invoke(update, farm);
            require(field(playerType, armed).getByte(null) == 1,
                    "With Special OFF the native at-spot tick rearms its legacy auto as before");
            runtime.getMethod("handleAction", Integer.TYPE).invoke(null, Integer.valueOf(-116));
        } finally {
            for (int i = 0; i < statics.length; i++) fields[i].set(null, saved[i]);
            set(player, playerType, "cG", Integer.valueOf(0)); set(player, playerType, "cA", Integer.valueOf(-1));
            set(player, playerType, "cO", null);
        }
    }

    private static void verifyQuietVisuals(Object fixture, Class<?> fixtureType, Class<?> playerType,
            Class<?> entityType, Class<?> castType, Object player, Object bridge, Class<?> runtime,
            Properties p) throws Exception {
        Method idle = bridge.getClass().getMethod("quietLocalAvatar", entityType);
        Method quiet = bridge.getClass().getMethod("isQuietLocalAvatar", entityType);
        int x = field(entityType, "aZ").getInt(player), y = field(entityType, "ba").getInt(player);
        set(player, playerType, "cG", Integer.valueOf(2));set(player, playerType, "cA", Integer.valueOf(7));
        set(player, playerType, "cQ", Integer.valueOf(6));set(player, playerType, "bg", Integer.valueOf(x+100));
        set(player, playerType, "bh", Integer.valueOf(y+100));set(player, playerType, "cO", new short[]{1,2});
        require(Boolean.TRUE.equals(invoke(idle,null,player)), "Farm special forces the local visual state idle");
        require(field(entityType,"cG").getInt(player)==0 && field(entityType,"cA").getInt(player)==-1
                && field(entityType,"cQ").getInt(player)==0, "Attack state and animation frame are cleared");
        require(field(entityType,"bg").getInt(player)==x && field(entityType,"bh").getInt(player)==y
                && field(entityType,"cO").get(player)==null && field(entityType,"aZ").getInt(player)==x
                && field(entityType,"ba").getInt(player)==y, "Farm path is cleared without moving the character");
        int packets=packetCount();Object prior=call(fixture,"nativeCast",p);
        Object targets=field(castType,required(p,"cast.targets.field")).get(prior);
        Method init=castType.getDeclaredMethod(required(p,"cast.init.method"),entityType,targets.getClass(),Integer.TYPE,Byte.TYPE);
        init.setAccessible(true);set(player,playerType,"cG",Integer.valueOf(2));set(player,playerType,"cQ",Integer.valueOf(9));
        invoke(init,unsafeAllocate(castType),player,targets,Integer.valueOf(7),Byte.valueOf((byte)-1));
        require(field(entityType,"cG").getInt(player)==0 && field(entityType,"cQ").getInt(player)==0,
                "An untagged local receive-side cast echo cannot restart the animation");
        Object animation=field(entityType,"dh").get(player);
        Method animate=animation.getClass().getDeclaredMethod(required(p,"request.animation.method"),entityType);
        animate.setAccessible(true);set(player,playerType,"cG",Integer.valueOf(2));set(player,playerType,"cQ",Integer.valueOf(5));
        invoke(animate,animation,player);
        require(field(entityType,"cG").getInt(player)==0 && field(entityType,"cQ").getInt(player)==0
                && packetCount()==packets, "The real native animation driver stays idle and emits no duplicate packet");
        Object remote=unsafeAllocate(playerType);initializeActor(remote,playerType,p);
        require(Boolean.FALSE.equals(invoke(quiet,null,remote)), "Remote actors are outside the quiet-visual guard");
        set(player,playerType,"cG",Integer.valueOf(4));
        require(Boolean.FALSE.equals(invoke(idle,null,player)) && field(entityType,"cG").getInt(player)==4,
                "The quiet guard preserves the native death state");
        set(player,playerType,"cG",Integer.valueOf(0));runtime.getMethod("handleAction",Integer.TYPE).invoke(null,Integer.valueOf(-116));
        set(player,playerType,"cG",Integer.valueOf(2));set(player,playerType,"cQ",Integer.valueOf(5));
        require(Boolean.FALSE.equals(invoke(idle,null,player)) && field(entityType,"cG").getInt(player)==2
                && field(entityType,"cQ").getInt(player)==5, "Special OFF restores native animation state");
        set(player,playerType,"cG",Integer.valueOf(0));set(player,playerType,"cA",Integer.valueOf(-1));runtime.getMethod("handleAction",Integer.TYPE).invoke(null,Integer.valueOf(-116));
    }

    private static void verifyManualAndRemoteScope(Object fixture, Class<?> fixtureType,
            Class<?> playerType, Class<?> entityType, Class<?> castType, Object local,
            Object bridge, Properties p, int packetsBefore) throws Exception {
        require(!Boolean.TRUE.equals(bridge.getClass().getMethod("specialContext").invoke(bridge)),
                "Manual and remote casts have no active Farm special context after the chooser returns");
        Field targetsField = field(castType, required(p, "cast.targets.field"));
        Object prior = call(fixture, "nativeCast", p);
        Object targets = targetsField.get(prior);
        Method init = castType.getDeclaredMethod(required(p, "cast.init.method"), entityType,
                targets.getClass(), Integer.TYPE, Byte.TYPE);
        init.setAccessible(true);
        String sentName = required(p, "special.sent.field");

        Object manual = unsafeAllocate(castType);
        invoke(init, manual, local, targets, Integer.valueOf(0), Byte.valueOf((byte)0));
        require(!field(castType, sentName).getBoolean(manual),
                "A manual local cast outside Farm keeps its native delayed-send path");

        Object remote = unsafeAllocate(playerType);
        initializeActor(remote, playerType, p);
        Object remoteCast = unsafeAllocate(castType);
        invoke(init, remoteCast, remote, targets, Integer.valueOf(0), Byte.valueOf((byte)0));
        require(!field(castType, sentName).getBoolean(remoteCast),
                "A remote actor cannot inherit Farm's direct special-send context");
        require(packetCount() == packetsBefore,
                "Constructing manual and remote casts emits no direct special packet");
    }

    private static Object seedNativeSkill(ClassLoader loader, Class<?> playerType, Properties p) throws Exception {
        Class<?> test = Class.forName(AUTO_HOST);
        Class<?> loaderType = Class.forName(AUTO_HOST + "$NativeProbeLoader");
        Method method = test.getDeclaredMethod("seedNativeSkill", loaderType, Class.class, Properties.class);
        method.setAccessible(true);
        return invoke(method, null, loader, playerType, p);
    }

    private static void seedNativeFontMetrics(ClassLoader loader) throws Exception {
        Class<?> test = Class.forName(AUTO_HOST);
        Class<?> loaderType = Class.forName(AUTO_HOST + "$NativeProbeLoader");
        Method method = test.getDeclaredMethod("seedNativeFontMetrics", loaderType);
        method.setAccessible(true);
        invoke(method, null, loader);
    }

    private static void configureSpecialSkill(Class<?> fixtureType, Object fixture,
            Properties p, Class<?> playerType, Object definition) throws Exception {
        Class<?> test = Class.forName(SPECIAL_HOST);
        Method method = test.getDeclaredMethod("configureSpecialSkill", fixtureType,
                Properties.class, Class.class, Object.class, Integer.TYPE, Integer.TYPE);
        method.setAccessible(true);
        invoke(method, null, fixture, p, playerType, definition, Integer.valueOf(7), Integer.valueOf(10));
    }

    private static int addReadyZeroHotbarSlot(Object fixture, Class<?> playerType) throws Exception {
        String hotbarName = (String)call(fixture, "encoded", "lliliiil");
        Field hotbarField = field(playerType, hotbarName);
        Object hotbar = hotbarField.get(null);
        int activeRow = field(playerType, "d").getInt(null);
        Object row = Array.get(hotbar, activeRow);
        Class<?> cellType = row.getClass().getComponentType();
        int oldLength = Array.getLength(row);
        Object extended = Array.newInstance(cellType, oldLength + 1);
        System.arraycopy(row, 0, extended, 0, oldLength);
        Object zero = unsafeAllocate(cellType);
        set(zero, cellType, "b", Byte.valueOf((byte)0));
        set(zero, cellType, (String)call(fixture, "encoded", "lilllli"), Short.valueOf((short)0));
        Array.set(extended, oldLength, zero);
        Array.set(hotbar, activeRow, extended);
        hotbarField.set(null, hotbar);
        return oldLength;
    }

    private static void addReadyZeroSkillDefinition(Object fixture, ClassLoader loader,
            Object template, Properties p) throws Exception {
        Object zero = unsafeAllocate(template.getClass());
        set(zero, template.getClass(), "b", Integer.valueOf(0));
        set(zero, template.getClass(), "e", Byte.valueOf((byte)0));
        set(zero, template.getClass(), "g", Byte.valueOf((byte)0));
        String costsName = (String)call(fixture, "encoded", "lllillllil");
        Field costsField = field(template.getClass(), costsName);
        Class<?> costType = costsField.getType().isArray()
                ? costsField.getType().getComponentType() : costsField.getType();
        Object cost = unsafeAllocate(costType);
        set(cost, costType, (String)call(fixture, "encoded", "lilllli"), Short.valueOf((short)0));
        set(cost, costType, "j", Byte.valueOf((byte)4));
        set(cost, costType, (String)call(fixture, "encoded", "lllliiliil"), Short.valueOf((short)2000));
        if (costsField.getType().isArray()) {
            Object values = Array.newInstance(costType, 1);
            Array.set(values, 0, cost);
            set(zero, template.getClass(), costsName, values);
        } else set(zero, template.getClass(), costsName, cost);

        Class<?> registryOwner = loader.loadClass(((String)call(fixture, "encoded", "lillilll")).replace('/', '.'));
        Object registry = field(registryOwner, "a").get(null);
        Class<?> autoTest = Class.forName(AUTO_HOST);
        Method add = autoTest.getDeclaredMethod("addNativeListItem", Properties.class, Object.class, Object.class);
        add.setAccessible(true);
        invoke(add, null, p, registry, zero);
    }

    private static void resetNativeCooldown(Class<?> playerType, Object fixture,
            Properties p, int skill) throws Exception {
        String cooldownField = (String)call(fixture, "encoded", "lliillli");
        Object cooldowns = field(playerType, cooldownField).get(null);
        Object state = Array.get(cooldowns, skill);
        set(state, state.getClass(), "d", Boolean.FALSE);
        setNumber(field(state.getClass(), "b"), state, 0);
    }

    private static void setNativeRequestClock(ClassLoader loader, Properties p, long time, short interval)
            throws Exception {
        Class<?> test = Class.forName(AUTO_HOST);
        Method method = test.getDeclaredMethod("setNativeRequestClock", ClassLoader.class, Properties.class,
                Long.TYPE, Short.TYPE);
        method.setAccessible(true);
        invoke(method, null, loader, p, Long.valueOf(time), Short.valueOf(interval));
    }

    private static void initializeActor(Object actor, Class<?> playerType, Properties p) throws Exception {
        Class<?> test = Class.forName(AUTO_HOST);
        Method method = test.getDeclaredMethod("initializeNativeActorHooks", Object.class, Class.class,
                Properties.class);
        method.setAccessible(true);
        invoke(method, null, actor, playerType, p);
    }

    private static Object unsafeAllocate(Class<?> type) throws Exception {
        Class<?> test = Class.forName(AUTO_HOST);
        Method method = test.getDeclaredMethod("unsafeAllocate", Class.class);
        method.setAccessible(true);
        return invoke(method, null, type);
    }

    private static int packetCount() throws Exception {
        return ((Integer)host(AUTO_HOST, "attackPacketCount", new Class<?>[0])).intValue();
    }
    private static boolean packetHasOpcode(int opcode) throws Exception {
        return ((Boolean)host(AUTO_HOST, "packetHasOpcode", new Class<?>[]{Integer.TYPE},
                Integer.valueOf(opcode))).booleanValue();
    }
    private static String packetSnapshot() throws Exception {
        return (String)host(AUTO_HOST, "nativePacketSnapshot", new Class<?>[0]);
    }
    private static void clearPackets() throws Exception {
        Class<?> test = Class.forName(AUTO_HOST);
        Field packets = field(test, "nativePackets");
        synchronized (packets.get(null)) { ((java.util.List<?>)packets.get(null)).clear(); }
    }
    private static Object host(String owner, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> type = Class.forName(owner);
        Method method = type.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return invoke(method, null, args);
    }
    private static boolean bool(Class<?> owner, String name) throws Exception {
        return ((Boolean)owner.getMethod(name).invoke(null)).booleanValue();
    }

    private static Object get(Object target, String name) throws Exception { return field(target.getClass(), name).get(target); }
    private static Object call(Object target, String name, Object... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) types[i] = primitive(args[i]);
        Method method = null;
        for (Class<?> type = target.getClass(); type != null && method == null; type = type.getSuperclass())
            try { method = type.getDeclaredMethod(name, types); } catch (NoSuchMethodException ignored) { }
        if (method == null) throw new NoSuchMethodException(target.getClass().getName() + "." + name);
        method.setAccessible(true);
        return invoke(method, target, args);
    }
    private static Class<?> primitive(Object value) {
        if (value instanceof Integer) return Integer.TYPE; if (value instanceof Boolean) return Boolean.TYPE;
        if (value instanceof Byte) return Byte.TYPE; if (value instanceof Short) return Short.TYPE;
        if (value instanceof Long) return Long.TYPE; return value.getClass();
    }
    private static Field field(Class<?> type, String name) throws Exception {
        for (Class<?> owner = type; owner != null; owner = owner.getSuperclass()) try {
            Field result = owner.getDeclaredField(name); result.setAccessible(true); return result;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field result = field(owner, name);
        if (result.getType() == Boolean.TYPE) result.setBoolean(target, ((Boolean)value).booleanValue());
        else if (result.getType() == Byte.TYPE) result.setByte(target, ((Byte)value).byteValue());
        else if (result.getType() == Short.TYPE) result.setShort(target, ((Short)value).shortValue());
        else if (result.getType() == Integer.TYPE) result.setInt(target, ((Integer)value).intValue());
        else if (result.getType() == Long.TYPE) result.setLong(target, ((Long)value).longValue());
        else result.set(target, value);
    }
    private static void setStatic(Class<?> owner, String name, Object value) throws Exception { set(null, owner, name, value); }
    private static void setNumber(Field field, Object target, int value) throws Exception {
        if (field.getType() == Integer.TYPE) field.setInt(target, value);
        else if (field.getType() == Short.TYPE) field.setShort(target, (short)value);
        else if (field.getType() == Byte.TYPE) field.setByte(target, (byte)value);
        else if (field.getType() == Long.TYPE) field.setLong(target, value);
        else throw new AssertionError("Expected numeric field: " + field);
    }
    private static Object invoke(Method method, Object receiver, Object... args) throws Exception {
        try { return method.invoke(receiver, args); }
        catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception)cause;
            if (cause instanceof Error) throw (Error)cause;
            throw error;
        }
    }
    private static Properties load(File file) throws Exception {
        Properties p = new Properties(); FileInputStream in = new FileInputStream(file);
        try { p.load(in); } finally { in.close(); } return p;
    }
    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null) throw new AssertionError("Missing catalog property " + key);
        return value;
    }
    private static String hash(File file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder value = new StringBuilder();
        for (byte item : digest) value.append(String.format("%02X", item & 255));
        return value.toString();
    }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void require(boolean value, String message) { check(value, message); }
}
