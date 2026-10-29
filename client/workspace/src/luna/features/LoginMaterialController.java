package luna.features;

import luna.api.BachHoAutomationBridge;
import luna.api.LunaClock;

/** One material-setting transaction per login, with no frame sleeps or blind toggles. */
public final class LoginMaterialController {
    private final BachHoAutomationBridge bridge;
    private final LunaClock clock;
    private int phase;
    private int requests;
    private long due;
    private long confirmationUntil;

    public LoginMaterialController(BachHoAutomationBridge bridge, LunaClock clock) {
        this.bridge = bridge;
        this.clock = clock;
    }

    public void onLoginEntry() {
        phase = 1;
        requests = 0;
        due = clock.nowMillis() + 5000L;
    }

    public void tick() {
        if (phase == 3) {
            if (bridge.finishMaterialDialog() || clock.nowMillis() >= confirmationUntil) phase = 0;
            return;
        }
        if (phase == 0 || !bridge.isWorldReady() || clock.nowMillis() < due) return;
        if (phase == 1) {
            bridge.openMaterialMenu();
            requests++;
            phase = 2;
            due = clock.nowMillis() + 2500L;
            return;
        }
        String action = bridge.whiteMedalAction();
        if (action == null || action.trim().length() == 0) {
            if (requests < 2) {
                phase = 1;
                due = clock.nowMillis() + 2500L;
            } else {
                phase = 0;
                bridge.notice("Không đọc được trạng thái rớt mề đay trắng.");
            }
            return;
        }
        String normalized = action.trim().toLowerCase();
        boolean closing = normalized.charAt(0) != 'm';
        if (closing) bridge.closeWhiteMedalDrop();
        bridge.hideMaterialMenu();
        phase = closing ? 3 : 0;
        confirmationUntil = clock.nowMillis() + 3000L;
    }

    public boolean isPending() { return phase != 0; }
}
