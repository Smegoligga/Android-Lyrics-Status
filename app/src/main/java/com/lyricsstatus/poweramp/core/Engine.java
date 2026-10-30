package com.lyricsstatus.poweramp.core;

/**
 * Decides what the Discord status should be at any moment.
 *
 * Port of the main loop in OvalQuilter's LyricsStatus (index.ts + StatusChanger.changeStatus): find the line
 * whose timestamp is behind (position + offset), send it once, offset = average Discord round-trip + 100 ms.
 * Differences: event-driven (sleeps until the next line instead of polling at 60 fps, which matters on a phone),
 * re-sends after seeking backwards, refreshes the status before Discord's expiry during long gaps, and
 * restores your normal status when playback pauses or stops.
 *
 * Not thread-safe: call everything on one thread (the Android main thread).
 */
public final class Engine {

    public interface Scheduler {
        long now(); // monotonic ms

        void postDelayed(Runnable r, long delayMs);

        void cancel(Runnable r);
    }

    public interface LyricsLoader {
        /** Load lyrics off-thread, then call {@code done} on the engine thread. */
        void load(Track track, Settings settings, LoadCallback done);
    }

    public interface LoadCallback {
        void onLoaded(LyricsRepository.Result result);
    }

    public interface Sink {
        void sendLine(String text, Settings s);

        void sendText(String text, String emoji, Settings s);

        void idle(Settings s);
    }

    /** Snapshot for the UI. */
    public static final class State {
        public Track track;
        public boolean playing;
        public long positionMs;
        public String lyricsInfo = "";
        public boolean loading;
        public int lineCount;
        public String currentLine;
        public String lastStatus;
        public String error;
        public boolean errorFatal;
        public long offsetMs;
    }

    private final Scheduler sched;
    private final LyricsLoader loader;
    private final Sink sink;
    private final AutoOffset autoOffset = new AutoOffset();
    private Settings settings;

    private Track track;
    private Lyrics lyrics;
    private String lyricsInfo = "";
    private boolean loading;
    private int loadGen;

    private boolean playing;
    private long anchorPos;
    private long anchorTime;
    private float speed = 1f;

    private int currentIdx = -1;
    private String currentText; // what we last asked to show
    private long lastSendAt;
    private boolean idleSent = true;
    private String lastStatus;
    private String error;
    private boolean errorFatal;

    private final Runnable tick = this::tick;
    private final Runnable pauseTimeout = this::onPauseTimeout;

    public Engine(Scheduler sched, LyricsLoader loader, Sink sink, Settings settings) {
        this.sched = sched;
        this.loader = loader;
        this.sink = sink;
        this.settings = settings.copy();
    }

    // ------------------------------------------------------------------ inputs

    public void setSettings(Settings s) {
        Settings old = settings;
        settings = s.copy();
        if (!settings.enabled || settings.token.isEmpty()) {
            sched.cancel(tick);
            goIdle();
            return;
        }
        boolean sourcesChanged = old.useLocalLrc != settings.useLocalLrc || old.useEmbedded != settings.useEmbedded
                || old.useLrclib != settings.useLrclib || old.onlineWhenLocalUnsynced != settings.onlineWhenLocalUnsynced;
        if (sourcesChanged && track != null) reload();
        // force the new format to show right away
        currentIdx = -1;
        currentText = null;
        if (!old.token.equals(settings.token)) {
            error = null;
            errorFatal = false;
        }
        schedule(0);
    }

    /** New or updated track info (from Poweramp's broadcast or the media session). */
    public void onTrack(Track t) {
        if (t == null || t.title.isEmpty()) return;
        if (track != null && track.sameSong(t)) {
            Track merged = t.mergeWith(track);
            boolean gotPath = track.path == null && merged.path != null;
            track = merged;
            // A file path arrived after we already looked online: local/embedded lyrics may exist now
            if (gotPath && !loading && (lyrics == null || isOnline(lyrics))) reload();
            return;
        }
        track = t;
        lyrics = null;
        currentIdx = -1;
        anchorPos = 0;
        anchorTime = sched.now();
        reload();
    }

    /** Playback state with millisecond position as of {@code atTime} (scheduler clock). */
    public void onPlayback(boolean isPlaying, long positionMs, long atTime, float playbackSpeed) {
        boolean wasPlaying = playing;
        playing = isPlaying;
        anchorPos = Math.max(0, positionMs);
        anchorTime = atTime;
        speed = playbackSpeed > 0 ? playbackSpeed : 1f;

        if (playing) {
            sched.cancel(pauseTimeout);
            if (!wasPlaying) currentIdx = idleSent ? -1 : currentIdx;
            schedule(0);
        } else {
            sched.cancel(tick);
            if (wasPlaying || !idleSent) {
                sched.cancel(pauseTimeout);
                sched.postDelayed(pauseTimeout, Math.max(0, settings.pauseClearSeconds) * 1000L);
            }
        }
    }

    public void onStopped() {
        playing = false;
        sched.cancel(tick);
        sched.cancel(pauseTimeout);
        goIdle();
    }

    /** Feedback from the sender. */
    public void onSent(String text, long roundTripMs) {
        autoOffset.add(roundTripMs);
        lastStatus = text;
        error = null;
        errorFatal = false;
    }

    public void onSenderIdle(String what) {
        lastStatus = what;
    }

    public void onSenderError(String message, boolean fatal) {
        error = message;
        errorFatal = fatal;
    }

    /** Re-run the lyrics lookup (e.g. after granting file access). */
    public void reload() {
        if (track == null) return;
        final int gen = ++loadGen;
        loading = true;
        lyricsInfo = "Looking for lyrics…";
        lyrics = null;
        currentIdx = -1;
        final Track t = track;
        loader.load(t, settings, result -> {
            if (gen != loadGen) return; // song changed meanwhile
            loading = false;
            lyrics = result.lyrics;
            lyricsInfo = result.info;
            currentIdx = -1;
            schedule(0);
        });
    }

    // ------------------------------------------------------------------ core

    public long position() {
        long p = anchorPos;
        if (playing) p += (long) ((sched.now() - anchorTime) * speed);
        if (track != null && track.durationMs > 0) p = Math.min(p, track.durationMs);
        return p;
    }

    private void schedule(long delay) {
        sched.cancel(tick);
        sched.postDelayed(tick, Math.max(0, delay));
    }

    private void tick() {
        if (!settings.enabled || settings.token.isEmpty() || track == null || !playing || errorFatal) return;
        long now = sched.now();
        long expiryMs = settings.statusExpirySeconds * 1000L;
        long refreshDue = currentText != null && !idleSent ? lastSendAt + Math.max(10_000, expiryMs - 15_000) : Long.MAX_VALUE;

        // Still looking up lyrics: the loader's callback schedules the next tick, no need to poll
        if (loading) return;

        if (lyrics == null || !lyrics.synced || lyrics.lines.isEmpty()) {
            String tpl = settings.noLyricsStatus == null ? "" : settings.noLyricsStatus.trim();
            if (tpl.isEmpty()) {
                goIdle();
                return;
            }
            String text = Formatter.clip(WordFilter.apply(Formatter.template(tpl, null, track), settings));
            if (!text.equals(currentText) || now >= refreshDue) send(text, true);
            schedule(Math.max(1000, (lastSendAt + Math.max(10_000, expiryMs - 15_000)) - now));
            return;
        }

        long offset = autoOffset.offset(settings);
        long target = position() + offset;
        int idx = lyrics.indexAt(target);

        if (idx >= 0 && idx != currentIdx) {
            Lyrics.Line line = lyrics.lines.get(idx);
            if (line.text.isEmpty()) {
                // Empty LRC lines (instrumental breaks) keep the previous line, like the original
                currentIdx = idx;
            } else {
                // Battery saver: don't update more often than the minimum gap; when the gap is over,
                // whatever line is current then gets sent and the ones in between are skipped
                long gap = Math.max(0, settings.minUpdateSeconds) * 1000L;
                long wait = gap > 0 && !idleSent ? lastSendAt + gap - now : 0;
                if (wait > 0) {
                    schedule(wait);
                    return;
                }
                currentIdx = idx;
                send(Formatter.forLine(settings, line, track), false);
                refreshDue = lastSendAt + Math.max(10_000, expiryMs - 15_000);
            }
        } else if (now >= refreshDue) {
            // keep the last line alive through long instrumental parts
            send(currentText, currentTextIsTemplate);
            refreshDue = lastSendAt + Math.max(10_000, expiryMs - 15_000);
        }

        // Sleep exactly until the next line or the expiry refresh. Seeks, pauses and speed changes arrive
        // as playback callbacks and reschedule this, so no periodic polling is needed.
        long next = Long.MAX_VALUE;
        if (idx + 1 < lyrics.lines.size()) {
            next = (long) ((lyrics.lines.get(idx + 1).time - target) / speed);
        }
        if (refreshDue != Long.MAX_VALUE) next = Math.min(next, refreshDue - now);
        if (next != Long.MAX_VALUE) schedule(Math.max(20, next));
    }

    private boolean currentTextIsTemplate;

    private void send(String text, boolean noLyricsTemplate) {
        text = Formatter.clip(WordFilter.apply(text, settings));
        currentText = text;
        currentTextIsTemplate = noLyricsTemplate;
        lastSendAt = sched.now();
        idleSent = false;
        if (noLyricsTemplate) sink.sendText(text, settings.customEmoji, settings);
        else sink.sendLine(text, settings);
    }

    private void onPauseTimeout() {
        if (!playing) goIdle();
    }

    private void goIdle() {
        currentIdx = -1;
        currentText = null;
        if (!idleSent) {
            idleSent = true;
            sink.idle(settings);
        }
    }

    private static boolean isOnline(Lyrics l) {
        return l.source.startsWith(LrcLibClient.SOURCE);
    }

    public State state() {
        State s = new State();
        s.track = track;
        s.playing = playing;
        s.positionMs = position();
        s.lyricsInfo = lyricsInfo;
        s.loading = loading;
        s.lineCount = lyrics != null ? lyrics.lines.size() : 0;
        if (lyrics != null && currentIdx >= 0 && currentIdx < lyrics.lines.size()) s.currentLine = lyrics.lines.get(currentIdx).text;
        s.lastStatus = lastStatus;
        s.error = error;
        s.errorFatal = errorFatal;
        s.offsetMs = autoOffset.offset(settings);
        return s;
    }
}
