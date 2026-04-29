package com.example.videodecoder;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
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
import android.widget.Toast;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {
    /* ----------------- 常量声明 ----------------- */
    private static final String TAG = "VideoDecoderMainActivity";
    /* ----------------- 成员变量 ----------------- */
    private Uri videoUri;
    private volatile PlaybackUiPolicy.PlaybackUiState playbackUiState = PlaybackUiPolicy.PlaybackUiState.IDLE;
    private Thread decodeThread;
    private volatile boolean isSurfaceReady = false;
    private volatile boolean isDestroyed = false;
    private Surface renderSurface;
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

        progressHandler.post(progressUpdater);

        configureVideoStage();
        configureVideoSurface();

        String initialStatus = stringFromJNI();
        LiquidGlassHelper.INSTANCE.setStatusText(initialStatus);

        setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.IDLE);

        // Compose overlay setup
        androidx.compose.ui.platform.ComposeView composeView = findViewById(R.id.compose_view);
        if (composeView != null) {
            LiquidGlassHelper.INSTANCE.setup(composeView);
            LiquidGlassHelper.INSTANCE.setSelectedSpeed(1.0f);
            LiquidGlassHelper.INSTANCE.setActions(new LiquidActions() {
                @Override
                public void onSelect() {
                    pickVideo();
                }

                @Override
                public void onDecode() {
                    startDecode();
                }

                @Override
                public void onPlay() {
                    if (!isDecodeRunning()) {
                        Toast.makeText(MainActivity.this, "请先开始解析视频", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (playbackUiState == PlaybackUiPolicy.PlaybackUiState.PAUSED) {
                        resumeDecoding();
                        setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.PLAYING);
                        Toast.makeText(MainActivity.this, "继续播放", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onPause() {
                    if (!isDecodeRunning()) {
                        Toast.makeText(MainActivity.this, "当前无可暂停的播放", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (playbackUiState == PlaybackUiPolicy.PlaybackUiState.PLAYING) {
                        pauseDecoding();
                        setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.PAUSED);
                        Toast.makeText(MainActivity.this, "暂停播放", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onSpeed(float speed) {
                    applyPlaybackSpeed(speed, "播放速度设置为" + speed + "倍");
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

    }
    // 新增native方法声明

    @SuppressWarnings("deprecation")
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
            LiquidGlassHelper.INSTANCE.setProgress(currentPosition, duration);
        });
    }

    private void pickVideo() {
        videoPickerLauncher.launch(new String[]{"video/*"});
    }

    private void startDecode() {
        if (decodeThread != null && decodeThread.isAlive()) {
            Toast.makeText(this, "正在解析中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isSurfaceReady) {
            Toast.makeText(this, "渲染界面尚未就绪，请稍后重试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (videoUri == null) {
            Toast.makeText(this, "请先选择视频文件", Toast.LENGTH_SHORT).show();
            return;
        }
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
        LiquidGlassHelper.INSTANCE.setSelectedSpeed(speed);
        setPlaybackSpeed(PlaybackInputPolicy.sanitizeSpeed(speed));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setStatusTextWithFade(String text) {
        if (text == null) {
            text = "";
        }
        LiquidGlassHelper.INSTANCE.setStatusText(text);
    }

    private void setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState state) {
        playbackUiState = state;
        runOnUiThread(() -> {
            LiquidGlassHelper.INSTANCE.setPlaying(state == PlaybackUiPolicy.PlaybackUiState.PLAYING);
            LiquidGlassHelper.INSTANCE.setPlaybackStateLabel(state.name());
        });
    }

    /* ----------------- 回调方法 ----------------- */
    public void onVideoDecoded(final String result) {
        runOnUiThread(() -> {
            if (result != null && !result.isEmpty()) {
                LiquidGlassHelper.INSTANCE.setStatusText(result);
            }
            if (result != null && result.endsWith(".yuv")) {
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
