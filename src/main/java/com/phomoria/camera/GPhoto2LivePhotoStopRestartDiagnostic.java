package com.phomoria.camera;

import com.phomoria.debug.DebugLog;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Isolated diagnostic for EOS/gPhoto2 Live Photo using the proven safe
 * transition: Live View -> PRE -> STOP Live View -> physical still capture
 * -> START Live View -> POST.
 *
 * IMPORTANT: This WILL trigger the physical shutter.
 * It does not modify production camera code.
 */
public final class GPhoto2LivePhotoStopRestartDiagnostic {
    private static final int MOVIE_SECONDS = 8;
    private static final double PRE_SECONDS = 1.5;
    private static final double POST_SECONDS = 1.5;
    private static final double WARMUP_SECONDS = 2.5;
    private static final int POLL_MS = 20;
    private static final int STILL_TIMEOUT_SECONDS = 45;

    private GPhoto2LivePhotoStopRestartDiagnostic() {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LiveWindow window = new LiveWindow();
            window.setVisible(true);
            Thread worker = new Thread(() -> run(window), "gphoto2-stop-restart-diagnostic");
            worker.setDaemon(true);
            worker.start();
        });
    }

    private static void run(LiveWindow window) {
        Path out = null;
        try {
            log(window, "=== gPhoto2 Live Photo STOP/RESTART Diagnostic ===");
            log(window, "PRE=" + PRE_SECONDS + "s POST=" + POST_SECONDS + "s WARMUP=" + WARMUP_SECONDS + "s");
            log(window, "WARNING: physical DSLR shutter WILL be triggered.");

            List<String> cameras = detectCameraNames();
            if (cameras.isEmpty()) { log(window, "FAIL: No gPhoto2 camera detected."); return; }
            String camera = cameras.get(0);
            log(window, "Detected camera: " + camera);

            out = Files.createTempDirectory("phomoria-live-photo-stop-restart-");
            Files.createDirectories(out.resolve("pre"));
            Files.createDirectories(out.resolve("post"));
            Files.createDirectories(out.resolve("still"));
            log(window, "Output directory = " + out);

            Result result = test(camera, out, window);
            writeReport(out, result);

            log(window, "=== FINAL RESULT ===");
            log(window, "PRE frames = " + result.preFrames);
            log(window, "POST frames = " + result.postFrames);
            log(window, "Still capture = " + (result.stillSuccess ? "SUCCESS" : "FAILED"));
            log(window, "Still size = " + result.width + "x" + result.height);
            log(window, "Still elapsed = " + result.stillElapsedMs + " ms");
            log(window, "Live View stop elapsed = " + result.stopElapsedMs + " ms");
            log(window, "Live View restart elapsed = " + result.restartElapsedMs + " ms");
            log(window, "First POST frame delay after capture = " + formatMs(result.postDelayMs));
            log(window, "Output directory = " + out);
            log(window, result.stillSuccess && result.preFrames >= 5 && result.postFrames >= 5
                    ? "STOP/RESTART LIVE PHOTO SUCCESS" : "STOP/RESTART LIVE PHOTO INCOMPLETE");
        } catch (Exception ex) {
            log(window, "FAIL: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            if (out != null) logStatic("Diagnostic output retained at: " + out);
            DebugLog.error("gPhoto2 stop/restart diagnostic failed.", ex);
        }
    }

    private static Result test(String camera, Path out, LiveWindow window) throws Exception {
        Deque<Frame> pre = new ArrayDeque<>();
        List<Frame> post = new ArrayList<>();
        Path movie = out.resolve("movie.mjpg");
        long start = System.nanoTime();

        log(window, "Starting Live View for PRE...");
        LiveSession live = startLive(camera, out, window);
        FrameReader reader = new FrameReader(live, movie, window, pre, post);

        long firstFrame = reader.readUntil(start + TimeUnit.SECONDS.toNanos(MOVIE_SECONDS),
                () -> elapsedSince(firstFrameHolder(reader)) >= WARMUP_SECONDS && pre.size() >= 5);
        if (firstFrame == 0) throw new IllegalStateException("No Live View frame received during warm-up.");

        trim(pre, System.nanoTime());
        log(window, "LIVE VIEW WARMED UP. PRE=" + pre.size() + " frames");

        long stopStart = System.nanoTime();
        log(window, "=== STOP LIVE VIEW ===");
        stopLive(live);
        long stopElapsed = msSince(stopStart);
        log(window, "Live View stopped in " + stopElapsed + " ms");

        long captureStart = System.nanoTime();
        log(window, "=== PHYSICAL SHUTTER ===");
        StillResult still = captureStill(camera, out.resolve("still"), window);
        long stillElapsed = msSince(captureStart);
        log(window, still.success ? "PHYSICAL STILL SUCCESS: " + still.width + "x" + still.height
                : "PHYSICAL STILL FAILED");

        Files.deleteIfExists(movie);
        long restartStart = System.nanoTime();
        log(window, "=== RESTART LIVE VIEW FOR POST ===");
        LiveSession postLive = startLive(camera, out, window);
        long restartElapsed = msSince(restartStart);
        log(window, "Live View process restarted in " + restartElapsed + " ms");

        long postDeadline = System.nanoTime() + secondsToNanos(POST_SECONDS + 4);
        long postFirst = 0;
        FrameReader postReader = new FrameReader(postLive, movie, window, pre, post);
        while (System.nanoTime() < postDeadline && post.size() < 100) {
            int before = post.size();
            postReader.readAvailable();
            if (postFirst == 0 && post.size() > before) postFirst = post.get(0).timeNanos;
            if (postFirst != 0 && (System.nanoTime() - postFirst) >= secondsToNanos(POST_SECONDS)) break;
            if (!postLive.process.isAlive() && before == post.size()) break;
            Thread.sleep(POLL_MS);
        }
        stopLive(postLive);

        export(out.resolve("pre"), new ArrayList<>(pre), "pre");
        export(out.resolve("post"), post, "post");

        long postDelay = postReader.firstFrameAt == 0 || still.finishedAt == 0
                ? -1 : msBetween(still.finishedAt, postReader.firstFrameAt);

        return new Result(pre.size(), post.size(), still.success, still.width, still.height,
                stillElapsed, stopElapsed, restartElapsed, postDelay,
                live.stderr.text(), postLive.stderr.text());
    }

    private static long[] firstFrameHolder(FrameReader r) { return new long[]{r.firstFrameAt}; }
    private static double elapsedSince(long[] holder) {
        if (holder[0] == 0) return 0;
        return (System.nanoTime() - holder[0]) / 1_000_000_000.0;
    }

    private static LiveSession startLive(String camera, Path out, LiveWindow window) throws Exception {
        Files.deleteIfExists(out.resolve("movie.mjpg"));
        List<String> args = Arrays.asList("--camera", camera, "--capture-movie=" + MOVIE_SECONDS + "s", "--filename", "live-test.mjpg");
        Process p = buildShell(out, args.toArray(String[]::new)).start();
        Collector stdout = new Collector(p.getInputStream());
        Collector stderr = new Collector(p.getErrorStream());
        new Thread(stdout, "live-stdout").start();
        new Thread(stderr, "live-stderr").start();
        return new LiveSession(p, stdout, stderr);
    }

    private static void stopLive(LiveSession live) throws Exception {
        if (live == null || !live.process.isAlive()) return;
        live.process.destroy();
        if (!live.process.waitFor(3, TimeUnit.SECONDS)) live.process.destroyForcibly();
        live.process.waitFor(2, TimeUnit.SECONDS);
    }

    private static StillResult captureStill(String camera, Path dir, LiveWindow window) throws Exception {
        Path target = dir.resolve("final.jpg");
        List<String> args = Arrays.asList("--camera", camera, "--capture-image-and-download", "--force-overwrite", "--filename", "final.jpg");
        ProcessResult r = runShell(dir, STILL_TIMEOUT_SECONDS, args.toArray(String[]::new));
        log(window, "Still exit=" + r.exitCode);
        log(window, "Still stderr=" + quote(r.stderr));
        if (!Files.isRegularFile(target)) return new StillResult(false, 0, 0, 0);
        BufferedImage img = ImageIO.read(target.toFile());
        if (img == null) return new StillResult(false, 0, 0, 0);
        return new StillResult(true, img.getWidth(), img.getHeight(), System.nanoTime());
    }

    private static List<String> detectCameraNames() throws Exception {
        ProcessResult r = runShell(null, 15, "--auto-detect");
        List<String> names = new ArrayList<>();
        for (String line : r.stdout.split("\\R")) {
            int usb = line.lastIndexOf("usb:");
            if (usb < 0) continue;
            String port = line.substring(usb).trim();
            if (!port.matches("usb:\\d+,\\d+")) continue;
            String name = line.substring(0, usb).trim();
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }

    private static ProcessResult runShell(Path dir, int timeout, String... args) throws Exception {
        Process p = buildShell(dir, args).start();
        Collector out = new Collector(p.getInputStream()), err = new Collector(p.getErrorStream());
        Thread ot = new Thread(out, "gphoto-stdout"), et = new Thread(err, "gphoto-stderr");
        ot.start(); et.start();
        boolean finished = p.waitFor(timeout, TimeUnit.SECONDS);
        if (!finished) { p.destroyForcibly(); p.waitFor(2, TimeUnit.SECONDS); }
        ot.join(2000); et.join(2000);
        return new ProcessResult(finished ? p.exitValue() : -1, out.text(), err.text());
    }

    private static ProcessBuilder buildShell(Path dir, String... args) {
        String bash = System.getProperty("phomoria.msys2.bash", System.getenv().getOrDefault("PHOMORIA_MSYS2_BASH", "C:\\msys64\\usr\\bin\\bash.exe"));
        StringBuilder command = new StringBuilder("/ucrt64/bin/gphoto2.exe");
        for (String a : args) command.append(' ').append(shellQuote(a));
        if (dir != null) command.insert(0, "cd '" + toMsysPath(dir) + "' && ");
        ProcessBuilder pb = new ProcessBuilder(bash, "--login", "-c", command.toString());
        Map<String,String> e = pb.environment();
        e.put("MSYSTEM", "UCRT64"); e.put("MSYS2_PATH_TYPE", "inherit"); e.put("CHERE_INVOKING", "1");
        return pb;
    }

    private static final class FrameReader {
        final LiveSession live; final Path movie; final LiveWindow window; final Deque<Frame> pre; final List<Frame> post;
        final ByteArrayOutputStream pending = new ByteArrayOutputStream();
        long position = 0, firstFrameAt = 0, firstFrameAfterRestart = 0; int total = 0;
        FrameReader(LiveSession l, Path m, LiveWindow w, Deque<Frame> p, List<Frame> po) { live=l; movie=m; window=w; pre=p; post=po; }
        long readUntil(long deadline, java.util.function.BooleanSupplier done) throws Exception {
            while (System.nanoTime() < deadline) { readAvailable(); if (done.getAsBoolean()) return firstFrameAt; if (!live.process.isAlive()) break; Thread.sleep(POLL_MS); }
            return firstFrameAt;
        }
        void readAvailable() throws Exception {
            if (!Files.isRegularFile(movie)) return;
            long size = Files.size(movie); if (size <= position) return;
            pending.write(readRange(movie, position, size)); position=size;
            while (true) {
                byte[] jpeg=extractJpeg(pending); if (jpeg==null) break;
                BufferedImage img=ImageIO.read(new ByteArrayInputStream(jpeg)); if (img==null) continue;
                long now=System.nanoTime(); total++; if(firstFrameAt==0){firstFrameAt=now; log(window,"FIRST LIVE VIEW FRAME");}
                if (firstFrameAt != now && firstFrameAfterRestart == 0 && post.size() > 0) {
                    firstFrameAfterRestart = now;
                }
                Frame f=new Frame(total,now,jpeg,img);
                SwingUtilities.invokeLater(()->window.setImage(img));
                if (post.isEmpty() && pre.size()>0 && total>pre.peekLast().number) { post.add(f); if(firstFrameAfterRestart==0) firstFrameAfterRestart=now; }
                else if (post.size()>0) post.add(f);
                else { pre.addLast(f); trim(pre,now); }
                if(total%15==0) SwingUtilities.invokeLater(()->window.setStats(total, pre.size(), post.size()));
            }
        }
    }

    private static void trim(Deque<Frame> q,long now){long min=now-(long)(PRE_SECONDS*1_000_000_000L);while(q.size()>1&&q.peekFirst().timeNanos<min)q.removeFirst();while(q.size()>40)q.removeFirst();}
    private static byte[] extractJpeg(ByteArrayOutputStream b){byte[] a=b.toByteArray();int s=-1,e=-1;for(int i=0;i<a.length-1;i++){if((a[i]&255)==255&&(a[i+1]&255)==216){s=i;break;}}if(s<0)return null;for(int i=s+2;i<a.length-1;i++){if((a[i]&255)==255&&(a[i+1]&255)==217){e=i+2;break;}}if(e<0)return null;byte[] r=Arrays.copyOfRange(a,s,e);b.reset();b.write(a,e,a.length-e);return r;}
    private static byte[] readRange(Path f,long s,long e)throws Exception{try(InputStream in=Files.newInputStream(f)){long n=0;while(n<s){long x=in.skip(s-n);if(x<=0)break;n+=x;}return in.readNBytes((int)Math.min(Integer.MAX_VALUE,e-s));}}
    private static void export(Path d,List<Frame> fs,String type)throws Exception{int i=0;for(Frame f:fs)Files.write(d.resolve(String.format(Locale.US,"%04d_%s.jpg",++i,type)),f.jpeg);}
    private static void writeReport(Path d,Result r)throws Exception{Files.writeString(d.resolve("report.txt"),r.toString(),StandardCharsets.UTF_8);}

    private static String toMsysPath(Path p){String s=p.toAbsolutePath().toString().replace('\\','/');if(s.length()>=2&&s.charAt(1)==':')s="/"+Character.toLowerCase(s.charAt(0))+s.substring(2);return s;}
    private static String shellQuote(String s){return "'"+s.replace("'", "'\\''")+"'";}
    private static long msSince(long n){return (System.nanoTime()-n)/1_000_000L;}
    private static long msBetween(long a,long b){return (b-a)/1_000_000L;}
    private static long secondsToNanos(double seconds){return Math.round(seconds * 1_000_000_000.0);}
    private static String formatMs(long n){return n<0?"N/A":n+" ms";}
    private static String quote(String s){return "\""+s.replace("\r","\\r").replace("\n","\\n")+"\"";}
    private static void log(LiveWindow w,String s){DebugLog.info(s);System.out.println(s);SwingUtilities.invokeLater(()->w.append(s));}
    private static void logStatic(String s){DebugLog.info(s);System.out.println(s);}

    private record Frame(int number,long timeNanos,byte[] jpeg,BufferedImage image){}
    private record ProcessResult(int exitCode,String stdout,String stderr){}
    private record StillResult(boolean success,int width,int height,long finishedAt){}
    private record LiveSession(Process process,Collector stdout,Collector stderr){}
    private record Result(int preFrames,int postFrames,boolean stillSuccess,int width,int height,long stillElapsedMs,long stopElapsedMs,long restartElapsedMs,long postDelayMs,String liveStderr,String postStderr){
        @Override public String toString(){return "Phomoria gPhoto2 Live Photo STOP/RESTART Diagnostic\n\nPRE frames: "+preFrames+"\nPOST frames: "+postFrames+"\nStill success: "+stillSuccess+"\nStill size: "+width+"x"+height+"\nStill elapsed ms: "+stillElapsedMs+"\nLive stop elapsed ms: "+stopElapsedMs+"\nLive restart elapsed ms: "+restartElapsedMs+"\nFirst POST frame delay ms: "+postDelayMs+"\nLive stderr: "+liveStderr+"\nPost stderr: "+postStderr+"\n";}}
    private static final class Collector implements Runnable {private final InputStream in;private final ByteArrayOutputStream b=new ByteArrayOutputStream();Collector(InputStream i){in=i;}public void run(){try{in.transferTo(b);}catch(Exception ignored){}}String text(){return b.toString(StandardCharsets.UTF_8);}}

    private static final class LiveWindow extends JFrame{
        final JLabel image=new JLabel();final JLabel stats=new JLabel("Starting...");final JTextArea log=new JTextArea();
        LiveWindow(){setTitle("Phomoria - gPhoto2 STOP/RESTART Diagnostic");setSize(1100,800);setLocationRelativeTo(null);setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);setLayout(new BorderLayout());image.setHorizontalAlignment(SwingConstants.CENTER);image.setBackground(Color.BLACK);image.setOpaque(true);add(image,BorderLayout.CENTER);add(stats,BorderLayout.NORTH);log.setEditable(false);add(new JScrollPane(log),BorderLayout.SOUTH);}
        void setImage(BufferedImage i){image.setIcon(new ImageIcon(i));}void setStats(int total,int pre,int post){stats.setText("Decoded="+total+" PRE="+pre+" POST="+post);}void append(String s){log.append(s+"\n");}
    }
}
