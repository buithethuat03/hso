import luna.features.HungDungeonPositionController;

/** Deterministic parser, gate cooldown, reset, and fixed tile checks. */
public final class HungDungeonPositionHostTest {
    private static int checks;

    public static void main(String[] args) {
        westAndNorthShareStandTile();
        gateAndCooldown();
        multiDirectionNotices();
        sessionReset();
        System.out.println("HungDungeonPositionHostTest PASS checks=" + checks);
    }

    private static void westAndNorthShareStandTile() {
        HungDungeonPositionController controller = new HungDungeonPositionController();
        check(!controller.hasTarget(), "no fallback aim before a direction arrives");
        check(!controller.notify("Quái sẽ xuất hiện phía Nam.", 48, true, 1000L),
                "compass text without gate marker is ignored");
        check(controller.notify(gate("Tây"), 48, true, 1000L), "west notice selects a tile");
        check(controller.targetTileX() == 20 && controller.targetTileY() == 20,
                "west maps to tile 20,20");
        check(controller.targetX() == 492 && controller.targetY() == 492,
                "west target uses tile center pixels");
        check(!controller.notify(gate("Bắc"), 48, true, 1200L),
                "north shares west stand tile without a new route");
        check(controller.targetX() == 492 && controller.targetY() == 492,
                "same stand tile remains selected");
    }

    private static void gateAndCooldown() {
        HungDungeonPositionController controller = new HungDungeonPositionController();
        check(!controller.notify("Từ hướng phía Đông, chuẩn bị.", 48, true, 1000L),
                "notice without cửa is ignored");
        check(!controller.notify("Chat: qua cửa phía Bắc, Tây nhé.", 48, true, 1000L),
                "chat mentioning a door and directions is ignored");
        check(!controller.notify(gate("unknown"), 48, true, 1000L),
                "gate notice without a cardinal direction is ignored");
        check(controller.notify(gate("Tây"), 48, true, 1000L), "first direction is accepted");
        check(!controller.notify(gate("Đông"), 48, true, 2999L),
                "different direction inside 2000ms gate is ignored");
        check(controller.targetTileX() == 20 && controller.targetTileY() == 20,
                "ignored direction does not replace the current aim");
        check(controller.notify(gate("Đông"), 48, true, 3000L),
                "different direction is accepted at the 2000ms boundary");
        check(controller.targetTileX() == 33 && controller.targetTileY() == 20,
                "east maps to tile 33,20");
        check(controller.targetX() == 804 && controller.targetY() == 492,
                "east target uses tile center pixels");
        check(controller.notify(gate("Nam"), 48, true, 5000L), "south is accepted after gate");
        check(controller.targetTileX() == 19 && controller.targetTileY() == 33,
                "south maps to tile 19,33");
        check(controller.targetX() == 468 && controller.targetY() == 804,
                "south target uses tile center pixels");
        check(!controller.notify(gate("NORTH"), 48, true, 8000L),
                "unsupported English direction is ignored");
    }

    private static void multiDirectionNotices() {
        HungDungeonPositionController controller = new HungDungeonPositionController();
        check(controller.notify(gate("Bắc, Tây"), 48, true, 1000L),
                "two distinct directions select the shared stand tile");
        check(controller.direction() == HungDungeonPositionController.MULTIPLE,
                "two directions take priority over individual directions");
        check(controller.targetTileX() == 27 && controller.targetTileY() == 26,
                "two directions map to tile 27,26");
        check(controller.targetX() == 660 && controller.targetY() == 636,
                "multiple-direction target uses tile center pixels");

        controller = new HungDungeonPositionController();
        check(controller.notify(gate("TÂY, ĐÔNG, BẮC, NAM"), 48, true, 1000L),
                "uppercase four-direction notice is recognized");
        check(controller.direction() == HungDungeonPositionController.MULTIPLE
                && controller.targetTileX() == 27 && controller.targetTileY() == 26,
                "all four distinct directions use the shared tile");

        controller = new HungDungeonPositionController();
        check(controller.notify(gate("Bắc, Bắc"), 48, true, 1000L),
                "repeating one direction remains a single direction");
        check(controller.direction() == HungDungeonPositionController.NORTH
                && controller.targetTileX() == 20 && controller.targetTileY() == 20,
                "repeated north does not select the multiple-direction tile");
        check(!controller.notify(gate("BẮC, BẮC"), 48, true, 1200L),
                "case-insensitive repeated notice does not request another path");
    }

    private static void sessionReset() {
        HungDungeonPositionController controller = new HungDungeonPositionController();
        check(controller.notify(gate("Nam"), 48, true, 1000L), "session accepts its first aim");
        check(!controller.notify(gate("Đông"), 47, true, 5000L), "other map is ignored");
        check(!controller.isActive() && !controller.hasTarget(), "leaving map clears the session");
        check(controller.notify(gate("Đông"), 48, true, 5100L),
                "re-entry accepts a new direction without stale cooldown");
        check(!controller.notify(gate("Nam"), 48, false, 9000L), "disabled owner ignores notices");
        check(!controller.isActive() && !controller.hasTarget(), "disabled owner clears the session");
        check(controller.onNotice(gate("Tây"), 9100L), "raw notice ingress can start an active session");
        controller.enter();
        check(controller.hasTarget(), "idempotent enter preserves the current wave aim");
        check(!controller.onNotice(gate("Đông"), 9200L), "raw notice ingress observes the gate cooldown");
        controller.enterDungeon();
        check(controller.isActive() && !controller.hasTarget(), "explicit dungeon entry starts clean");
        check(controller.notify(gate("Bắc"), 48, true, 10000L), "fresh entry accepts first wave direction");
    }

    private static String gate(String direction) {
        return "Bầy quái vật sẽ xuất hiện ở cửa phía " + direction + " sau 15 giây nữa...";
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
