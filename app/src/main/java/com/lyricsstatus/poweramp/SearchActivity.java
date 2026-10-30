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
    private EditText song, artist, album;
    private int searchGen;

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
                + (track.album.isEmpty() ? "" : " (" + track.album + ")")));

        LinearLayout g = skin.group(root, "Search");
        song = field(g, "Song", track != null ? track.title : "");
        artist = field(g, "Artist", track != null ? track.artist : "");
        album = field(g, "Album", track != null ? track.album : "");
        g.addView(skin.toggle("Ignore feat., prod., remaster etc.", "Searches without featured artists, producers "
                + "and version tags in the names", s.searchIgnoreExtras, v -> {
            com.lyricsstatus.poweramp.core.Settings ns = app.settings();
            ns.searchIgnoreExtras = v;
            app.updateSettings(ns);
        }));
        g.addView(skin.hint("Leave Song empty to list the songs on an album."));
        LinearLayout btns = skin.row();
        btns.addView(skin.button("Search", true, v -> search()));
        btns.addView(skin.button("Back", false, v -> finish()));
        g.addView(btns);
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
        final String s = song.getText().toString(), a = artist.getText().toString(), al = album.getText().toString();
        if (s.trim().isEmpty() && a.trim().isEmpty() && al.trim().isEmpty()) {
            status.setText("Type a song, artist or album first.");
            return;
        }
        final boolean ignore = app.settings().searchIgnoreExtras;
        final int gen = ++searchGen;
        status.setText("Searching…");
        app.io.execute(() -> {
            List<LyricsSearch.Record> list = null;
            String err = null;
            try {
                list = LyricsSearch.search(s, a, al, ignore);
            } catch (Exception e) {
                err = "Search failed: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
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
        results.removeAllViews();
        if (list.isEmpty()) {
            results.addView(skin.hint("No results."));
            return;
        }
        for (LyricsSearch.Record r : list) results.addView(row(r));
    }

    /** One result: title + details; tap to preview and choose. */
    private View row(LyricsSearch.Record r) {
        LinearLayout box = skin.box();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = skin.dp(6);
        box.setLayoutParams(lp);
        TextView title = skin.text(r.track, 15, true);
        box.addView(title);
        box.addView(skin.hint(LyricsSearch.describe(r)));
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
        Lyrics l = r.lyrics("preview");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Lyrics.Line line : l.lines) {
            if (line.text.isEmpty()) continue;
            sb.append(line.text).append('\n');
            if (++n == 6) break;
        }
        TextView preview = skin.text(sb.length() == 0 ? (r.instrumental ? "Instrumental" : "No lyrics") : sb.toString().trim(), 14, false);
        preview.setTextColor(skin.highlight());
        preview.setMaxLines(6);
        d.addView(preview);
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
