import javax.microedition.lcdui.Image;

/**
 * Automatic ghost captcha answer, ported from tools/legacy/ghostmod/GhostMod.java.
 * The captcha is five digits drawn from 1/3/5/7/9; answer index 0..4 selects one digit.
 * sendAnswer() and captchaPending() are placeholders; HungBySonGhostPatcher rewrites
 * their bodies to call the client's answer sender and read the captcha image field.
 */
public final class HungBySonGhostSupport implements Runnable {
    private static final int DIGITS = 5;
    private static final long START_DELAY_MS = 1500L;
    private static final long KEY_DELAY_MS = 1500L;
    private static final long RETRY_MS = 20000L;
    private static final long IDLE_EXIT_MS = 15000L;
    private static final long POLL_MS = 250L;
    private static final int MAX_ATTEMPTS = 10;

    private static final Object LOCK = new Object();
    private static int[] pendingDigits;
    private static int pendingSeq;
    private static boolean running;
    private static boolean clearedSinceAnswer;

    private HungBySonGhostSupport() {
    }

    /** Called on the network thread right after the client stores a new captcha image. */
    public static void onCaptcha(Image image) {
        try {
            int[] digits = ocr(image);
            if (digits == null) {
                return;
            }
            synchronized (LOCK) {
                if (running && !clearedSinceAnswer && sameDigits(digits, pendingDigits)) {
                    return; // repeated delivery of the captcha being answered
                }
                pendingDigits = digits;
                pendingSeq++;
                if (running) {
                    return;
                }
                running = true;
                clearedSinceAnswer = false;
            }
            new Thread(new HungBySonGhostSupport()).start();
        } catch (Throwable ignored) {
            synchronized (LOCK) {
                running = false;
            }
        }
    }

    public void run() {
        try {
            Thread.sleep(START_DELAY_MS);
            int answeredSeq = -1;
            int attempts = 0;
            long nextRetry = 0L;
            long idleSince = -1L;
            while (true) {
                int[] digits;
                int seq;
                synchronized (LOCK) {
                    digits = pendingDigits;
                    seq = pendingSeq;
                }
                long now = System.currentTimeMillis();
                if (!captchaPending()) {
                    // Ghost died, or a wrong answer cleared the image; the client re-requests it.
                    synchronized (LOCK) {
                        clearedSinceAnswer = true;
                    }
                    if (idleSince < 0L) {
                        idleSince = now;
                    } else if (now - idleSince >= IDLE_EXIT_MS) {
                        synchronized (LOCK) {
                            if (seq == pendingSeq) {
                                running = false;
                                return;
                            }
                        }
                        idleSince = -1L;
                    }
                } else {
                    idleSince = -1L;
                    if (seq != answeredSeq || now >= nextRetry) {
                        attempts = seq != answeredSeq ? 1 : attempts + 1;
                        if (attempts > MAX_ATTEMPTS) {
                            synchronized (LOCK) {
                                if (seq == pendingSeq) {
                                    running = false;
                                    return;
                                }
                            }
                            continue;
                        }
                        answeredSeq = seq;
                        synchronized (LOCK) {
                            clearedSinceAnswer = false;
                        }
                        pressAnswer(digits);
                        nextRetry = System.currentTimeMillis() + RETRY_MS;
                    }
                }
                Thread.sleep(POLL_MS);
            }
        } catch (Throwable ignored) {
            synchronized (LOCK) {
                running = false;
            }
        }
    }

    private static void pressAnswer(int[] digits) throws InterruptedException {
        for (int i = 0; i < DIGITS; i++) {
            int index = answerIndex(digits[i]);
            if (index >= 0) {
                sendAnswer(index);
                Thread.sleep(KEY_DELAY_MS);
            }
        }
    }

    private static boolean sameDigits(int[] a, int[] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return false;
        }
        return true;
    }

    private static int answerIndex(int digit) {
        switch (digit) {
            case 1: return 0;
            case 3: return 1;
            case 5: return 2;
            case 7: return 3;
            case 9: return 4;
            default: return -1;
        }
    }

    /** Placeholder: rewritten to sender.getInstance().answer((byte) index). */
    private static void sendAnswer(int index) {
    }

    /** Placeholder: rewritten to (captcha image field != null). */
    private static boolean captchaPending() {
        return false;
    }

    private static int[] ocr(Image img) {
        if (img == null) {
            return null;
        }
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int[] pixels = new int[w * h];
        img.getRGB(pixels, 0, w, 0, 0, w, h);

        int minB = 255;
        int maxB = 0;
        for (int i = 0; i < pixels.length; i++) {
            if (((pixels[i] >> 24) & 0xFF) < 20) continue;
            int br = brightness(pixels[i]);
            if (br < minB) minB = br;
            if (br > maxB) maxB = br;
        }
        int threshold = minB + ((maxB - minB) * 7 / 10);

        byte[] bw = new byte[w * h];
        int topRow = -1;
        int botRow = -1;
        for (int y = 0; y < h; y++) {
            boolean hasPixel = false;
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (((pixels[i] >> 24) & 0xFF) >= 20 && brightness(pixels[i]) >= threshold) {
                    bw[i] = 1;
                    hasPixel = true;
                }
            }
            if (hasPixel) {
                if (topRow == -1) topRow = y;
                botRow = y;
            }
        }
        if (topRow == -1) return null;

        int[] colSum = new int[w];
        int minCol = -1;
        int maxCol = -1;
        for (int x = 0; x < w; x++) {
            for (int y = topRow; y <= botRow; y++) {
                colSum[x] += bw[y * w + x];
            }
            if (colSum[x] > 0) {
                if (minCol == -1) minCol = x;
                maxCol = x;
            }
        }
        if (minCol == -1) return null;

        int[][] bounds = new int[DIGITS][2];
        int count = 0;
        boolean inChar = false;
        for (int x = minCol; x <= maxCol; x++) {
            if (colSum[x] > 0 && !inChar) {
                if (count < DIGITS) bounds[count][0] = x;
                inChar = true;
            } else if (colSum[x] == 0 && inChar) {
                if (count < DIGITS) bounds[count][1] = x;
                count++;
                inChar = false;
            }
        }
        if (inChar && count < DIGITS) {
            bounds[count][1] = maxCol + 1;
            count++;
        }
        // Touching digits: split the text block into five equal columns.
        if (count != DIGITS) {
            int charW = (maxCol - minCol + 1) / DIGITS;
            for (int i = 0; i < DIGITS; i++) {
                bounds[i][0] = minCol + i * charW;
                bounds[i][1] = i == DIGITS - 1 ? maxCol + 1 : minCol + (i + 1) * charW;
            }
        }

        int[] result = new int[DIGITS];
        for (int i = 0; i < DIGITS; i++) {
            int first = 0;
            int last = 0;
            for (int x = bounds[i][0]; x < bounds[i][1]; x++) {
                if (bw[topRow * w + x] == 1) first++;
                if (bw[botRow * w + x] == 1) last++;
            }
            result[i] = identifyDigit(first * 2, last * 2);
        }
        return result;
    }

    private static int brightness(int argb) {
        return (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3;
    }

    private static int identifyDigit(int top, int bot) {
        // Top/bottom row widths: 1=(4,4) 3=(8,8) 5=(10,8) 7=(12,4) 9=(8,6)
        if (top == 4 && bot == 4) return 1;
        if (top == 8 && bot == 8) return 3;
        if (top == 10 && bot == 8) return 5;
        if (top == 12 && bot == 4) return 7;
        if (top == 8 && bot == 6) return 9;
        if (bot == 4) return top > 8 ? 7 : 1;
        if (bot == 8) return top > 9 ? 5 : 3;
        if (bot == 6) return 9;
        return 1;
    }
}
