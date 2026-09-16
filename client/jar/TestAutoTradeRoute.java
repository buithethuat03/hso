import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Doc-only. Kiem tra phan dinh tuyen cua HSOAutoTradeSupport (nextHop, mapIdOfName) -
 * day la logic thuan tuy, khong cham vao game, nen test duoc ngoai emulator.
 *
 * javac -encoding UTF-8 TestAutoTradeRoute.java
 * java  TestAutoTradeRoute [jar]
 */
public final class TestAutoTradeRoute {

    static Method nextHop;
    static Method mapIdOfName;
    static Method straightPath;
    static int failures;

    public static void main(String[] args) throws Exception {
        String jarPath = args.length > 0 ? args[0] : "HSOLUNA_TRUONGBK_AUTOTRADE.jar";
        URL[] urls = {new File(jarPath).toURI().toURL(), new File("microemulator.jar").toURI().toURL()};
        URLClassLoader cl = new URLClassLoader(urls, null);
        try {
            Class<?> c = Class.forName("HSOAutoTradeSupport", true, cl);
            nextHop = c.getDeclaredMethod("nextHop", int.class, int.class);
            nextHop.setAccessible(true);
            mapIdOfName = c.getDeclaredMethod("mapIdOfName", String.class);
            mapIdOfName.setAccessible(true);
            straightPath = c.getDeclaredMethod("straightPath",
                    int.class, int.class, int.class, int.class, int.class);
            straightPath.setAccessible(true);

            System.out.println("-- Ten map -> id --");
            expectName("Cổng địa ngục", 52);
            expectName("Thung Lũng Kỳ Bí", 8);
            expectName("Cổng vào Hạ giới", 45);
            expectName("Cánh Đồng Sói", 7);
            expectName("Thành Phố Kho Báu", 33);
            expectName("khong phai ten map nao ca", -1);

            System.out.println();
            System.out.println("-- Duong di 52 -> 8 (Cong dia nguc -> Thung Lung Ky Bi) --");
            int[] route = walk(52, 8);
            printRoute(route);
            expectRoute(route, new int[]{52, 45, 44, 43, 42, 22, 24, 23, 20, 34, 33, 25, 18, 7, 8});

            System.out.println();
            System.out.println("-- Duong ve 8 -> 52 --");
            int[] back = walk(8, 52);
            printRoute(back);
            expect(back[back.length - 1] == 52, "duong ve phai ket thuc o map 52");

            System.out.println();
            System.out.println("-- Vai truong hop bien --");
            expect(((Integer) nextHop.invoke(null, Integer.valueOf(52), Integer.valueOf(52))).intValue() == 52,
                    "cung map thi nextHop tra ve chinh no");
            expect(((Integer) nextHop.invoke(null, Integer.valueOf(1), Integer.valueOf(52))).intValue() >= 0,
                    "tu lang (map 1) phai tim duoc duong toi map 52");
            expect(((Integer) nextHop.invoke(null, Integer.valueOf(-5), Integer.valueOf(8))).intValue() == -1,
                    "map id am phai tra ve -1");
            expect(((Integer) nextHop.invoke(null, Integer.valueOf(7), Integer.valueOf(999))).intValue() == -1,
                    "map dich khong ton tai phai tra ve -1");

            System.out.println();
            System.out.println("-- Duong di thang (giai ma y het engine) --");
            checkPath(10, 10, 20, 10, 64, 10);      // ngang
            checkPath(10, 10, 10, 25, 64, 15);      // doc
            checkPath(10, 10, 30, 30, 64, 20);      // cheo
            checkPath(30, 30, 10, 10, 64, 20);      // cheo nguoc
            checkPath(10, 10, 100, 10, 3, 3);       // bi cat ngan
            expect(straightPath.invoke(null, i(10), i(10), i(10), i(10), i(64)) == null,
                    "dung dung o dich thi tra ve null");
            expect(straightPath.invoke(null, i(10), i(10), i(200), i(10), i(64)) == null,
                    "o ngoai 0..127 thi tra ve null (engine ep kieu byte)");

            System.out.println();
            System.out.println("-- Danh sach ngoc uu tien --");
            java.lang.reflect.Field gf = c.getDeclaredField("GEM_NAMES");
            gf.setAccessible(true);
            String[] gems = (String[]) gf.get(null);
            expect(gems.length >= 2, "GEM_NAMES co " + gems.length
                    + " loai (het loai dau thi con loai sau de mua tiep)");
            boolean lower = true;
            StringBuffer list = new StringBuffer();
            for (int k = 0; k < gems.length; k++) {
                if (!gems[k].equals(gems[k].toLowerCase())) lower = false;
                if (k > 0) list.append(" -> ");
                list.append(gems[k]);
            }
            expect(lower, "GEM_NAMES phai viet thuong het (" + list
                    + ") vi doi chieu voi ten hang va hop thoai da duoc ha ve chu thuong");
            java.lang.reflect.Field mf = c.getDeclaredField("BUY_OK_MARK");
            mf.setAccessible(true);
            String mark = (String) mf.get(null);
            expect(mark.equals(mark.toLowerCase()) && mark.length() > 0,
                    "BUY_OK_MARK = \"" + mark + "\" phai viet thuong (cung ly do tren)");

            System.out.println();
            System.out.println("-- Cua so cho sau khi doi map --");
            long settle = cl(c, "MAP_SETTLE_MS");
            long cowGone = cl(c, "COW_GONE_MS");
            long routeMiss = cl(c, "ROUTE_MISS_MS");
            expect(settle > 1000,
                    "MAP_SETTLE_MS = " + settle + "ms phai > 1000: qua cong xong, con bo chi"
                    + " duoc server gui lai o nhip pet ke tiep (MapService.java:215, moi 1 giay)");
            expect(cowGone > 1000,
                    "COW_GONE_MS = " + cowGone + "ms phai > 1000 (cung ly do tren)");
            expect(routeMiss > settle,
                    "ROUTE_MISS_MS = " + routeMiss + "ms phai > MAP_SETTLE_MS = " + settle
                    + "ms, khong thi bo cuoc truoc khi bang cong kip nap");
            expect(cowGone < cl(c, "COW_SEARCH_MS"),
                    "COW_GONE_MS = " + cowGone + "ms phai < COW_SEARCH_MS = "
                    + cl(c, "COW_SEARCH_MS") + "ms (phai ket luan mat bo truoc khi het gio tim)");

            System.out.println();
            System.out.println("-- Bang ten trang thai --");
            java.lang.reflect.Field lf = c.getDeclaredField("STATE_LABEL");
            lf.setAccessible(true);
            String[] labels = (String[]) lf.get(null);
            int maxState = 0;
            String[] stateNames = {"OFF", "GOTO_START", "SPAWN_PET", "TALK_DYLAN", "BUY",
                    "HAUL", "TALK_GRAHAM", "RETURN_HOME", "BOOST_PET", "WAIT_STOCK",
                    "DISMISS_COW"};
            for (int k = 0; k < stateNames.length; k++) {
                int v = ci(c, stateNames[k]);
                if (v > maxState) maxState = v;
            }
            expect(labels.length > maxState,
                    "STATE_LABEL co " + labels.length + " dong, trang thai lon nhat la " + maxState
                    + " (thieu la qua han se nem ArrayIndexOutOfBounds)");
            /*
             * Nhan buoc hien thang ra HUD cho nguoi choi doc, nen phai la tieng Viet CO DAU -
             * nguoi choi da bao viet khong dau kho nhin. Font cua client ve duoc dau (chinh
             * dong "Auto Di Buon: " da co dau), nen khong co ly do gi bo dau.
             */
            boolean accented = false;
            StringBuffer plain = new StringBuffer();
            for (int k = 1; k < labels.length; k++) {
                boolean hasAccent = false;
                for (int j = 0; j < labels[k].length(); j++) {
                    if (labels[k].charAt(j) > 127) hasAccent = true;
                }
                if (hasAccent) accented = true;
                else { if (plain.length() > 0) plain.append(", "); plain.append(labels[k]); }
            }
            expect(accented && plain.length() == 0,
                    "moi nhan trong STATE_LABEL phai la tieng Viet co dau"
                    + (plain.length() == 0 ? "" : " - con thieu dau: " + plain));

            System.out.println();
            System.out.println("-- Tranh cong khac tren duong di --");
            near = c.getDeclaredMethod("segmentNearPoint", int.class, int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class);
            near.setAccessible(true);
            int r = ci(c, "GATE_AVOID_PX");
            // cong nam giua duong di thang -> phai phat hien duoc
            expect(seg(0, 0, 400, 400, 200, 200, r), "cong nam ngay tren duong cheo");
            expect(seg(0, 0, 400, 0, 200, 30, r), "cong lech " + 30 + "px canh duong ngang");
            // cong o xa -> khong duoc bao dong
            expect(!seg(0, 0, 400, 0, 200, 300, r), "cong o xa thi khong tinh la chan duong");
            // buoc lay mau phai du min de khong nhay qua cong
            expect(seg(0, 0, 900, 0, 450, 0, r), "duong dai van phat hien duoc cong o giua");
            expect(seg(0, 0, 0, 900, 0, 450, r), "duong doc dai van phat hien duoc");
            // di mot truc: chan ngang roi chan doc phai tranh duoc cong nam tren duong cheo
            expect(!seg(0, 0, 400, 0, 200, 200, r) && !seg(400, 0, 400, 400, 200, 200, r),
                    "di ngang roi doc thi tranh duoc cong o giua duong cheo");

            System.out.println();
            System.out.println("-- Nhip dat bo (rang buoc voi server) --");
            int stepOk = ci(c, "PET_STEP_OK");
            int lost = ci(c, "PET_LOST_PX");
            int step = ci(c, "HAUL_STEP_PX");
            long tickMs = cl(c, "HAUL_TICK_MS");
            long pxPerSec = step * 1000L / tickMs;
            expect(pxPerSec < 85,
                    "toc do dat bo = " + pxPerSec + "px/giay, phai < 85"
                    + " (MapService.java:213 - moi nhip 1 giay bo chi theo neu con trong 85px)");
            expect(lost > step,
                    "PET_LOST_PX = " + lost + " phai > HAUL_STEP_PX = " + step
                    + " (khong thi buoc nao cung bi coi la bo tut lai)");
            expect(lost < 85, "PET_LOST_PX = " + lost + " phai < 85 de phat hien truoc khi bo dung im");
            long spawnStep = cl(c, "SPAWN_STEP_MS");
            long spawnWait = cl(c, "SPAWN_WAIT_MS");
            expect(spawnStep >= 500L && spawnStep <= 1000L,
                    "SPAWN_STEP_MS = " + spawnStep + "ms phai trong 500..1000: duoi 500 la bam"
                    + " truoc khi hop xac nhan cua server ve, tren 1000 la nhan vat troi them"
                    + " qua xa theo quan tinh trong luc dung cho");
            expect(spawnWait > spawnStep * 4,
                    "SPAWN_WAIT_MS = " + spawnWait + "ms phai rong rai hon nhieu: dung lai vat"
                    + " pham khi bo da duoc tao la HUY mat con do");
            expect(spawnWait * ci(c, "MAX_SPAWN_TRIES") < cl(c, "STATE_TIMEOUT_MS"),
                    "thu du " + ci(c, "MAX_SPAWN_TRIES") + " lan goi bo phai xong truoc khi het"
                    + " han cua buoc goi bo");

            int gateNear = ci(c, "GATE_NEAR_PX");
            int gatePet = ci(c, "GATE_PET_PX");
            expect(gatePet < 125,
                    "GATE_PET_PX = " + gatePet + " phai < 125 (Player.java:1209 - dieu kien keo bo"
                    + " qua cong, va 125 KHONG duoc nhan theo toc do pet)");
            expect(gatePet + step / TILE_PX * TILE_PX < 125,
                    "GATE_PET_PX + mot buoc luc tang toc = " + (gatePet + step / TILE_PX * TILE_PX)
                    + " phai < 125, khong thi chinh buoc cuoi vao cong lam bo rot lai");
            expect(gateNear > gatePet,
                    "GATE_NEAR_PX = " + gateNear + " phai > GATE_PET_PX = " + gatePet);
            expect(stepOk < lost,
                    "PET_STEP_OK = " + stepOk + " phai < PET_LOST_PX = " + lost
                    + " (cho tre khi don bo, tranh dao qua lai)");
            expect(pxPerSec < 125,
                    "luc cham cong bo trong " + pxPerSec + "px, phai < 125"
                    + " (Player.java:1209 - dieu kien keo bo qua map)");
            // Nguoi choi dang test nen tam de 1; doi lai 24 thi sua ca hai cho.
            expect(ci(c, "MAX_GEMS") == EXPECT_MAX_GEMS,
                    "MAX_GEMS = " + ci(c, "MAX_GEMS") + ", mong doi " + EXPECT_MAX_GEMS + ".");

            System.out.println();
            System.out.println("-- Tuy chon tang toc --");
            // Tat tang toc = di nua toc do; nhip nhanh van phai duoi nguong cua server.
            java.lang.reflect.Method ht = c.getDeclaredMethod("haulTiles", int.class);
            ht.setAccessible(true);
            java.lang.reflect.Field bf = c.getDeclaredField("boostOn");
            bf.setAccessible(true);
            expect(((Boolean) bf.get(null)).booleanValue(), "mac dinh phai la bat tang toc");
            int fast = ((Integer) ht.invoke(null, i(TILE_PX))).intValue();
            bf.set(null, Boolean.FALSE);
            int slow = ((Integer) ht.invoke(null, i(TILE_PX))).intValue();
            bf.set(null, Boolean.TRUE);
            expect(fast == step / TILE_PX,
                    "bat tang toc: " + fast + " o moi nhip, mong doi HAUL_STEP_PX/o = "
                    + (step / TILE_PX));
            expect(slow == fast / 2 && slow >= 1,
                    "tat tang toc phai di nua so o: " + slow + " o so voi " + fast + " o");
            expect(fast * TILE_PX * 1000L / tickMs < 85,
                    "toc do khi bat tang toc = " + (fast * TILE_PX * 1000L / tickMs)
                    + "px/giay, phai < 85");
            java.lang.reflect.Method pl = c.getDeclaredMethod("petLostPx");
            pl.setAccessible(true);
            int leashFast = ((Integer) pl.invoke(null)).intValue();
            bf.set(null, Boolean.FALSE);
            int leashSlow = ((Integer) pl.invoke(null)).intValue();
            bf.set(null, Boolean.TRUE);
            int lostFast = ci(c, "PET_LOST_BOOST_PX");
            expect(leashSlow == lost && leashFast == lostFast,
                    "nguong bo tut lai: tat tang toc " + leashSlow + ", bat " + leashFast
                    + " - phai la " + lost + " va " + lostFast);
            expect(leashFast > leashSlow,
                    "nguong luc tang toc (" + leashFast + ") phai rong hon luc thuong ("
                    + leashSlow + "): buoc dai gap doi thi khoang cach tuc thoi cung lon hon");
            expect(leashFast > fast * TILE_PX * 2,
                    "nguong luc tang toc (" + leashFast + ") phai bo xa hon hai buoc ("
                    + (fast * TILE_PX * 2) + "), khong thi van bao bo tut oan");
            expect(leashFast == 240,
                    "nguong luc tang toc = " + leashFast + ", nguoi choi chot 240px sau khi"
                    + " chay thu (160 -> 200 -> 240)");
            expect(leashFast < 85 * 4,
                    "nguong luc tang toc (" + leashFast + ") phai < 85*4 = ban kinh bam cua server"
                    + " khi pet duoc tang toc (Pet_di_buon.update_speed dat speed = 4)");
            /*
             * DINH CHINH: tren server dang chay, Tang toc song toi khi con bo bien mat chu
             * khong phai 10 giay, nen speed = 4 suot ca chang va ban kinh keo that la 340px.
             * Nguong 200 nam gon trong do.
             */
            expect(ci(c, "GATE_PET_PX") < 125,
                    "chot rieng o cong (" + ci(c, "GATE_PET_PX") + ") phai < 125 va KHONG duoc"
                    + " an theo nguong di duong (" + leashFast + ") - 125 khong nhan theo toc do");
            /*
             * Nhan dang con bo cua MINH. Loi da kiem chung: hai nguoi cung dat bo qua cong
             * mot luc, ca hai con deu chua kip mang ten, minh vo nham con cua nguoi ta roi
             * dung im ca dem. Hai loi thoat duoi day la thu chan no.
             */
            boolean hasDrop = false;
            try {
                c.getDeclaredMethod("dropCow", String.class);
                hasDrop = true;
            } catch (Throwable t) {
                hasDrop = false;
            }
            expect(hasDrop, "thieu dropCow(String) - khong co duong buong con bo vo nham");
            int maxFetch = ci(c, "MAX_FETCH_RUNS");
            expect(maxFetch >= 2,
                    "MAX_FETCH_RUNS = " + maxFetch + " qua nho: bo cua minh cung co luc phai"
                    + " don hai lan (qua cong hep, bi mob chan), buong som la mat bo oan");
            expect(maxFetch <= 5,
                    "MAX_FETCH_RUNS = " + maxFetch + " qua lon: moi lan don mat vai giay, de"
                    + " lau qua thi van coi nhu dung im mot doan dai truoc khi chiu buong");
            /*
             * KHONG DUOC HO RA "DAI CHET".
             *
             * Loi da kiem chung o ban v21: ham don bo dung lai khi con cach bo rejoinPx()
             * (100px luc tang toc), trong khi chot cong doi bo trong 70px. O dai 70..100px
             * thi chot cong bao "chua dat" con ham don bao "du gan roi" - khong ben nao
             * phat lenh di, ca nguoi lan bo dung im cho toi khi nguoi choi tu bam phim.
             *
             * Luat chung: nguong KET THUC luot don phai chat hon MOI nguong kich hoat don.
             */
            int done = ci(c, "PET_STEP_OK");
            expect(done < ci(c, "GATE_PET_PX"),
                    "ket thuc luot don o " + done + "px ma chot cong doi " + ci(c, "GATE_PET_PX")
                    + "px: dai " + done + ".." + ci(c, "GATE_PET_PX") + " khong ai phat lenh di"
                    + " -> ket han o cong");
            expect(done < ci(c, "PET_LOST_PX") && done < leashFast,
                    "ket thuc luot don o " + done + "px phai chat hon ca hai nguong bo tut ("
                    + ci(c, "PET_LOST_PX") + " / " + leashFast + ")");
            boolean goneWait = true;
            try {
                c.getDeclaredMethod("waitForCow", int.class);
                goneWait = false;
            } catch (Throwable t) {
                goneWait = true;
            }
            expect(goneWait, "van con waitForCow() - moi duong cho la mot duong return truoc"
                    + " cho phat lenh di, ghep lai thanh trang thai dung im");

            expect(maxFetch * tickMs * 20 < cl(c, "HAUL_TIMEOUT_MS"),
                    "dem du " + maxFetch + " lan don con lau hon ca han cua chang dat bo -"
                    + " han gio se no truoc, tuc chot nay khong bao gio co tac dung");

            expect(((Integer) ht.invoke(null, i(1000))).intValue() == 1,
                    "o to hon ca mot buoc thi van phai giao 1 o, khong duoc giao 0 (dung yen)");
            expect(ci(c, "ACT_BOOST") != ci(c, "ACT_TOGGLE")
                            && ci(c, "ACT_BOOST") != ci(c, "ACT_SUBMENU"),
                    "id hanh dong cua nut Tang toc bi trung voi nut khac");

            System.out.println();
            System.out.println("-- Tuy chon Noclip --");
            java.lang.reflect.Field nf = c.getDeclaredField("noclipOn");
            nf.setAccessible(true);
            expect(((Boolean) nf.get(null)).booleanValue(),
                    "mac dinh Noclip phai bat de giu hanh vi x3");
            expect(ci(c, "ACT_NOCLIP") != ci(c, "ACT_BOOST")
                            && ci(c, "ACT_NOCLIP") != ci(c, "ACT_TOGGLE")
                            && ci(c, "ACT_NOCLIP") != ci(c, "ACT_SUBMENU"),
                    "id hanh dong Noclip bi trung voi nut khac");
            expect(c.getDeclaredMethod("realPath", int.class, int.class, int.class,
                    int.class, int.class) != null,
                    "phai co nhanh realPath khi tat Noclip");
            Class<?> runtime = Class.forName("HSOSKYRuntimeSupport", true, cl);
            expect(runtime.getDeclaredMethod("setNoClipEnabled", boolean.class) != null,
                    "runtime phai cho phep bat/tat noclip that");
            boolean hasPlan = false;
            try {
                c.getDeclaredMethod("clearPlan");
                hasPlan = true;
            } catch (Throwable ex) {
                hasPlan = false;
            }
            expect(hasPlan, "thieu clearPlan() - khong co ke hoach duong di thi moi nhip lai"
                    + " tinh lai mot loi vong khac, nhan vat chay vong vo khi tat Noclip");
            int forceTiles = ci(c, "GATE_FORCE_TILES");
            expect(forceTiles >= 1,
                    "GATE_FORCE_TILES = " + forceTiles + " - phai ep buoc thang len o cong khi"
                    + " da ap sat, khong thi tat Noclip la khong bao gio vao duoc cong");
            expect(forceTiles * TILE_PX <= ci(c, "ARRIVE_PX") + TILE_PX * 2,
                    "ep buoc thang tu " + (forceTiles * TILE_PX) + "px la qua xa - doan di"
                    + " thang khi da tat Noclip phai ngan, chi du de dat chan len o cong");
            boolean hasForce = false;
            try {
                for (java.lang.reflect.Method mm : c.getDeclaredMethods()) {
                    if ("forceStep".equals(mm.getName())) hasForce = true;
                }
            } catch (Throwable ex) {
                hasForce = false;
            }
            expect(hasForce, "thieu forceStep() - mieng da thieu qua ba ban: ap sat cong thi"
                    + " phai giao duong mot o thay vi tim duong");
            expect(has(c, "gridPath"), "thieu gridPath() - mat tang tu tim duong tren ban"
                    + " do o that, map lon se lai khong tim duoc duong khi tat Noclip");
            expect(has(c, "walkableTile"), "thieu walkableTile() - khong doc duoc dia hinh that");
            expect(has(c, "nearestOnPath"), "thieu nearestOnPath() - bam ke hoach theo khop"
                    + " tuyet doi thi chi lech nua o la vut ca ke hoach va quay dau");
            int engineMax = ci(c, "ENGINE_PATH_MAX");
            expect(engineMax > 0 && engineMax <= 126,
                    "ENGINE_PATH_MAX = " + engineMax + " - duong engine dai hon 126 o thi"
                    + " khong tin duoc nua (GhostMod dat dung con so nay)");
            int approach = ci(c, "GATE_APPROACH_TILES");
            expect(approach >= 1 && approach <= 8,
                    "GATE_APPROACH_TILES = " + approach + " - o dich bi chan thi nhan o gan"
                    + " nhat trong ban kinh nay; 0 la khong bao gio toi noi, qua lon la dung"
                    + " cach dich toi may chuc pixel");
            expect(ci(c, "PATH_BUDGET") == 5000,
                    "PATH_BUDGET = " + ci(c, "PATH_BUDGET") + ", phai la 5000 - dung con so"
                    + " engine goc truyen cho findPath o ca ba cho no goi. De nho hon thi map"
                    + " nao duong dai se khong tim ra duong, va sau "
                    + ci(c, "MAX_PATH_FAILS") + " lan that bai la auto tu tat");

            System.out.println();
            System.out.println("-- Auto Nga 4 --");
            int a1 = ci(c, "ACT_N4_SUBMENU");
            int a2 = ci(c, "ACT_N4_TOGGLE");
            expect(a1 != ci(c, "ACT_SUBMENU") && a1 != ci(c, "ACT_TOGGLE")
                            && a1 != ci(c, "ACT_BOOST") && a1 != ci(c, "ACT_NOCLIP")
                            && a2 != ci(c, "ACT_SUBMENU") && a2 != ci(c, "ACT_TOGGLE")
                            && a2 != ci(c, "ACT_BOOST") && a2 != ci(c, "ACT_NOCLIP")
                            && a1 != a2,
                    "action id cua Auto Nga 4 trung voi Auto Di Buon - bam mot muc se chay"
                    + " nham tinh nang kia");
            expect(has(c, "n4Tick") && has(c, "n4StartAttack") && has(c, "n4OnNpcMenu")
                            && has(c, "n4RockX") && has(c, "n4RockY") && has(c, "n4Stop"),
                    "thieu mot trong cac ham cua Auto Nga 4");
            expect(has(c, "n4NearestMob") && has(c, "n4ReadGate"),
                    "thieu buoc bam theo quai / doc cua quai ra - dung yen giua thien thach"
                    + " thi Auto Tu Danh chon duoc muc tieu nhung khong voi toi");
            int edge = ci(c, "N4_GATE_EDGE_PX");
            expect(edge > 0 && edge < 400,
                    "N4_GATE_EDGE_PX = " + edge + " - cua quai ra cach vien ban do ngan nay;"
                    + " 0 la dung ra ngoai ban do, qua lon la dung khong toi cua");
            expect(ci(c, "N4_MELEE_PX") > 0 && ci(c, "N4_MELEE_PX") <= 60,
                    "N4_MELEE_PX phai du sat de engine danh toi con quai");
            expect(!has(c, "n4RockEntity"),
                    "support con n4RockEntity() - cach di tim thien thach bang thuc the da"
                    + " hong khi choi that, thien thach nam chinh giua ban do");
            int talkFails = ci(c, "MAX_N4_TALK_FAILS");
            expect(talkFails >= 2 && talkFails <= 10,
                    "MAX_N4_TALK_FAILS = " + talkFails + " - 1 la mot goi ve cham cung bi coi"
                    + " la het ve, qua nhieu la bam mai vao mat nguoi choi");
            expect(ci(c, "N4_NPC_PX") > 0 && ci(c, "N4_NPC_PX") <= 120,
                    "N4_NPC_PX phai la mot khoang cach bat chuyen hop ly");
            expect(cl(c, "N4_FIGHT_TIMEOUT_MS") >= 900000L,
                    "han o trong pho ban phai >= 15 phut - 20 hiep quai khong nhanh");

            System.out.println("-- Het hang / cho NPC nhap hang --");
            expect(gems.length == 3 && "kim cương".equals(gems[0]) && "ruby".equals(gems[1])
                            && "saphia".equals(gems[2]),
                    "thu tu uu tien phai la kim cuong > ruby > saphia");
            java.lang.reflect.Method so = c.getDeclaredMethod("isSoldOut", String.class);
            so.setAccessible(true);
            expect(((Boolean) so.invoke(null, "vật phẩm hiện tại đã bán hết")).booleanValue(),
                    "phai nhan ra dong \"Vat pham hien tai da ban het\"");
            expect(!((Boolean) so.invoke(null, "đã mua 1 kim cương")).booleanValue(),
                    "dong mua thanh cong khong duoc coi la het hang");
            expect(!((Boolean) so.invoke(null, (String) null)).booleanValue(),
                    "khong co dong thong bao nao thi khong coi la het hang");
            int noReply = ci(c, "MAX_NO_REPLY");
            expect(noReply >= 1 && noReply <= 3,
                    "MAX_NO_REPLY = " + noReply + " - cho qua nhieu lan khong hoi am la spam mua,"
                    + " cho 1 lan la mot goi ve cham bi tinh oan");
            expect(noReply * cl(c, "BUY_INTERVAL_MS") * gems.length < cl(c, "STATE_TIMEOUT_MS"),
                    "bo cuoc ca 3 loai phai xong truoc khi het han cua buoc mua");

            long poll = cl(c, "STOCK_POLL_MS");
            long resummon = cl(c, "COW_RESUMMON_AGE_MS");
            expect(poll >= 10000L && poll <= 30000L,
                    "STOCK_POLL_MS = " + poll + "ms - nguoi choi yeu cau 15 giay mot lan");
            expect(resummon < 1800000L,
                    "COW_RESUMMON_AGE_MS = " + resummon + "ms phai < 30 phut (tuoi tho con bo),"
                    + " khong thi bo bien mat truoc khi kip goi lai");
            expect(resummon + 420000L < 1800000L,
                    "sau khi cho toi " + resummon + "ms van phai con du ~7 phut de dat bo di ban");

            System.out.println();
            System.out.println("-- Mat ket noi --");
            java.lang.reflect.Method link = c.getDeclaredMethod("isLinkDialog", String.class);
            link.setAccessible(true);
            expect(((Boolean) link.invoke(null, "bạn bị mất kết nối với máy chủ.")).booleanValue(),
                    "phai nhan ra hop \"Ban bi mat ket noi voi may chu.\"");
            expect(((Boolean) link.invoke(null, "đang kết nối lại. vui lòng chờ.")).booleanValue(),
                    "phai nhan ra hop \"Dang ket noi lai. Vui long cho.\"");
            expect(!((Boolean) link.invoke(null, "đã mua 1 kim cương")).booleanValue(),
                    "hop mua hang khong duoc coi la hop he thong");
            expect(!((Boolean) link.invoke(null, (String) null)).booleanValue(),
                    "khong doc duoc noi dung thi khong coi la hop he thong");

            System.out.println();
            if (failures == 0) {
                System.out.println("OK - dinh tuyen dung.");
            } else {
                System.out.println("CO " + failures + " kiem tra that bai.");
                System.exit(1);
            }
        } finally {
            cl.close();
        }
    }

    /**
     * Kiem tra mot duong di thang: phan tu 0 phai la o dang dung, moi buoc dich dung 1 o,
     * va giai ma nguoc bang dung hai phep ep kieu byte ma engine dung.
     */
    static void checkPath(int fx, int fy, int tx, int ty, int max, int wantSteps) throws Exception {
        short[] p = (short[]) straightPath.invoke(null, i(fx), i(fy), i(tx), i(ty), i(max));
        if (p == null) { expect(false, "duong di " + fx + "," + fy + " -> " + tx + "," + ty + " bi null"); return; }
        boolean ok = p.length == wantSteps + 1;
        int px = (byte) (p[0] >> 8);
        int py = (byte) p[0];
        ok = ok && px == fx && py == fy;
        int prevX = px, prevY = py;
        for (int k = 1; k < p.length; k++) {
            int x = (byte) (p[k] >> 8);
            int y = (byte) p[k];
            if (Math.abs(x - prevX) > 1 || Math.abs(y - prevY) > 1) ok = false;   // phai lien o
            if (x == prevX && y == prevY) ok = false;                              // khong dam chan
            prevX = x; prevY = y;
        }
        if (max >= Math.max(Math.abs(tx - fx), Math.abs(ty - fy))) {
            ok = ok && prevX == tx && prevY == ty;                                 // toi dung dich
        }
        expect(ok, fx + "," + fy + " -> " + tx + "," + ty + " (max " + max + "): "
                + p.length + " o, ket thuc " + prevX + "," + prevY);
    }

    /** So vien mua moi chuyen dang dat trong support class. */
    static final int EXPECT_MAX_GEMS = 24;

    /** Kich thuoc o cua TileMap trong jar (public static int = 24). */
    static final int TILE_PX = 24;

    static Integer i(int v) { return Integer.valueOf(v); }


    static Method near;

    /** Goi segmentNearPoint voi doan thang (x0,y0)->(x1,y1), lay mau nhu code that. */
    static boolean seg(int x0, int y0, int x1, int y1, int wx, int wy, int r) throws Exception {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int len = Math.abs(dx) > Math.abs(dy) ? Math.abs(dx) : Math.abs(dy);
        int steps = len / 16 + 1;
        return ((Boolean) near.invoke(null, i(x0), i(y0), i(dx), i(dy), i(steps),
                i(wx), i(wy), i(r))).booleanValue();
    }

    /** Doc mot hang so long trong HSOAutoTradeSupport. */
    static long cl(Class<?> c, String name) throws Exception {
        java.lang.reflect.Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.getLong(null);
    }

    /** Doc mot hang so int trong HSOAutoTradeSupport. */
    static boolean has(Class<?> c, String name) {
        for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
            if (m.getName().equals(name)) return true;
        }
        return false;
    }

    static int ci(Class<?> c, String name) throws Exception {
        java.lang.reflect.Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.getInt(null);
    }

    /** Lan theo nextHop tung buoc mot cho toi khi den noi (toi da 40 chang). */
    static int[] walk(int from, int to) throws Exception {
        int[] buf = new int[64];
        int n = 0;
        int cur = from;
        buf[n++] = cur;
        while (cur != to && n < 40) {
            int hop = ((Integer) nextHop.invoke(null, Integer.valueOf(cur), Integer.valueOf(to))).intValue();
            if (hop < 0) break;
            cur = hop;
            buf[n++] = cur;
        }
        int[] out = new int[n];
        System.arraycopy(buf, 0, out, 0, n);
        return out;
    }

    static void printRoute(int[] r) {
        StringBuffer sb = new StringBuffer("   ");
        for (int i = 0; i < r.length; i++) {
            if (i > 0) sb.append(" -> ");
            sb.append(r[i]);
        }
        sb.append("   (").append(r.length - 1).append(" lan chuyen map)");
        System.out.println(sb.toString());
    }

    static void expectRoute(int[] got, int[] want) {
        boolean same = got.length == want.length;
        for (int i = 0; same && i < got.length; i++) if (got[i] != want[i]) same = false;
        expect(same, "duong di phai khop tuyen mong doi");
    }

    static void expectName(String name, int want) throws Exception {
        int got = ((Integer) mapIdOfName.invoke(null, name)).intValue();
        expect(got == want, "\"" + name + "\" -> " + got + " (mong doi " + want + ")");
    }

    static void expect(boolean cond, String what) {
        System.out.println((cond ? "   OK  " : "   SAI ") + what);
        if (!cond) failures++;
    }
}
