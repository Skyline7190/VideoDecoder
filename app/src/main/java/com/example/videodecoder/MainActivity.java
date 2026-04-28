package com.example.videodecoder;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    /* ----------------- 常量声明 ----------------- */
    private static final String TAG = "VideoDecoderMainActivity";
    /* ----------------- 成员变量 ----------------- */
    private TextView tv;
    private Uri videoUri;
    private volatile PlaybackUiPolicy.PlaybackUiState playbackUiState = PlaybackUiPolicy.PlaybackUiState.IDLE;
    private Thread decodeThread;

    private Button selectVideoButton;
    private Button playButton;
    private Button pauseButton;
    private Button speed05Button;
    private Button speed1Button;
    private Button speed2Button;
    private Button speed3Button;
    private Button decodeVideoButton;
    private float selectedSpeed = 1.0f;
    private SeekBar seekBar;
    //

    private TextView currentTimeText;
    private TextView totalTimeText;
    private TextView headerStatusChip;
    private TextView previewStatusChip;
    private MaterialCardView headerCard;
    private MaterialCardView actionCard;
    private MaterialButton decodeButton;
    private LinearLayout speedLayout;
    private boolean isSeeking = false;
    private volatile boolean isSurfaceReady = false;
    private volatile boolean isDestroyed = false;
    private Surface renderSurface;
    private boolean hasUiStateApplied = false;
    private final AccelerateDecelerateInterpolator entranceInterpolator =
            new AccelerateDecelerateInterpolator();
    private final ActivityResultLauncher<String[]> videoPickerLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), this::handlePickedVideo);
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private final Runnable progressUpdater = new Runnable() {
        @Override
        public void run() {
            if (isDecodeRunning()) {
                int duration = getDurationMs();
                int current = getCurrentPositionMs();
                if (duration > 0) {
                    if (current < 0) current = 0;
                    if (current > duration) current = duration;
                    updateProgress(current, duration);
                }
                progressHandler.postDelayed(this, 200);
            } else {
                progressHandler.postDelayed(this, 600);
            }
        }
    };


    /* ----------------- JNI原生方法声明 ----------------- */
    public native void decodeVideo(String videoPath, String outputPath);
    // 新增 native 方法声明
    public native void setSurface(Surface surface);
    public native String stringFromJNI();
    public native void pauseDecoding();
    public native void resumeDecoding();
    public native void setPlaybackSpeed(float speed);
    public native void seekToPosition(int progressMs);
    public native void nativeReleaseAudio();
    public native int getDurationMs();
    public native int getCurrentPositionMs();

    //加载so库
    static {
        System.loadLibrary("native-lib");
    }
    /* ----------------- 生命周期方法 ----------------- */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureEdgeToEdgeWindow();
        setContentView(R.layout.activity_main);
        applySystemBarInsets();

        // 初始化SeekBar
        seekBar = findViewById(R.id.seek_bar);
        currentTimeText = findViewById(R.id.current_time_text);
        totalTimeText = findViewById(R.id.total_time_text);
        updateTimeText(0, 0);
        progressHandler.post(progressUpdater);
        // 设置SeekBar监听
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateTimeText(progress, Math.max(seekBar.getMax(), progress));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (isDecodeRunning()) {
                    seekToPosition(seekBar.getProgress());
                }
                isSeeking = false;
            }
        });

        speed05Button = findViewById(R.id.speed_05);
        speed1Button = findViewById(R.id.speed_1);
        speed2Button = findViewById(R.id.speed_2);
        speed3Button = findViewById(R.id.speed_3);
        headerStatusChip = findViewById(R.id.header_status_chip);
        previewStatusChip = findViewById(R.id.preview_status_chip);
        headerCard = findViewById(R.id.header_card);
        actionCard = findViewById(R.id.action_card);
        speedLayout = findViewById(R.id.speed_layout);



        speed05Button.setOnClickListener(v -> {
            applyPlaybackSpeed(0.5f, "播放速度设置为0.5倍");
        });
        speed1Button.setOnClickListener(v -> {
            applyPlaybackSpeed(1.0f, "播放速度设置为1倍");
        });
        speed2Button.setOnClickListener(v -> {
            applyPlaybackSpeed(2.0f, "播放速度设置为2倍");
        });
        speed3Button.setOnClickListener(v -> {
            applyPlaybackSpeed(3.0f, "播放速度设置为3倍");
        });
        configureVideoStage();
        configureVideoSurface();





        tv = findViewById(R.id.sample_text);
        if (headerCard != null) {
            headerCard.setVisibility(View.INVISIBLE);
        }
        if (previewStatusChip != null) {
            previewStatusChip.setVisibility(View.INVISIBLE);
        }
        selectVideoButton = findViewById(R.id.select_video_button);
        decodeVideoButton = findViewById(R.id.decode_video_button);
        decodeButton = findViewById(R.id.decode_video_button);

        playButton = findViewById(R.id.play_button);
        pauseButton = findViewById(R.id.pause_button);

        playButton.setOnClickListener(v -> {
            if (!isDecodeRunning()) {
                Toast.makeText(this, "请先开始解析视频", Toast.LENGTH_SHORT).show();
                return;
            }
            if (playbackUiState == PlaybackUiPolicy.PlaybackUiState.PAUSED) {
                resumeDecoding();
                setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.PLAYING);
                Toast.makeText(this, "继续播放", Toast.LENGTH_SHORT).show();
            }
        });

        pauseButton.setOnClickListener(v -> {
            if (!isDecodeRunning()) {
                Toast.makeText(this, "当前无可暂停的播放", Toast.LENGTH_SHORT).show();
                return;
            }
            if (playbackUiState == PlaybackUiPolicy.PlaybackUiState.PLAYING) {
                pauseDecoding();
                setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.PAUSED);
                Toast.makeText(this, "暂停播放", Toast.LENGTH_SHORT).show();
            }
        });

        String initialStatus = stringFromJNI();
        tv.setText(initialStatus);
        LiquidGlassHelper.INSTANCE.setStatusText(initialStatus);
        // 监听按钮点击事件
        selectVideoButton.setOnClickListener(v -> pickVideo());

        decodeVideoButton.setOnClickListener(v -> {
            if (decodeThread != null && decodeThread.isAlive()) {
                Toast.makeText(this, "正在解析中，请稍候", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isSurfaceReady) {
                Toast.makeText(this, "渲染界面尚未就绪，请稍后重试", Toast.LENGTH_SHORT).show();
                return;
            }
            if (videoUri != null) {
                final Uri selectedUri = videoUri;
                final boolean[] decodeFailed = {false};
                setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.PLAYING);
                decodeThread = new Thread(() -> {
                    runOnUiThread(() -> setStatusTextWithFade("正在解析视频..."));
                    try {
                        try (ParcelFileDescriptor videoFd = openVideoFileDescriptor(selectedUri)) {
                            decodeVideo("fd:" + videoFd.getFd(), null);
                        }
                    } catch (IOException e) {
                        decodeFailed[0] = true;
                        Log.e(TAG, "Failed to open selected video file descriptor", e);
                        runOnUiThread(() -> {
                            setStatusTextWithFade("无法读取文件");
                            Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                        });
                    } finally {
                        decodeThread = null;
                        if (!decodeFailed[0]) {
                            runOnUiThread(() -> setStatusTextWithFade("解析结束，可重新选择或再次解析"));
                        }
                        if (!isSurfaceReady) {
                            setSurface(null);
                        }
                        if (!isDestroyed) {
                            setPlaybackUiState(videoUri == null
                                    ? PlaybackUiPolicy.PlaybackUiState.IDLE
                                    : PlaybackUiPolicy.PlaybackUiState.READY);
                        }
                    }
                }, "video-decode-runner");
                decodeThread.start();
            } else {
                Toast.makeText(this, "请先选择视频文件", Toast.LENGTH_SHORT).show();
            }
        });

        setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.IDLE);

        startEntranceAnimations();

        // ----------------- 新增：集成Liquid Glass Compose UI -----------------
        androidx.compose.ui.platform.ComposeView composeView = findViewById(R.id.compose_view);
        if (composeView != null) {
            LiquidGlassHelper.INSTANCE.setup(composeView);
            LiquidGlassHelper.INSTANCE.setSelectedSpeed(selectedSpeed);
            LiquidGlassHelper.INSTANCE.setActions(new LiquidActions() {
                @Override
                public void onSelect() {
                    selectVideoButton.performClick();
                }

                @Override
                public void onDecode() {
                    decodeVideoButton.performClick();
                }

                @Override
                public void onPlay() {
                    playButton.performClick();
                }

                @Override
                public void onPause() {
                    pauseButton.performClick();
                }

                @Override
                public void onSpeed(float speed) {
                    if (speed == 0.5f) speed05Button.performClick();
                    else if (speed == 1.0f) speed1Button.performClick();
                    else if (speed == 2.0f) speed2Button.performClick();
                    else if (speed == 3.0f) speed3Button.performClick();
                }

                @Override
                public void onSeekTo(int positionMs) {
                    if (isDecodeRunning()) {
                        seekToPosition(positionMs);
                        updateProgress(positionMs, Math.max(getDurationMs(), positionMs));
                    }
                }
            });
        }
        // ------------------------------------------------------------------

    }
    // 新增native方法声明

    private void configureEdgeToEdgeWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);

        boolean lightSystemBars = shouldUseLightSystemBarIcons();
        View decorView = window.getDecorView();
        int flags = decorView.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (lightSystemBars) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (lightSystemBars) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decorView.setSystemUiVisibility(flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightSystemBars ? lightBars : 0, lightBars);
            }
        }
    }

    private boolean shouldUseLightSystemBarIcons() {
        int nightMode = getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode != Configuration.UI_MODE_NIGHT_YES;
    }

    private void applySystemBarInsets() {
        View playerContent = findViewById(R.id.player_content);
        if (playerContent == null) {
            return;
        }

        final int initialLeft = playerContent.getPaddingLeft();
        final int initialTop = playerContent.getPaddingTop();
        final int initialRight = playerContent.getPaddingRight();
        final int initialBottom = playerContent.getPaddingBottom();

        playerContent.setOnApplyWindowInsetsListener((view, insets) -> {
            int insetLeft;
            int insetTop;
            int insetRight;
            int insetBottom;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets systemBars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                insetLeft = systemBars.left;
                insetTop = systemBars.top;
                insetRight = systemBars.right;
                insetBottom = systemBars.bottom;
            } else {
                insetLeft = insets.getSystemWindowInsetLeft();
                insetTop = insets.getSystemWindowInsetTop();
                insetRight = insets.getSystemWindowInsetRight();
                insetBottom = insets.getSystemWindowInsetBottom();
            }

            view.setPadding(
                    initialLeft + insetLeft,
                    initialTop + insetTop,
                    initialRight + insetRight,
                    initialBottom + insetBottom
            );
            return insets;
        });
        playerContent.requestApplyInsets();
    }

    private void configureVideoStage() {
        View videoStage = findViewById(R.id.video_stage);
        if (videoStage == null) {
            return;
        }

        videoStage.post(() -> {
            int stageWidth = videoStage.getWidth();
            if (stageWidth <= 0) {
                return;
            }

            int stageHeight = Math.round(stageWidth * 9f / 16f);
            ViewGroup.LayoutParams layoutParams = videoStage.getLayoutParams();
            if (layoutParams != null && layoutParams.height != stageHeight) {
                layoutParams.height = stageHeight;
                videoStage.setLayoutParams(layoutParams);
            }
        });
    }

    private void configureVideoSurface() {
        TextureView textureView = findViewById(R.id.surface_view);
        if (textureView == null) {
            Toast.makeText(this, "TextureView 未找到", Toast.LENGTH_SHORT).show();
            return;
        }

        textureView.setOpaque(true);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                if (width > 0 && height > 0) {
                    surfaceTexture.setDefaultBufferSize(width, height);
                }
                attachRenderSurface(surfaceTexture);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                if (width > 0 && height > 0) {
                    surfaceTexture.setDefaultBufferSize(width, height);
                    attachRenderSurface(surfaceTexture);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                isSurfaceReady = false;
                if (isDecodeRunning()) {
                    nativeReleaseAudio();
                }
                setSurface(null);
                releaseRenderSurface();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}
        });

        if (textureView.isAvailable()) {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null) {
                int width = textureView.getWidth();
                int height = textureView.getHeight();
                if (width > 0 && height > 0) {
                    surfaceTexture.setDefaultBufferSize(width, height);
                }
                attachRenderSurface(surfaceTexture);
            }
        }
    }

    private void attachRenderSurface(SurfaceTexture surfaceTexture) {
        if (surfaceTexture == null) {
            return;
        }

        releaseRenderSurface();
        renderSurface = new Surface(surfaceTexture);
        setSurface(renderSurface);
        isSurfaceReady = true;
    }

    private void releaseRenderSurface() {
        if (renderSurface != null) {
            setSurface(null);
            renderSurface.release();
            renderSurface = null;
        }
    }


    // 新增方法：更新进度条和时间显示
    public void updateProgress(final int currentPosition, final int duration) {
        runOnUiThread(() -> {
            if (!isSeeking) {
                seekBar.setMax(duration);
                seekBar.setProgress(currentPosition);
                updateTimeText(currentPosition, duration);
                LiquidGlassHelper.INSTANCE.setProgress(currentPosition, duration);
            }
        });
    }

    // 新增方法：格式化时间显示
    private void updateTimeText(int currentMs, int totalMs) {
        currentTimeText.setText(PlaybackTimeFormatter.formatTime(currentMs, Locale.getDefault()));
        totalTimeText.setText(PlaybackTimeFormatter.formatTime(totalMs, Locale.getDefault()));
    }

    private void pickVideo() {
        videoPickerLauncher.launch(new String[]{"video/*"});
    }

    private void handlePickedVideo(Uri uri) {
        if (uri == null) {
            return;
        }
        if (isDecodeRunning()) {
            Toast.makeText(this, "正在解析中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // Validate the descriptor once so playback can fail early on unreadable providers.
            try (ParcelFileDescriptor ignored = openVideoFileDescriptor(uri)) {
                videoUri = uri;
            }
            setStatusTextWithFade("已选择视频：" + uri);
            updateProgress(0, 0);
            setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.READY);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read selected video file", e);
            setStatusTextWithFade("无法读取文件");
        }

        // 并非所有文档提供者都支持持久化授权，失败时保持播放流程可用
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException e) {
            Log.w(TAG, "Persistable URI permission not granted by provider: " + uri, e);
        }
    }
    /* ----------------- 文件操作辅助方法 ----------------- */
    private ParcelFileDescriptor openVideoFileDescriptor(Uri uri) throws IOException {
        ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "r");
        if (descriptor == null) {
            throw new IOException("无法打开文件描述符");
        }
        return descriptor;
    }

    private boolean isDecodeRunning() {
        return decodeThread != null && decodeThread.isAlive();
    }

    private void applyPlaybackSpeed(float speed, String message) {
        if (!isDecodeRunning()) {
            Toast.makeText(this, "请先开始解析视频", Toast.LENGTH_SHORT).show();
            return;
        }
        selectedSpeed = speed;
        LiquidGlassHelper.INSTANCE.setSelectedSpeed(speed);
        setPlaybackSpeed(PlaybackInputPolicy.sanitizeSpeed(speed));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void startEntranceAnimations() {
        View[] cards = {
                findViewById(R.id.header_card),
                findViewById(R.id.preview_card),
                findViewById(R.id.progress_card),
                findViewById(R.id.speed_card),
                findViewById(R.id.action_card),
                findViewById(R.id.status_card)
        };

        for (int i = 0; i < cards.length; i++) {
            animateEntrance(cards[i], i * 70L, 380L, 14f);
        }

        View[] buttons = {
                speed05Button,
                speed1Button,
                speed2Button,
                speed3Button,
                playButton,
                pauseButton,
                selectVideoButton,
                decodeVideoButton
        };

        long buttonBaseDelay = cards.length * 70L;
        for (int i = 0; i < buttons.length; i++) {
            animateEntrance(buttons[i], buttonBaseDelay + (i * 45L), 280L, 8f);
        }
    }

    private void animateEntrance(View view, long delayMs, long durationMs, float offsetDp) {
        if (view == null) {
            return;
        }

        float targetAlpha = view.getAlpha();
        view.setAlpha(0f);
        view.setTranslationY(dpToPx(offsetDp));
        view.animate()
                .alpha(targetAlpha)
                .translationY(0f)
                .setStartDelay(delayMs)
                .setDuration(durationMs)
                .setInterpolator(entranceInterpolator)
                .start();
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void setStatusTextWithFade(String text) {
        if (text == null) {
            text = "";
        }
        LiquidGlassHelper.INSTANCE.setStatusText(text);

        if (tv == null) {
            return;
        }

        tv.animate().cancel();
        String nextText = text;
        tv.animate()
                .alpha(0f)
                .setDuration(90L)
                .setInterpolator(entranceInterpolator)
                .withEndAction(() -> {
                    tv.setText(nextText);
                    tv.animate()
                            .alpha(1f)
                            .setDuration(90L)
                            .setInterpolator(entranceInterpolator)
                            .start();
                })
                .start();
    }

    private void setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState state) {
        playbackUiState = state;
        runOnUiThread(() -> {
            
            // 同步给Liquid Glass UI
            LiquidGlassHelper.INSTANCE.setPlaying(state == PlaybackUiPolicy.PlaybackUiState.PLAYING);
            LiquidGlassHelper.INSTANCE.setPlaybackStateLabel(state.name());

            PlaybackUiPolicy.ControlState controlState = PlaybackUiPolicy.resolve(state);
            updateUiStateColorScheme(state);
            if (decodeVideoButton != null) {
                applyControlFade(decodeVideoButton, controlState.decodeEnabled, 0.45f);
                decodeVideoButton.setText(controlState.decodeText);
            }
            if (playButton != null) {
                applyControlFade(playButton, controlState.playEnabled, 0.45f);
            }
            if (pauseButton != null) {
                applyControlFade(pauseButton, controlState.pauseEnabled, 0.45f);
            }

            if (speed05Button != null) applyControlFade(speed05Button, controlState.speedEnabled, 0.45f);
            if (speed1Button != null) applyControlFade(speed1Button, controlState.speedEnabled, 0.45f);
            if (speed2Button != null) applyControlFade(speed2Button, controlState.speedEnabled, 0.45f);
            if (speed3Button != null) applyControlFade(speed3Button, controlState.speedEnabled, 0.45f);
            if (seekBar != null) applyControlFade(seekBar, controlState.seekEnabled, 0.45f);

            View progressCard = findViewById(R.id.progress_card);
            View speedCard = findViewById(R.id.speed_card);
            applyViewFade(progressCard, controlState.seekEnabled ? 1f : 0.62f);
            applyViewFade(speedCard, controlState.speedEnabled ? 1f : 0.62f);

            hasUiStateApplied = true;
        });
    }

    private void updateUiStateColorScheme(PlaybackUiPolicy.PlaybackUiState state) {
        StateColorScheme scheme = resolveStateColorScheme(state);

        if (headerStatusChip != null) {
            headerStatusChip.setText(scheme.label);
            headerStatusChip.setBackgroundTintList(ColorStateList.valueOf(resolveColor(scheme.chipBackgroundColorRes)));
            headerStatusChip.setTextColor(resolveColor(scheme.chipTextColorRes));
        }

        if (previewStatusChip != null) {
            previewStatusChip.setText(scheme.label);
            previewStatusChip.setBackgroundTintList(ColorStateList.valueOf(resolveColor(scheme.chipBackgroundColorRes)));
            previewStatusChip.setTextColor(resolveColor(scheme.chipTextColorRes));
        }

        if (headerCard != null) {
            headerCard.setCardBackgroundColor(0x33FFFFFF);
        }

        if (actionCard != null) {
            actionCard.setCardBackgroundColor(resolveColor(scheme.cardBackgroundColorRes));
        }

        if (decodeButton != null) {
            applyButtonTint(decodeButton, scheme.buttonBackgroundColorRes, scheme.buttonTextColorRes);
        }

        applyButtonTint(playButton, scheme.buttonBackgroundColorRes, scheme.buttonTextColorRes);
        applyButtonTint(pauseButton, scheme.buttonBackgroundColorRes, scheme.buttonTextColorRes);
        applyButtonTint(selectVideoButton, scheme.buttonBackgroundColorRes, scheme.buttonTextColorRes);
        applyButtonTint(speed05Button, scheme.buttonBackgroundColorRes, scheme.buttonTextColorRes);
        applyButtonTint(speed1Button, scheme.buttonBackgroundColorRes, scheme.buttonTextColorRes);
        applyButtonTint(speed2Button, scheme.buttonBackgroundColorRes, scheme.buttonTextColorRes);
        applyButtonTint(speed3Button, scheme.buttonBackgroundColorRes, scheme.buttonTextColorRes);

        if (speedLayout != null) {
            int accent = resolveColor(scheme.buttonBackgroundColorRes);
            if (seekBar != null) {
                seekBar.setProgressTintList(ColorStateList.valueOf(accent));
                seekBar.setThumbTintList(ColorStateList.valueOf(accent));
            }
        }
    }

    private StateColorScheme resolveStateColorScheme(PlaybackUiPolicy.PlaybackUiState state) {
        switch (state) {
            case PLAYING:
                return new StateColorScheme(
                        "UNDERGO",
                        R.color.liquid_state_playing_chip_bg,
                        R.color.liquid_state_playing_chip_text,
                        R.color.liquid_state_playing_card_bg,
                        R.color.liquid_state_playing_button_bg,
                        R.color.liquid_state_playing_button_text
                );
            case PAUSED:
                return new StateColorScheme(
                        "NEAR",
                        R.color.liquid_state_paused_chip_bg,
                        R.color.liquid_state_paused_chip_text,
                        R.color.liquid_state_paused_card_bg,
                        R.color.liquid_state_paused_button_bg,
                        R.color.liquid_state_paused_button_text
                );
            case IDLE:
                return new StateColorScheme(
                        "PASSED",
                        R.color.liquid_state_idle_chip_bg,
                        R.color.liquid_state_idle_chip_text,
                        R.color.liquid_state_idle_card_bg,
                        R.color.liquid_state_idle_button_bg,
                        R.color.liquid_state_idle_button_text
                );
            case READY:
            default:
                return new StateColorScheme(
                        "COMPLETED",
                        R.color.liquid_state_ready_chip_bg,
                        R.color.liquid_state_ready_chip_text,
                        R.color.liquid_state_ready_card_bg,
                        R.color.liquid_state_ready_button_bg,
                        R.color.liquid_state_ready_button_text
                );
        }
    }

    private int resolveColor(int colorResId) {
        return ContextCompat.getColor(this, colorResId);
    }

    private void applyButtonTint(Button button, int backgroundColorRes, int textColorRes) {
        if (button == null) {
            return;
        }

        button.setBackgroundTintList(ColorStateList.valueOf(resolveColor(backgroundColorRes)));
        button.setTextColor(resolveColor(textColorRes));
    }

    private static final class StateColorScheme {
        final String label;
        final int chipBackgroundColorRes;
        final int chipTextColorRes;
        final int cardBackgroundColorRes;
        final int buttonBackgroundColorRes;
        final int buttonTextColorRes;

        StateColorScheme(
                String label,
                int chipBackgroundColorRes,
                int chipTextColorRes,
                int cardBackgroundColorRes,
                int buttonBackgroundColorRes,
                int buttonTextColorRes
        ) {
            this.label = label;
            this.chipBackgroundColorRes = chipBackgroundColorRes;
            this.chipTextColorRes = chipTextColorRes;
            this.cardBackgroundColorRes = cardBackgroundColorRes;
            this.buttonBackgroundColorRes = buttonBackgroundColorRes;
            this.buttonTextColorRes = buttonTextColorRes;
        }
    }

    private void applyControlFade(View view, boolean enabled, float disabledAlpha) {
        if (view == null) {
            return;
        }

        view.setEnabled(enabled);
        applyViewFade(view, enabled ? 1f : disabledAlpha);
    }

    private void applyViewFade(View view, float targetAlpha) {
        if (view == null) {
            return;
        }

        if (!hasUiStateApplied) {
            view.setAlpha(targetAlpha);
            return;
        }

        view.animate()
                .alpha(targetAlpha)
                .setDuration(180L)
                .setInterpolator(entranceInterpolator)
                .start();
    }

    /* ----------------- 回调方法 ----------------- */
    public void onVideoDecoded(final String result) {
        runOnUiThread(() -> {
            if (result != null && !result.isEmpty()) {
                tv.append("\n" + result);
                LiquidGlassHelper.INSTANCE.setStatusText(result);
            }
            // 添加YUV文件路径显示
            if(result != null && result.endsWith(".yuv")) {
                Toast.makeText(this, "YUV文件已生成: " + result, Toast.LENGTH_LONG).show();
            }
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        isDestroyed = true;
        progressHandler.removeCallbacks(progressUpdater);
        if (decodeThread != null && decodeThread.isAlive()) {
            decodeThread.interrupt();
        }
        isSurfaceReady = false;
        setPlaybackUiState(videoUri == null
                ? PlaybackUiPolicy.PlaybackUiState.IDLE
                : PlaybackUiPolicy.PlaybackUiState.READY);
        setSurface(null);
        releaseRenderSurface();
        // 释放 Native 层音频资源
        nativeReleaseAudio();
    }

}
