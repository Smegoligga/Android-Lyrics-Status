package com.lyricsstatus.poweramp;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.lyricsstatus.poweramp.core.Lyrics;
import com.lyricsstatus.poweramp.core.LyricsSearch;
import com.lyricsstatus.poweramp.core.Overrides;
import com.lyricsstatus.poweramp.core.Track;

import java.util.List;

/**
 * Manual lyrics search by song, artist and album (LRCLIB). A result can be pinned to the current song, or its
 * album can be used for the current song's whole album (each song is then matched by name in that album).
 */
public final class SearchActivity extends Activity {

    private App app;
    private Skin skin;
    private Sfx sfx;
    private Track track;
    private LinearLayout results;
    private TextView status;
    private EditText query, song, artist, album;
    private LinearLayout advanced;
    private boolean advancedOn;
    private String prefilled = "";
    private int searchGen;
    private int sortMode = com.lyricsstatus.poweramp.core.LyricsSearch.SORT_BEST;
    private List<LyricsSearch.Record> lastResults;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = App.get(this);
        com.lyricsstatus.poweramp.core.Settings s = app.settings();
        sfx = new Sfx(this);
        skin = Skin.create(s.theme, this, sfx);
        if (skin.hasSounds()) sfx.load(s.menuSounds, s.menuVolume);
        track = app.engine().state().track;

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = skin.dp(16);
        root.setPadding(p, p, p, skin.dp(32));
        scroll.addView(root);
        setContentView(scroll);
        skin.applyWindow(scroll, root);
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                android.graphics.Insets i = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                        | android.view.WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return insets;
            });
        }

        root.addView(skin.header("Search lyrics", track == null ? "Nothing is playing. You can still search."
                : "Now playing: " + track.title + (track.artist.isEmpty() ? "" : " - " + track.artist)
                + (track.album.isEmpty() ? "" : " (" + track.album + ")")
                + (track.durationMs > 0 ? "\nLength: " + com.lyricsstatus.poweramp.core.Formatter.formatSeconds(
                Math.round(track.durationMs / 1000.0)) : "")));

        LinearLayout g = skin.group(root, "Search");
        // Simple search: one box, pre-filled with "song artist"
        if (track != null) {
            java.util.List<String> who = LyricsSearch.credits(track.artist, track.title);
            prefilled = (LyricsSearch.cleanTitle(track.title) + " " + (who.isEmpty() ? track.artist : who.get(0))).trim();
        }
        query = field(g, "Search", prefilled);
        query.setHint("Song and artist");
        query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener((v, id, e) -> {
            search();
            return true;
        });
        // Advanced search: separate fields
        g.addView(skin.toggle("Advanced search", "Search by song, artist and album separately", false, on -> {
            advancedOn = on;
            advanced.setVisibility(on ? View.VISIBLE : View.GONE);
            query.setEnabled(!on);
            query.setAlpha(on ? 0.5f : 1f);
        }));
        advanced = new LinearLayout(this);
        advanced.setOrientation(LinearLayout.VERTICAL);
        advanced.setVisibility(View.GONE);
        song = field(advanced, "Song", track != null ? track.title : "");
        artist = field(advanced, "Artist", track != null ? track.artist : "");
        album = field(advanced, "Album", track != null ? track.album : "");
        advanced.addView(skin.hint("Leave Song empty to list the songs on an album."));
        g.addView(advanced);
        g.addView(skin.toggle("Ignore feat., prod., remaster etc.", "Searches without featured artists, producers "
                + "and version tags in the names", s.searchIgnoreExtras, v -> {
            com.lyricsstatus.poweramp.core.Settings ns = app.settings();
            ns.searchIgnoreExtras = v;
            app.updateSettings(ns);
        }));
        LinearLayout btns = skin.row();
        btns.addView(skin.button("Search", true, v -> search()));
        btns.addView(skin.button("Back", false, v -> finish()));
        g.addView(btns);
        g.addView(skin.choice("Sort results", new String[]{
                "Best match: synced lyrics with the same length first",
                "Closest length to your song"}, sortMode, i -> {
            sortMode = i;
            if (lastResults != null) render(lastResults);
        }));
        status = skin.hint("");
        g.addView(status);

        // current manual choices, with undo
        Overrides ov = app.repository().overrides();
        if (track != null && ov != null && (ov.hasSong(track) || ov.album(track) != null)) {
            LinearLayout picked = skin.group(root, "Picked lyrics");
            if (ov.hasSong(track)) {
                picked.addView(skin.text("This song has lyrics picked by hand.", 14, false));
                picked.addView(skin.button("Remove pick for this song", false, v -> {
                    ov.unpinSong(track);
                    app.engine().reload();
                    toast("Removed");
                    recreate();
                }));
            }
            Overrides.AlbumMap m = ov.album(track);
            if (m != null) {
                picked.addView(skin.text("This album uses lyrics from \"" + m.album + "\" by " + m.artist + ".", 14, false));
                picked.addView(skin.button("Remove album choice", false, v -> {
                    ov.unmapAlbum(track);
                    app.engine().reload();
                    toast("Removed");
                    recreate();
                }));
            }
        }

        results = skin.group(root, "Results");
        results.addView(skin.hint("Results appear here."));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sfx.release();
    }

    private EditText field(LinearLayout parent, String label, String value) {
        parent.addView(skin.fieldLabel(label));
        EditText e = skin.edit();
        e.setSingleLine(true);
        e.setText(value);
        parent.addView(e);
        return e;
    }

    private void search() {
        final boolean adv = advancedOn;
        final String q = query.getText().toString();
        final String s = song.getText().toString(), a = artist.getText().toString(), al = album.getText().toString();
        if (adv ? (s.trim().isEmpty() && a.trim().isEmpty() && al.trim().isEmpty()) : q.trim().isEmpty()) {
            status.setText(adv ? "Type a song, artist or album first." : "Type a song and artist first.");
            return;
        }
        final boolean ignore = app.settings().searchIgnoreExtras;
        final Track playing = track;
        final boolean usingPrefill = !adv && playing != null && q.trim().equals(prefilled);
        final int gen = ++searchGen;
        status.setText("Searching…");
        app.io.execute(() -> {
            List<LyricsSearch.Record> list = null;
            String err = null;
            try {
                if (adv) {
                    list = LyricsSearch.search(s, a, al, ignore);
                } else {
                    list = LyricsSearch.searchSimple(q, ignore);
                    // nothing for the pre-filled text: try the playing song's tags with all the fallbacks
                    if (list.isEmpty() && usingPrefill) {
                        list = LyricsSearch.search(playing.title, playing.artist, playing.album, true);
                    }
                }
            } catch (LyricsSearch.ServiceDown e) {
                err = e.getMessage();
            } catch (Exception e) {
                err = "Search failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                        + ". Check your connection and try again.";
            }
            final List<LyricsSearch.Record> fl = list;
            final String fe = err;
            app.main.post(() -> {
                if (gen != searchGen || isFinishing()) return;
                if (fe != null) {
                    status.setText(fe);
                    return;
                }
                status.setText(fl.isEmpty() ? "Nothing found. Try fewer words or tick Ignore feat./prod."
                        : fl.size() + " result" + (fl.size() == 1 ? "" : "s") + (fl.size() == 20 ? " (first 20)" : ""));
                render(fl);
            });
        });
    }

    private void render(List<LyricsSearch.Record> list) {
        lastResults = list;
        results.removeAllViews();
        if (list.isEmpty()) {
            results.addView(skin.hint("No results."));
            return;
        }
        long dur = track != null ? track.durationMs : 0;
        String title = track != null ? track.title : "";
        List<LyricsSearch.Record> ranked = LyricsSearch.rank(list, dur, title, sortMode);
        for (int i = 0; i < ranked.size(); i++) {
            LyricsSearch.Record r = ranked.get(i);
            boolean best = i == 0 && sortMode == LyricsSearch.SORT_BEST && track != null
                    && LyricsSearch.isGoodMatch(r, dur, title);
            results.addView(row(r, best));
        }
    }

    /** One result: title + details; tap to preview and choose. */
    private View row(LyricsSearch.Record r, boolean best) {
        LinearLayout box = skin.box();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = skin.dp(6);
        box.setLayoutParams(lp);
        TextView title = skin.text(r.track, 15, true);
        box.addView(title);
        if (best) {
            TextView tag = skin.text("Best match", 12, true);
            tag.setTextColor(skin.highlight());
            box.addView(tag);
        }
        String kind = r.instrumental ? "Instrumental" : r.isSynced() ? "Synced" : r.plain.isEmpty() ? "No lyrics" : "Not synced";
        box.addView(skin.hint(r.artist + (r.album.isEmpty() ? "" : " · " + r.album)));
        box.addView(skin.hint(LyricsSearch.lengthVsSong(r, track != null ? track.durationMs : 0) + " · " + kind
                + " · tap to see the lyrics"));
        LinearLayout detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        detail.setVisibility(View.GONE);
        box.addView(detail);
        box.setOnClickListener(v -> {
            boolean open = detail.getVisibility() == View.VISIBLE;
            if (open) {
                detail.setVisibility(View.GONE);
                return;
            }
            if (detail.getChildCount() == 0) fillDetail(detail, r);
            detail.setVisibility(View.VISIBLE);
        });
        sfx.wire(box, Sfx.MENU);
        return box;
    }

    private void fillDetail(LinearLayout d, LyricsSearch.Record r) {
        d.addView(timedLyrics(r));
        if (!r.isSynced() && !r.plain.isEmpty()) {
            d.addView(skin.hint("Not synced: it can't follow the music, so the status won't change line by line."));
        }
        if (track == null) {
            d.addView(skin.hint("Play a song to use these lyrics."));
            return;
        }
        LinearLayout b = skin.row();
        b.setOrientation(LinearLayout.VERTICAL);
        if (!r.text().trim().isEmpty()) {
            b.addView(skin.button("Use for this song", true, v -> {
                app.repository().overrides().pinSong(track, r);
                app.engine().reload();
                toast("Using these lyrics for \"" + track.title + "\"");
                finish();
            }));
        }
        if (!track.album.isEmpty() && !r.album.isEmpty()) {
            b.addView(skin.button("Use this album for my album", false, v -> useAlbum(r)));
        }
        d.addView(b);
    }

    /** A short scrollable window with every line and its exact timing, like "[0:12.34] text". */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private View timedLyrics(LyricsSearch.Record r) {
        Lyrics l = r.lyrics("preview");
        StringBuilder sb = new StringBuilder();
        for (Lyrics.Line line : l.lines) {
            String stamp = l.synced ? com.lyricsstatus.poweramp.core.Formatter.lrcStamp(line.time)
                    .replaceFirst("^\\[0?(\\d+):", "[$1:") : "[--:--]";
            sb.append(stamp).append(' ').append(line.text.isEmpty() ? "♪" : line.text).append('\n');
        }
        TextView lines = skin.text(sb.length() == 0 ? (r.instrumental ? "Instrumental" : "No lyrics")
                : sb.toString().trim(), 13, false);
        lines.setTypeface(android.graphics.Typeface.MONOSPACE);
        lines.setEllipsize(null);
        lines.setTextIsSelectable(false);
        ScrollView window = new ScrollView(this);
        window.setVerticalScrollBarEnabled(true);
        window.setScrollbarFadingEnabled(false);
        window.addView(lines);
        // let this small window scroll inside the page's own scrolling
        window.setOnTouchListener((v, e) -> {
            v.getParent().requestDisallowInterceptTouchEvent(e.getActionMasked() != android.view.MotionEvent.ACTION_UP
                    && e.getActionMasked() != android.view.MotionEvent.ACTION_CANCEL);
            return false;
        });
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(0x22000000);
        bg.setCornerRadius(skin.id().equals("cs16") ? 0 : skin.dp(4));
        window.setBackground(bg);
        window.setPadding(skin.dp(8), skin.dp(6), skin.dp(8), skin.dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, skin.dp(200));
        lp.topMargin = skin.dp(6);
        lp.bottomMargin = skin.dp(4);
        window.setLayoutParams(lp);
        return window;
    }

    /** Map the playing track's album to the result's album, then check the current song is on it. */
    private void useAlbum(LyricsSearch.Record r) {
        Overrides ov = app.repository().overrides();
        ov.mapAlbum(track, r.artist, r.album);
        ov.unpinSong(track); // the album choice should apply to this song too
        status.setText("Looking for \"" + LyricsSearch.cleanTitle(track.title) + "\" on " + r.album + "…");
        app.io.execute(() -> {
            String msg;
            try {
                LyricsSearch.Record hit = LyricsSearch.findInAlbum(track.title, track.durationMs, r.artist, r.album);
                msg = hit != null ? "Album applied. This song matched \"" + hit.track + "\"."
                        : "Album applied, but no song called \"" + LyricsSearch.cleanTitle(track.title)
                        + "\" is on it. Other songs from your album will still use it.";
            } catch (Exception e) {
                msg = "Album applied. Couldn't check this song right now (offline?).";
            }
            final String m = msg;
            app.main.post(() -> {
                app.engine().reload();
                toast(m);
                if (!isFinishing()) status.setText(m);
            });
        });
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }
}
