package luna.features;

import luna.api.AutoCombatSnapshot;
import luna.api.BachHoAutomationBridge;
import luna.api.LunaClock;
import luna.core.MovementArbiter;

/** Native auto combat with an optional, persistent map/zone/pixel origin. */
public final class AutoCombatController {
    public static final int MENU = -90;
    public static final int TOGGLE = -91;
    public static final int SAVE_POSITION = -92;
    public static final int CLEAR_POSITION = -93;
    private final BachHoAutomationBridge bridge;
    private final LunaClock clock;
    private final MovementArbiter movement;
    private final AutoCombatSnapshot player = new AutoCombatSnapshot();
    // map, x, y, zone. A negative map means ordinary native combat.
    private final int[] position = new int[] {-1, 0, 0, 0};
    private boolean pendingMenu;
    private boolean returning;
    private boolean wasEnabled;
    private long lastMove;
    private long lastZone;
    private int observedMap = -1;
    private int observedZone = -1;
    private int settleTicks;

    public AutoCombatController(BachHoAutomationBridge bridge, LunaClock clock,
            MovementArbiter movement) {
        this.bridge = bridge;
        this.clock = clock;
        this.movement = movement;
        bridge.loadPosition(position);
    }

    public boolean handleMenuAction(int action) {
        if (action == MENU) { pendingMenu = true; return true; }
        if (action == TOGGLE) {
            boolean enabled = !bridge.isAutoAttackEnabled();
            bridge.setAutoAttackEnabled(enabled);
            returning = enabled && hasPosition();
            if (returning) bridge.stopWalking();
            if (!enabled) release();
            pendingMenu = true;
            return true;
        }
        if (action == SAVE_POSITION) {
            bridge.readCombat(player);
            if (player.ready) {
                position[0] = player.map; position[1] = player.x;
                position[2] = player.y; position[3] = player.zone;
                bridge.savePosition(position);
                returning = bridge.isAutoAttackEnabled();
                bridge.notice("Đã lưu tọa độ tự đánh.");
            } else bridge.notice("Vào bản đồ để lưu tọa độ.");
            pendingMenu = true;
            return true;
        }
        if (action == CLEAR_POSITION) {
            position[0] = -1;
            bridge.savePosition(position);
            release();
            returning = false;
            if (bridge.isAutoAttackEnabled()) bridge.setAutoAttackEnabled(true);
            bridge.notice("Đã xóa tọa độ. Tự đánh theo chế độ hiện tại.");
            pendingMenu = true;
            return true;
        }
        return false;
    }

    public void onLoginEntry() {
        returning = hasPosition();
        lastMove = 0L;
        lastZone = 0L;
        observedMap = -1;
        observedZone = -1;
    }

    public void tick() {
        if (pendingMenu) { pendingMenu = false; showMenu(); }
        boolean enabled = bridge.isAutoAttackEnabled();
        boolean rising = enabled && !wasEnabled;
        if (rising) returning = hasPosition();
        wasEnabled = enabled;
        if (!enabled || !hasPosition()) { returning = false; release(); return; }
        bridge.readCombat(player);
        if (!player.ready || bridge.routeOwnsMovement()) {
            movement.release(MovementArbiter.OWNER_COMBAT);
            return;
        }
        if (rising) bridge.stopWalking();
        if (player.map != observedMap || player.zone != observedZone) {
            observedMap = player.map;
            observedZone = player.zone;
            settleTicks = 10;
            lastZone = 0L;
            returning = true;
        }
        if (settleTicks > 0) {
            bridge.pauseCombat();
            settleTicks--;
            return;
        }
        long now = clock.nowMillis();
        if (player.map != position[0]) {
            returning = true;
            if (!takeMovement()) return;
            bridge.pauseCombat();
            bridge.returnToMap(position[0]);
            return;
        }
        if (player.zone != position[3]) {
            returning = true;
            if (!takeMovement()) return;
            bridge.pauseCombat();
            if (lastZone == 0L || now - lastZone >= 2500L) {
                bridge.changeZone(position[3]);
                lastZone = now;
            }
            return;
        }
        int distance = distance(player.x, player.y, position[1], position[2]);
        if (distance > 30) returning = true;
        if (returning && distance > 30) {
            if (!takeMovement()) return;
            bridge.pauseCombat();
            if (!player.moving && (lastMove == 0L || now - lastMove >= 500L)) {
                bridge.walkTo(position[1], position[2]);
                lastMove = now;
            }
            return;
        }
        if (returning) {
            release();
            returning = false;
            bridge.setAutoAttackEnabled(true);
        }
        bridge.holdPosition(position[1], position[2]);
        bridge.setCombatOrigin(position[1], position[2]);
    }

    /** Applied after the existing native target validator and dungeon range gate. */
    public boolean allowTarget(Object target) {
        if (!hasPosition() || bridge.routeOwnsMovement()) return true;
        if (!bridge.isAutoAttackEnabled()) return true;
        bridge.readCombat(player);
        if (!player.ready || returning || player.map != position[0]
                || player.zone != position[3]) return false;
        return true;
    }

    public boolean ownsMovement() {
        return hasPosition() && bridge.isAutoAttackEnabled()
                && !bridge.routeOwnsMovement();
    }

    public boolean shouldPauseNativeCombat() { return returning && ownsMovement(); }

    public boolean hasPosition() { return position[0] >= 0; }
    public int position(int index) { return position[index]; }

    private boolean takeMovement() { return movement.acquire(MovementArbiter.OWNER_COMBAT); }

    private void release() {
        if (movement.currentOwner() == MovementArbiter.OWNER_COMBAT) bridge.stopWalking();
        movement.release(MovementArbiter.OWNER_COMBAT);
    }

    private void showMenu() {
        String on = bridge.isAutoAttackEnabled() ? "Tự đánh: [ON]" : "Tự đánh: [OFF]";
        String at = "Lưu tọa độ [chưa lưu]";
        if (hasPosition()) {
            StringBuffer text = new StringBuffer("Lưu tọa độ [map ");
            text.append(position[0]).append(" / khu ").append(position[3] + 1);
            text.append(" / ").append(position[1] / 24).append(',').append(position[2] / 24).append(']');
            at = text.toString();
        }
        bridge.showMenu("AUTO TỰ ĐÁNH", new String[] {on, at, "Xóa tọa độ đã lưu"},
                new int[] {TOGGLE, SAVE_POSITION, CLEAR_POSITION});
    }

    private static int distance(int x, int y, int tx, int ty) {
        return Math.abs(x - tx) + Math.abs(y - ty);
    }
}
