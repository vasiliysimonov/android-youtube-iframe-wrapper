package com.vvs.android.youtube.wrapper;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.MediaController;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String ARG_VIDEO_ID = "arg.videoId";
    private static final String ARG_FIX_REQUEST = "arg.fix-request-id";
    private final String TAG_CHILD = "tag.youtube";

    private String videoId;
    private WebView webView;
    private boolean videoPlaying; // TODO save to state
    private View gradientView;
    private WebInterface webInterface;
    VideoControllerView mediaController;

    /*public static YoutubeFragment forVideo(Uri videoUri, int fixProblemRequestId) throws RuntimeException {
        String videoId = parseVideoId(videoUri);
        Bundle args = new Bundle();
        args.putString(ARG_VIDEO_ID, videoId);
        args.putInt(ARG_FIX_REQUEST, fixProblemRequestId);

        YoutubeFragment fragment = new YoutubeFragment();
        fragment.setArguments(args);
        return fragment;
    }*/

    /*private static String parseVideoId(Uri uri) {
        if (uri.getHost() == null) throw new IllegalArgumentException("Illegal uri");

        String host = uri.getHost().toLowerCase();
        String path = uri.getPath().replaceFirst("^/", "");
        switch (host) {
            case "m.youtube.com":
            case "www.youtube.com":
            case "youtube.com": {
                String[] pathParts = path.split("/");
                if (pathParts.length == 1 && pathParts[0].equalsIgnoreCase("watch")) {
                    return uri.getQueryParameter("v");
                }
                if (pathParts.length == 2 && pathParts[0].equalsIgnoreCase("embed")) {
                    return pathParts[1];
                }
                throw new IllegalArgumentException("Unknown url scheme");
            }
            case "youtu.be":
                return path;
            default:
                throw new IllegalArgumentException("Host is not youtube");
        }
    }*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_youtube);

        webView = (WebView) findViewById(R.id.player_web_view);
        webView.getSettings().setJavaScriptEnabled(true);
        if (Build.VERSION.SDK_INT >= 17) {
            webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        }
        webView.getSettings().setPluginState(WebSettings.PluginState.ON);
        webInterface = new WebInterface();
        webView.addJavascriptInterface(webInterface, "AndroidCallbacks");

        gradientView = findViewById(R.id.gradient_view);
        gradientView.setBackgroundDrawable(PlayerUtil.createGradient(
                new int[]{Color.BLACK, Color.TRANSPARENT, Color.TRANSPARENT, Color.BLACK},
                new float[]{0, 0.3f, 0.8f, 1}));
        gradientView.setVisibility(View.INVISIBLE);

        mediaController = new VideoControllerView(this);
        mediaController.setControlInterface(controlInterface);
        mediaController.setMediaPlayer(playerInterface);
        mediaController.setEnabled(true);

        ViewGroup anchor = (ViewGroup) findViewById(R.id.player_controls_anchor);
        mediaController.setAnchorView(anchor);

        webView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mediaController.show();
                }
                return false; // TODO?
            }
        });

        String playerArgs = "{" +
                "width: '100%', " +
                "height: '100%', " +
                "videoId: '" + "1afDwoPd_e8" + "', " +
                "events: { " +
                "'onReady' : onReady, " +
                "'onStateChange' : onStateChange, " +
                "'onPlaybackQualityChange' : onPlaybackQualityChange, " +
                "'onPlayerError' : onPlayerError " +
                " }" +
                " }";

        InputStream is = null;
        try {
            is = getAssets().open("YTPlayerView-iframe-player.html");
            //String htmlTemplate = IOUtils.inputStreamToString(is);
            String html = IOUtils.inputStreamToString(is); //String.format(htmlTemplate, playerArgs);
            webView.loadData(html, "text/html", "UTF-8");
        } catch (IOException e) {
            // TODO
        } finally {
            IOUtils.closeSilently(is);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webInterface.printStats();
    }

    public class WebInterface {

        private CountDownLatch countDown;
        private String jsResult;
        private Map<String, long[]> successTimes = new HashMap<>();
        private Map<String, int[]> successCounts = new HashMap<>();
        private Map<String, int[]> failCounts = new HashMap<>();

        @SuppressWarnings("unused")
        @JavascriptInterface
        public void onResult(String data) {
            Log.d(TAG, "JS result " + data);
            jsResult = data;
            countDown.countDown();
        }

        @SuppressWarnings("unused")
        @JavascriptInterface
        public void onReady(String eventData) {
            Log.d(TAG, "onReady");
        }

        @SuppressWarnings("unused")
        @JavascriptInterface
        public void onStateChange(final String eventData) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    PlaybackState state = PlaybackState.byId(eventData);
                    onYoutubeStateChange(state);
                }
            });
        }

        @SuppressWarnings("unused")
        @JavascriptInterface
        public void onPlaybackQualityChange(String eventData) {
            Log.d(TAG, "onPlaybackQualityChange: " + eventData + " thread: " + Thread.currentThread());
        }

        @SuppressWarnings("unused")
        @JavascriptInterface
        public void onError(String eventData) {
            Log.d(TAG, "onError: " + eventData + " thread: " + Thread.currentThread());
        }

        @SuppressWarnings("unused")
        @JavascriptInterface
        public void onGotDuration(String data) {
            Log.d(TAG, "onGetDuration: " + data);
        }

        @SuppressWarnings("unused")
        @JavascriptInterface
        public void onGetQuality(String data) {
            Log.d(TAG, "onGetQuality: " + data);
        }

        private String callJsForResult(String code) {

            countDown = new CountDownLatch(1);
            long t = System.currentTimeMillis();
            webView.loadUrl("javascript:AndroidCallbacks.onResult(" + code + ")");
            try {
                if (countDown.await(1000, TimeUnit.MILLISECONDS)) {
                    addTime(successTimes, code, t);
                    addCount(successCounts, code);
                    Log.v(TAG, code + "=" + jsResult + " in " + (System.currentTimeMillis() - t) + "ms");
                    return jsResult;
                } else {
                    addCount(failCounts, code);
                    Log.v(TAG, code + " timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return null;
        }

        private void addTime(Map<String, long[]> times, String method, long startTime) {
            long t = System.currentTimeMillis() - startTime;
            long[] time = times.get(method);
            if (time == null) {
                times.put(method, time = new long[]{0});
            }
            time[0] += t;
        }

        private void addCount(Map<String, int[]> counts, String method) {
            int[] count = counts.get(method);
            if (count == null) {
                counts.put(method, count = new int[]{0});
            }
            count[0]++;
        }

        private void printStats() {
            Set<String> unifiedKeys = new HashSet<>(webInterface.successCounts.keySet());
            unifiedKeys.addAll(webInterface.failCounts.keySet());
            for (String code : unifiedKeys) {
                long[] times = webInterface.successTimes.get(code);
                int[] successes = webInterface.successCounts.get(code);
                int[] fails = webInterface.failCounts.get(code);
                int success = successes == null ? 0 : successes[0];
                int fail = fails == null ? 0 : fails[0];
                long time = times == null ? 0 : times[0];
                Log.i(TAG, code + " Nsuccess=" + success + " Nfail=" + fail + " AVGtime=" + (success == 0 ? 0 : time / (1000.0 * success)));
            }
            successCounts.clear();
            successTimes.clear();
            failCounts.clear();
        }
    }

    private void onYoutubeStateChange(PlaybackState state) {
        Log.d(TAG, "onStateChange");
        if (state == null) return;

        playerInterface.setState(state);
        switch (state) {
            case Unstarted:
                break;
            case Ended:
                videoPlaying = false;
                break;
            case Playing:
                if (!videoPlaying) {
                    mediaController.show();
                    videoPlaying = true;
                }
                mediaController.updatePausePlay();
                break;
            case Paused:
                mediaController.updatePausePlay();
                break;
            case Buffering:
                break;
            case VideoCued:
                break;
        }
    }

    private final YoutubePlayerInterface playerInterface = new YoutubePlayerInterface();

    private class YoutubePlayerInterface implements MediaController.MediaPlayerControl {
        private PlaybackState state;

        @Override
        public void start() {
            webView.loadUrl("javascript:player.playVideo();");
        }

        @Override
        public void pause() {
            webView.loadUrl("javascript:player.pauseVideo();");
        }

        @Override
        public int getDuration() {
            String s = webInterface.callJsForResult("player.getDuration()");
            return s !=null ? (int) (Float.valueOf(s) * 1000) : 0;
        }

        @Override
        public int getCurrentPosition() {
            String s = webInterface.callJsForResult("player.getCurrentTime()");
            return s != null ? (int) (Float.valueOf(s) * 1000) : 0;
        }

        @Override
        public void seekTo(int pos) {
            webView.loadUrl("javascript:player.seekTo(" + (pos / 1000) + ", true);");
        }

        @Override
        public boolean isPlaying() {
            if (state == null) {
                String s = webInterface.callJsForResult("player.getPlayerState()");
                if (s == null) return false;
                state = PlaybackState.byId(s);
            }

            switch (state) {
                case Playing:
                case Buffering:
                case VideoCued:
                    return true;

                case Unstarted:
                case Ended:
                case Paused:
                default:
                    return false;
            }
        }

        @Override
        public int getBufferPercentage() {
            String s = webInterface.callJsForResult("player.getVideoLoadedFraction()");
            return s != null ? (int) (Float.valueOf(s) * 100) : 0;
        }

        @Override
        public boolean canPause() {
            return true; // TODO
        }

        @Override
        public boolean canSeekBackward() {
            return true;
        }

        @Override
        public boolean canSeekForward() {
            return true;
        }

        @Override
        public int getAudioSessionId() {
            return 0;
        }

        private void setState(PlaybackState state) {
            this.state = state;
        }
    }

    private VideoControllerView.ControlInterface controlInterface = new VideoControllerView.ControlInterface() {
        @Override
        public void hideControls() {

        }

        @Override
        public void showControls() {

        }

        @Override
        public void toggleFullScreen() {

        }

        @Override
        public boolean isFullScreen() {
            return false;
        }

        @Override
        public void onShowingChanged(boolean isShowing) {
            gradientView.setVisibility(isShowing ? View.VISIBLE : View.INVISIBLE);
        }

        @Override
        public void onQualityClick() {

        }

        @Override
        public void togglePlayPause(boolean isPlaying) {

        }
    };

    enum PlaybackState {
        Unstarted(-1),
        Ended(0),
        Playing(1),
        Paused(2),
        Buffering(3),
        VideoCued(5);

        final int id;

        PlaybackState(int id) {
            this.id = id;
        }

        static PlaybackState byId(String s) {
            int i = Integer.valueOf(s);
            for (PlaybackState v : values()) {
                if (v.id == i) return v;
            }
            return null;
        }
    }

    private enum VideoQuality {
        Small("small"),
        Medium("medium"),
        Large("large"),
        HD720("hd720"),
        HD1080("hd1080"),
        HighRes("highres"),
        Auto("auto"),
        Default("default"),
        Unknown("unknown");

        final String value;

        VideoQuality(String q) {
            this.value = q;
        }

        VideoQuality fromString(String s) {
            for (VideoQuality q : values()) {
                if (q.value.equalsIgnoreCase(s)) return q;
            }
            return Unknown;
        }
    }
}
