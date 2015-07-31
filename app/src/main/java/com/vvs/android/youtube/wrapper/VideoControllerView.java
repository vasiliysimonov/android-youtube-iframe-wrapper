package com.vvs.android.youtube.wrapper;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.MediaController;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

import java.lang.ref.WeakReference;
import java.util.Formatter;
import java.util.Locale;

public class VideoControllerView extends FrameLayout {

    private static final int DEFAULT_TIMEOUT = 3000;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int START_PROGRESS = 3;

    private MediaController.MediaPlayerControl mPlayer;

    private Context context;
    private ViewGroup anchor;
    private View mainView;
    private SeekBar progressView;
    private TextView endTime, currentTime;
    private ImageButton fullscreenButton;
    private ImageButton pauseButton;

    private boolean showing;
    private boolean dragging;

    StringBuilder formatBuilder;
    Formatter formatter;

    private ControlInterface controlInterface;

    private final Handler handler = new MessageHandler(this);

    public VideoControllerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mainView = null;
        this.context = context;
    }

    public VideoControllerView(Context context) {
        super(context);
        this.context = context;
    }

    public void setControlInterface(ControlInterface controlInterface) {
        this.controlInterface = controlInterface;
    }

    @Override
    public void onFinishInflate() {
        if (mainView != null)
            initControllerView(mainView);
    }

    public void setMediaPlayer(MediaController.MediaPlayerControl player) {
        mPlayer = player;
        updatePausePlay();
        updateFullScreen();
    }

    /**
     * Set the view that acts as the anchor for the control view.
     * This can for example be a VideoView, or your Activity's main view.
     * @param view The view to which to anchor the controller when it is visible.
     */
    public void setAnchorView(ViewGroup view) {
        anchor = view;

        LayoutParams frameParams = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        removeAllViews();
        View v = makeControllerView();
        addView(v, frameParams);
    }

    // this violates incapsulation.
    // TODO move logic of this class to VideoPlayerFragment
    public View getPauseButton() {
        return pauseButton;
    }

    /**
     * Create the view that holds the widgets that control playback.
     * Derived classes can override this to create their own.
     * @return The controller view.
     * @hide This doesn't work as advertised
     */
    protected View makeControllerView() {
        LayoutInflater inflater = LayoutInflater.from(context);
        mainView = inflater.inflate(getLayoutId(), null, false);

        initControllerView(mainView);

        return mainView;
    }

    protected int getLayoutId() {
        return R.layout.media_controller;
    }

    private void initControllerView(View v) {

        fullscreenButton = (ImageButton) v.findViewById(R.id.fullscreen);
        if (fullscreenButton != null) {
            fullscreenButton.requestFocus();
            fullscreenButton.setOnClickListener(fullscreenListener);
        }

        pauseButton = (ImageButton) v.findViewById(R.id.pause);
        pauseButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                onTooglePlayPauseClick();
            }
        });

        View qualityButton = v.findViewById(R.id.quality);
        qualityButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (controlInterface != null) {
                    controlInterface.onQualityClick();
                }
            }
        });

        progressView = (SeekBar) v.findViewById(R.id.mediacontroller_progress);
        if (progressView != null) {
            if (progressView instanceof SeekBar) {
                SeekBar seeker = progressView;
                seeker.setOnSeekBarChangeListener(mSeekListener);
            }
            progressView.setMax(1000);
        }

        endTime = (TextView) v.findViewById(R.id.time);
        currentTime = (TextView) v.findViewById(R.id.time_current);
        formatBuilder = new StringBuilder();
        formatter = new Formatter(formatBuilder, Locale.getDefault());
    }

    /**
     * Show the controller on screen. It will go away
     * automatically after 3 seconds of inactivity.
     */
    public void show() {
        show(DEFAULT_TIMEOUT);
    }

    public void startProgress() {
        handler.sendEmptyMessage(START_PROGRESS);
    }

    /**
     * Disable pause or seek buttons if the stream cannot be paused or seeked.
     * This requires the control interface to be a MediaPlayerControlExt
     */
    private void disableUnsupportedButtons() {
        if (mPlayer == null) {
            return;
        }

        try {
            if (pauseButton != null && !mPlayer.canPause()) {
                pauseButton.setEnabled(false);
            }
        } catch (IncompatibleClassChangeError ex) {
            // We were given an old version of the interface, that doesn't have
            // the canPause/canSeekXYZ methods. This is OK, it just means we
            // assume the media can be paused and seeked, and so we don't disable
            // the buttons.
        }
    }

    public void show(int timeout) {
        if (!showing && anchor != null) {
            setProgress();
            if (pauseButton != null) {
                pauseButton.requestFocus();
            }
            disableUnsupportedButtons();

            LayoutParams tlp = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM
            );

            anchor.addView(this, tlp);

            if (controlInterface != null) {
                controlInterface.showControls();
            }

            updatePausePlay();
            updateFullScreen();

            setShowing(true);
        }

        // cause the progress bar to be updated even if mShowing
        // was already true.  This happens, for example, if we're
        // paused with the progress bar showing the user hits play.
        handler.sendEmptyMessage(SHOW_PROGRESS);

        Message msg = handler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            handler.removeMessages(FADE_OUT);
            handler.sendMessageDelayed(msg, timeout);
        }
    }

    public boolean isShowing() {
        return showing;
    }

    private void setShowing(boolean value) {
        if (showing != value) {
            showing = value;
            if (controlInterface != null) {
                controlInterface.onShowingChanged(showing);
            }
        }
    }

    /**
     * Remove the controller from the screen.
     */
    public void hide() {
        if (anchor == null || mPlayer == null || !mPlayer.isPlaying()) {
            return;
        }

        try {
            anchor.removeView(this);
            handler.removeMessages(SHOW_PROGRESS);
        } catch (IllegalArgumentException ex) {
            Log.w("MediaController", "already removed");
        }

        if (controlInterface != null)
            controlInterface.hideControls();

        setShowing(false);
    }

    private String stringForTime(int timeMs) {
        int totalSeconds = timeMs / 1000;

        int seconds = totalSeconds % 60;
        int minutes = (totalSeconds / 60) % 60;
        int hours   = totalSeconds / 3600;

        formatBuilder.setLength(0);
        if (hours > 0) {
            return formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString();
        } else {
            return formatter.format("%02d:%02d", minutes, seconds).toString();
        }
    }

    private int setProgress() {
        if (mPlayer == null || dragging) {
            return 0;
        }

        int position = mPlayer.getCurrentPosition();
        int duration = mPlayer.getDuration();
        setProgress(position, duration);

        return position;
    }

    public void updateProgressToFinish() {
        if (mPlayer == null) return;

        handler.removeMessages(SHOW_PROGRESS);
        int duration = mPlayer.getDuration();
        setProgress(duration, duration);
    }

    private void setProgress(int position, int duration)  {
        if (progressView != null) {
            if (duration > 0) {
                // use long to avoid overflow
                long pos = 1000L * position / duration;
                progressView.setProgress( (int) pos);
            }
            int percent = mPlayer.getBufferPercentage();
            progressView.setSecondaryProgress(percent * 10);
        }

        if (endTime != null)
            endTime.setText(stringForTime(duration));
        if (currentTime != null)
            currentTime.setText(stringForTime(position));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        show(DEFAULT_TIMEOUT);
        return true;
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        show(DEFAULT_TIMEOUT);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (mPlayer == null) {
            return true;
        }

        int keyCode = event.getKeyCode();
        final boolean uniqueDown = event.getRepeatCount() == 0
                && event.getAction() == KeyEvent.ACTION_DOWN;
        if (keyCode ==  KeyEvent.KEYCODE_HEADSETHOOK
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            if (uniqueDown) {
                doPauseResume();
                show(mPlayer.isPlaying() ? DEFAULT_TIMEOUT : 0);
                if (pauseButton != null) {
                    pauseButton.requestFocus();
                }
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
            if (uniqueDown && !mPlayer.isPlaying()) {
                mPlayer.start();
                updatePausePlay();
                show(DEFAULT_TIMEOUT);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
            if (uniqueDown && mPlayer.isPlaying()) {
                mPlayer.pause();
                updatePausePlay();
                show(0);
            }
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
            // don't show the controls for volume adjustment
            return super.dispatchKeyEvent(event);
        } else if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU) {
            if (uniqueDown) {
                hide();
            }
            return true;
        }

        show(DEFAULT_TIMEOUT);
        return super.dispatchKeyEvent(event);
    }

    public void onTooglePlayPauseClick() {
        if (mPlayer == null) return;
        if (controlInterface != null) {
            controlInterface.togglePlayPause(mPlayer.isPlaying());
        }
        doPauseResume();
        show(mPlayer.isPlaying() ? DEFAULT_TIMEOUT : 0);
    }

    public void onRepeatClick() {
        if (mPlayer == null) {
            return;
        }

        mPlayer.seekTo(0);
        mPlayer.start();
    }

    public void pause() {
        if (mPlayer == null) {
            return;
        }

        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        }
        updatePausePlay();
    }

    private OnClickListener fullscreenListener = new OnClickListener() {
        public void onClick(View v) {
            doToggleFullscreen();
            show(DEFAULT_TIMEOUT);
        }
    };

    public void updatePausePlay() {
        if (mainView == null || pauseButton == null || mPlayer == null) {
            return;
        }

        int resId = mPlayer.isPlaying() ? R.drawable.ic_player_pause : R.drawable.ic_player_play;
        pauseButton.setImageResource(resId);
    }

    public void updateFullScreen() {
        if (mainView == null || fullscreenButton == null || mPlayer == null) {
            return;
        }

        if (controlInterface.isFullScreen()) {
            fullscreenButton.setImageResource(R.drawable.ic_media_small);
        } else {
            fullscreenButton.setImageResource(R.drawable.ic_media_full);
        }
    }

    private void doPauseResume() {
        if (mPlayer == null) {
            return;
        }

        if (mPlayer.isPlaying()) {
            mPlayer.pause();
        } else {
            mPlayer.start();
        }
        updatePausePlay();
    }

    private void doToggleFullscreen() {
        if(controlInterface != null) {
            controlInterface.toggleFullScreen();
        }
    }

    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            dragging = true;
            handler.removeMessages(SHOW_PROGRESS);
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (mPlayer == null) {
                return;
            }

            if (!fromuser) {
                // We're not interested in programmatically generated changes to
                // the progress bar's position.
                return;
            }

            long duration = mPlayer.getDuration();
            long newposition = (duration * progress) / 1000L;
            mPlayer.seekTo( (int) newposition);
            if (currentTime != null)
                currentTime.setText(stringForTime( (int) newposition));
        }

        public void onStopTrackingTouch(SeekBar bar) {
            dragging = false;
            setProgress();
            updatePausePlay();
            show(DEFAULT_TIMEOUT);

            // Ensure that progress is properly updated in the future,
            // the call to show() does not guarantee this because it is a
            // no-op if we are already showing.
            handler.sendEmptyMessage(SHOW_PROGRESS);
        }
    };

    @Override
    public void setEnabled(boolean enabled) {
        if (pauseButton != null) {
            pauseButton.setEnabled(enabled);
        }
        if (progressView != null) {
            progressView.setEnabled(enabled);
        }
        disableUnsupportedButtons();
        super.setEnabled(enabled);
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeMessages(SHOW_PROGRESS);
        handler.removeMessages(FADE_OUT);
        super.onDetachedFromWindow();
    }

    public interface ControlInterface {
        void hideControls();
        void showControls();
        void toggleFullScreen();
        boolean isFullScreen();
        void onShowingChanged(boolean isShowing);
        void onQualityClick();
        void togglePlayPause(boolean isPlaying);
    }

    private static class MessageHandler extends Handler {
        private final WeakReference<VideoControllerView> mView;

        MessageHandler(VideoControllerView view) {
            mView = new WeakReference<>(view);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoControllerView view = mView.get();
            if (view == null || view.mPlayer == null) {
                return;
            }

            int pos;
            switch (msg.what) {
                case FADE_OUT:
                    view.hide();
                    break;
                case SHOW_PROGRESS:
                    pos = view.setProgress();
                    if (!view.dragging && view.showing) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case START_PROGRESS:
                    pos = view.setProgress();
                    msg = obtainMessage(SHOW_PROGRESS);
                    sendMessageDelayed(msg, 1000 - (pos % 1000));
                    break;
            }
        }
    }
}