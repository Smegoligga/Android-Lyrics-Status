package com.lyricsstatus.poweramp.core;

import org.json.JSONObject;

/**
 * Background worker that pushes status changes to Discord, one request at a time.
 * Only the newest pending update is kept, so after a rate limit the current line is sent rather than a
 * backlog of stale ones. Also saves the status you had before and puts it back when playback goes idle.
 */
public final class StatusSender {

    /** Persists the pre-existing status across app restarts. */
    public interface Store {
        String loadOriginal();

        void saveOriginal(String json);

        String loadLastSent();

        void saveLastSent(String text);
    }

    public interface Listener {
        void onSent(String text, long roundTripMs);

        void onIdle(String what);

        void onError(String message, boolean fatal);
    }

    private static final class Op {
        final boolean idle;
        final String text;
        final String emoji;
        final int expiry;
        final Settings settings;

        Op(boolean idle, String text, String emoji, int expiry, Settings settings) {
            this.idle = idle;
            this.text = text;
            this.emoji = emoji;
            this.expiry = expiry;
            this.settings = settings;
        }
    }

    private final Store store;
    private final Listener listener;
    private final Object lock = new Object();
    private Op pending;
    private boolean active; // we've overwritten the user's status since the last idle
    private boolean running = true;
    private final Thread thread;

    public StatusSender(Store store, Listener listener) {
        this.store = store;
        this.listener = listener;
        thread = new Thread(this::loop, "discord-status");
        thread.setDaemon(true);
        thread.start();
    }

    public void sendLine(String text, Settings s) {
        offer(new Op(false, text, s.emoji(), s.statusExpirySeconds, s));
    }

    public void sendText(String text, String emoji, Settings s) {
        offer(new Op(false, text, emoji, s.statusExpirySeconds, s));
    }

    /** Restore (or clear) the status. No-op if we never touched it. */
    public void idle(Settings s) {
        synchronized (lock) {
            if (!active && (pending == null || pending.idle)) {
                pending = null;
                return;
            }
        }
        offer(new Op(true, null, null, 0, s));
    }

    public void shutdown() {
        synchronized (lock) {
            running = false;
            lock.notifyAll();
        }
    }

    private void offer(Op op) {
        synchronized (lock) {
            pending = op;
            lock.notifyAll();
        }
    }

    private void loop() {
        while (true) {
            Op op;
            synchronized (lock) {
                while (running && pending == null) {
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                if (!running) return;
                op = pending;
                pending = null;
            }
            try {
                run(op);
            } catch (Exception e) {
                listener.onError("Network error: " + e.getClass().getSimpleName()
                        + (e.getMessage() != null ? " (" + e.getMessage() + ")" : ""), false);
                sleep(2000);
            }
        }
    }

    private void run(Op op) throws Exception {
        if (op.settings.token == null || op.settings.token.trim().isEmpty()) {
            listener.onError("No Discord token set", true);
            return;
        }
        DiscordClient d = new DiscordClient(op.settings.token);

        if (op.idle) {
            JSONObject restore = null;
            String what = "Status cleared";
            if (op.settings.restoreStatus) {
                String json = store.loadOriginal();
                if (json != null && !json.equals("null")) {
                    try {
                        JSONObject o = new JSONObject(json);
                        if (!expired(o)) {
                            restore = o;
                            what = "Restored your status";
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            Http.Response r = d.setCustomStatus(restore);
            if (handleError(r, op)) return;
            synchronized (lock) {
                active = false;
            }
            store.saveLastSent(null);
            listener.onIdle(what);
            return;
        }

        boolean wasActive;
        synchronized (lock) {
            wasActive = active;
        }
        if (!wasActive && op.settings.restoreStatus) captureOriginal(d);

        JSONObject cs = DiscordClient.customStatus(op.text, op.emoji, op.expiry);
        Http.Response r = d.setCustomStatus(cs);
        if (handleError(r, op)) return;
        synchronized (lock) {
            active = true;
        }
        store.saveLastSent(op.text);
        listener.onSent(op.text, r.elapsedMs);
    }

    /** Save whatever status the user had, unless it's one of ours left over from a previous run. */
    private void captureOriginal(DiscordClient d) {
        try {
            Object cur = d.getCustomStatus();
            if (cur == null) return; // request failed: keep what we had
            if (cur == JSONObject.NULL || !(cur instanceof JSONObject)) {
                store.saveOriginal("null");
                return;
            }
            JSONObject o = (JSONObject) cur;
            String text = o.isNull("text") ? "" : o.optString("text", "");
            String lastSent = store.loadLastSent();
            if (lastSent != null && lastSent.equals(text)) return; // it's ours
            store.saveOriginal(o.toString());
        } catch (Exception ignored) {
        }
    }

    private static boolean expired(JSONObject o) {
        if (o.isNull("expires_at")) return false;
        String exp = o.optString("expires_at", "");
        if (exp.isEmpty()) return false;
        try {
            String norm = exp.replaceAll("\\.(\\d{3})\\d*", ".$1").replace("+00:00", "Z");
            if (!norm.contains(".")) norm = norm.replace("Z", ".000Z");
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.ROOT);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(norm).getTime() <= System.currentTimeMillis();
        } catch (Exception e) {
            return false;
        }
    }

    /** @return true if the request failed (and was handled). */
    private boolean handleError(Http.Response r, Op op) {
        if (r.ok()) return false;
        if (r.code == 429) {
            double wait = DiscordClient.retryAfter(r);
            listener.onError("Rate limited by Discord, waiting " + String.format(java.util.Locale.ROOT, "%.1f", wait) + " s", false);
            sleep((long) (wait * 1000));
            // retry unless something newer arrived meanwhile
            synchronized (lock) {
                if (pending == null) pending = op;
            }
            return true;
        }
        if (r.code == 401) {
            listener.onError("Discord rejected the token (401). Paste a fresh one.", true);
            return true;
        }
        listener.onError("Discord HTTP " + r.code + ": " + truncate(r.body), false);
        return true;
    }

    private static String truncate(String s) {
        if (s == null) return "";
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
