package io.openflux.app;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.openflux.bridge.mobile.Mobile;

// The app's event log, one for the process: the core's lines and the
// app's own. Whoever runs drains the core (the log screen, or a connection
// service while the screen is closed), so a line gets the time it came,
// not the time someone looked; the core itself keeps only 500 lines.
// The log lives as long as the process: a recreated screen (a new
// language) still has it.
final class LogBuffer {
    static final int MAX = 5000;
    // What matters is kept apart and for far longer: with the verbose log
    // on the core writes several lines per packet, so MAX lines are less
    // than a minute, and by the time someone notices a fault and sends the
    // log, everything that explains it has been pushed out. These are the
    // same lines a log without the verbose switch would hold, so they cost
    // what the app logged anyway.
    static final int MAX_IMPORTANT = 4000;
    private static final Pattern TAG = Pattern.compile("^(\\[[A-Z0-9_-]+\\])");

    static final class Entry {
        final long seq;
        final long time;
        final String text;
        // [ERROR], [SESSION]...; null for an untagged line.
        final String tag;
        // An untagged line that came with the line before it: the filters
        // keep or drop it together with that line.
        final boolean continuation;

        Entry(long seq, long time, String text, String tag, boolean continuation) {
            this.seq = seq;
            this.time = time;
            this.text = text;
            this.tag = tag;
            this.continuation = continuation;
        }
    }

    private static final ArrayDeque<Entry> entries = new ArrayDeque<>();
    // A subset of entries: everything but the per-packet lines.
    private static final ArrayDeque<Entry> important = new ArrayDeque<>();
    private static long nextSeq;
    private static boolean lastNoisy;

    // The core's per-packet tags; everything else - connections, carriers,
    // the stripe figures, errors, the app's own lines - is kept.
    private static boolean noisy(String tag) {
        return "[BATCH]".equals(tag) || "[CRYPTO]".equals(tag);
    }

    private LogBuffer() {
    }

    static final String KEY_VERBOSE = "verbose_log";

    /**
     * Sets how much the core logs from its next start: only what matters
     * (connections, errors, rooms) unless the verbose log is on. The verbose
     * log writes every packet, hundreds of lines a second during a video,
     * which costs the phone time; it is for finding faults.
     */
    static void applyCoreLevel(android.content.Context context) {
        boolean verbose = context.getSharedPreferences(MainActivity.SETTINGS_PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .getBoolean(KEY_VERBOSE, false);
        Mobile.setDebugLevel(verbose ? 2 : 0);
    }

    static synchronized void drain() {
        String pending = Mobile.readLogs();
        if (pending != null && !pending.isEmpty()) add(pending);
    }

    static synchronized void add(String text) {
        long now = System.currentTimeMillis();
        boolean first = true;
        for (String line : text.split("\n")) {
            if (line.trim().isEmpty()) continue;
            Matcher m = TAG.matcher(line);
            String tag = m.find() ? m.group(1) : null;
            boolean continuation = tag == null && !first;
            Entry e = new Entry(nextSeq++, now, line, tag, continuation);
            entries.addLast(e);
            // A line with no tag belongs to the line before it (a hexdump
            // under its packet), so it is kept or dropped with it.
            lastNoisy = continuation ? lastNoisy : noisy(tag);
            if (!lastNoisy) important.addLast(e);
            first = false;
        }
        while (entries.size() > MAX) entries.removeFirst();
        while (important.size() > MAX_IMPORTANT) important.removeFirst();
    }

    // What goes to the server: everything that matters, as far back as it
    // reaches, and the per-packet lines of the last minutes around it, in
    // the order they came. Together they are what a fault needs - what led
    // to it, and the detail at the moment it happened.
    static synchronized List<Entry> report() {
        List<Entry> out = new ArrayList<>(important.size() + entries.size());
        long from = entries.isEmpty() ? 0 : entries.peekFirst().seq;
        for (Entry e : important) {
            if (e.seq < from) out.add(e);
        }
        out.addAll(entries);
        return out;
    }

    // The entries from seq on (all of them for 0), oldest first.
    static synchronized List<Entry> since(long seq) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.seq >= seq) out.add(e);
        return out;
    }

    static synchronized long nextSeq() {
        return nextSeq;
    }

    static synchronized void clear() {
        entries.clear();
        important.clear();
    }

    static String time(Entry e) {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(e.time));
    }

    // A line as it is copied: the time, then the text.
    static String line(Entry e) {
        return time(e) + "  " + e.text;
    }

    // A line in a shared file, which may span days: with the date.
    static String fullLine(Entry e) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date(e.time)) + "  " + e.text;
    }
}
