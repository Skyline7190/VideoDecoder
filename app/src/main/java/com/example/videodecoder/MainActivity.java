package com.example.videodecoder;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    /* ----------------- 常量声明 ----------------- */
    private static final String TAG = "VideoDecoderMainActivity";
    /* ----------------- 成员变量 ----------------- */
    private TextView tv;
    private String videoPath;
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
    private SeekBar seekBar;
    //

    private TextView currentTimeText;
    private TextView totalTimeText;
    private boolean isSeeking = false;
    private volatile boolean isSurfaceReady = false;
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
        setContentView(R.layout.activity_main);

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
        //------------分割线------------//
        // 获取 SurfaceView
        SurfaceView surfaceView = findViewById(R.id.surface_view);

        // 设置 SurfaceHolder 的回调
        if (surfaceView != null) {
            surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    // 将 Surface 传递给 native 层
                    setSurface(holder.getSurface());
                    isSurfaceReady = true;
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    isSurfaceReady = false;
                }
            });
        } else {
            // 如果 surfaceView 为 null，输出错误信息
            Toast.makeText(this, "SurfaceView 未找到", Toast.LENGTH_SHORT).show();
        }

        //------------分割线------------//





        tv = findViewById(R.id.sample_text);
        selectVideoButton = findViewById(R.id.select_video_button);
        decodeVideoButton = findViewById(R.id.decode_video_button);

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

        tv.setText(stringFromJNI());
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
            if (videoPath != null) {
                // 获取应用私有目录下的输出路径
                File externalDir = getExternalFilesDir(null);
                if (externalDir == null) {
                    Toast.makeText(this, "无法访问应用存储目录", Toast.LENGTH_SHORT).show();
                    return;
                }
                String outputPath = new File(externalDir, "output.yuv").getAbsolutePath();
                setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.PLAYING);
                decodeThread = new Thread(() -> {
                    runOnUiThread(() -> tv.setText("正在解析视频..."));
                    try {
                        decodeVideo(videoPath, outputPath);
                    } finally {
                        decodeThread = null;
                        setPlaybackUiState(videoPath == null
                                ? PlaybackUiPolicy.PlaybackUiState.IDLE
                                : PlaybackUiPolicy.PlaybackUiState.READY);
                    }
                }, "video-decode-runner");
                decodeThread.start();
            } else {
                Toast.makeText(this, "请先选择视频文件", Toast.LENGTH_SHORT).show();
            }
        });

        setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.IDLE);

    }
    // 新增native方法声明


    // 新增方法：更新进度条和时间显示
    public void updateProgress(final int currentPosition, final int duration) {
        runOnUiThread(() -> {
            if (!isSeeking) {
                seekBar.setMax(duration);
                seekBar.setProgress(currentPosition);
                updateTimeText(currentPosition, duration);
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
        try {
            // 将 URI 的内容复制到临时文件
            String tempFilePath = copyUriToTempFile(uri);
            videoPath = tempFilePath;
            tv.setText("已选择视频：" + videoPath);
            updateProgress(0, 0);
            setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState.READY);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read selected video file", e);
            tv.setText("无法读取文件");
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
    private String copyUriToTempFile(Uri uri) throws IOException {
        File tempFile = File.createTempFile("temp", "mp4", getCacheDir());
        tempFile.deleteOnExit();
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(tempFile)) {
            if (inputStream == null) {
                throw new IOException("无法打开输入流");
            }
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
        return tempFile.getAbsolutePath();
    }

    private boolean isDecodeRunning() {
        return decodeThread != null && decodeThread.isAlive();
    }

    private void applyPlaybackSpeed(float speed, String message) {
        if (!isDecodeRunning()) {
            Toast.makeText(this, "请先开始解析视频", Toast.LENGTH_SHORT).show();
            return;
        }
        setPlaybackSpeed(PlaybackInputPolicy.sanitizeSpeed(speed));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setPlaybackUiState(PlaybackUiPolicy.PlaybackUiState state) {
        playbackUiState = state;
        runOnUiThread(() -> {
            PlaybackUiPolicy.ControlState controlState = PlaybackUiPolicy.resolve(state);
            if (decodeVideoButton != null) {
                decodeVideoButton.setEnabled(controlState.decodeEnabled);
                decodeVideoButton.setText(controlState.decodeText);
            }
            if (playButton != null) {
                playButton.setEnabled(controlState.playEnabled);
            }
            if (pauseButton != null) {
                pauseButton.setEnabled(controlState.pauseEnabled);
            }
            if (speed05Button != null) speed05Button.setEnabled(controlState.speedEnabled);
            if (speed1Button != null) speed1Button.setEnabled(controlState.speedEnabled);
            if (speed2Button != null) speed2Button.setEnabled(controlState.speedEnabled);
            if (speed3Button != null) speed3Button.setEnabled(controlState.speedEnabled);
            if (seekBar != null) seekBar.setEnabled(controlState.seekEnabled);
        });
    }

    /* ----------------- 回调方法 ----------------- */
    public void onVideoDecoded(final String result) {
        runOnUiThread(() -> {
            tv.append("\n" + result);
            // 添加YUV文件路径显示
            if(result.contains("YUV")) {
                Toast.makeText(this, "YUV文件已生成: " + result, Toast.LENGTH_LONG).show();
            }
        });
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        progressHandler.removeCallbacks(progressUpdater);
        if (decodeThread != null && decodeThread.isAlive()) {
            decodeThread.interrupt();
        }
        isSurfaceReady = false;
        setPlaybackUiState(videoPath == null
                ? PlaybackUiPolicy.PlaybackUiState.IDLE
                : PlaybackUiPolicy.PlaybackUiState.READY);
        // 释放 Native 层音频资源
        nativeReleaseAudio();
    }

}
