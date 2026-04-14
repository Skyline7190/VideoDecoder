package com.example.videodecoder;

import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    /* ----------------- 常量声明 ----------------- */
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final int PICK_VIDEO_REQUEST = 101;
    /* ----------------- 成员变量 ----------------- */
    private TextView tv;
    private String videoPath;
    // 播放控制标志
    private volatile boolean isPlaying = true;
    private Thread decodeThread;

    private Button speed05Button;
    private Button speed1Button;
    private Button speed2Button;
    private Button speed3Button;
    private SeekBar seekBar;
    //

    private TextView currentTimeText;
    private TextView totalTimeText;
    private boolean isSeeking = false;


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
        // 设置SeekBar监听
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // 用户拖动进度条时的处理
                    seekToPosition(progress);
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // 开始拖动时暂停播放
                isSeeking = true;
                pauseDecoding();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 停止拖动时恢复播放
                isSeeking = false;
                resumeDecoding();
            }
        });

        speed05Button = findViewById(R.id.speed_05);
        speed1Button = findViewById(R.id.speed_1);
        speed2Button = findViewById(R.id.speed_2);
        speed3Button = findViewById(R.id.speed_3);



        speed05Button.setOnClickListener(v -> {
            setPlaybackSpeed(0.5f);
            Toast.makeText(this, "播放速度设置为0.5倍", Toast.LENGTH_SHORT).show();
        });
        speed1Button.setOnClickListener(v -> {
            setPlaybackSpeed(1.0f);
            Toast.makeText(this, "播放速度设置为1倍", Toast.LENGTH_SHORT).show();
        });
        speed2Button.setOnClickListener(v -> {
            setPlaybackSpeed(2.0f);
            Toast.makeText(this, "播放速度设置为2倍", Toast.LENGTH_SHORT).show();
        });
        speed3Button.setOnClickListener(v -> {
            setPlaybackSpeed(3.0f);
            Toast.makeText(this, "播放速度设置为3倍", Toast.LENGTH_SHORT).show();
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
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {}
            });
        } else {
            // 如果 surfaceView 为 null，输出错误信息
            Toast.makeText(this, "SurfaceView 未找到", Toast.LENGTH_SHORT).show();
        }

        //------------分割线------------//





        tv = findViewById(R.id.sample_text);
        Button selectVideoButton = findViewById(R.id.select_video_button);
        Button decodeVideoButton = findViewById(R.id.decode_video_button);

        Button playButton = findViewById(R.id.play_button);
        Button pauseButton = findViewById(R.id.pause_button);

        playButton.setOnClickListener(v -> {
            if (!isPlaying) {
                isPlaying = true;
                resumeDecoding();
                Toast.makeText(this, "继续播放", Toast.LENGTH_SHORT).show();
            }
        });

        pauseButton.setOnClickListener(v -> {
            if (isPlaying) {
                isPlaying = false;
                pauseDecoding();
                Toast.makeText(this, "暂停播放", Toast.LENGTH_SHORT).show();
            }
        });

        tv.setText(stringFromJNI());
        // 监听按钮点击事件
        selectVideoButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                            != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO
                        },
                        PERMISSION_REQUEST_CODE);
            } else {
                pickVideo();
            }
        });

        decodeVideoButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
            } else if (videoPath != null) {
                // 获取应用私有目录下的输出路径
                String outputPath = new File(getExternalFilesDir(null), "output.yuv").getAbsolutePath();
                new Thread(() -> {
                    // 在开始解析前更新UI显示“正在解析”
                    decodeThread = new Thread(() -> {
                        runOnUiThread(() -> tv.setText("正在解析视频..."));
                        decodeVideo(videoPath, outputPath);
                    });
                    decodeThread.start();
                    // 假设decodeVideo方法会调用onVideoDecoded方法来通知解析结果
                }).start();
            } else {
                Toast.makeText(this, "请先选择视频文件", Toast.LENGTH_SHORT).show();
            }
        });


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
        currentTimeText.setText(formatTime(currentMs));
        totalTimeText.setText(formatTime(totalMs));
    }

    private String formatTime(int milliseconds) {
        int seconds = milliseconds / 1000;
        int minutes = seconds / 60;
        int hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        startActivityForResult(intent, PICK_VIDEO_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == PICK_VIDEO_REQUEST && resultCode == RESULT_OK) {
            Uri uri = resultData.getData();
            if (uri != null) {
                try {
                    // 将 URI 的内容复制到临时文件
                    String tempFilePath = copyUriToTempFile(uri);
                    videoPath = tempFilePath;
                    runOnUiThread(() -> {
                        tv.setText("已选择视频：" + videoPath);
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        tv.setText("无法读取文件");
                    });
                }

                // 获取持久化 URI 权限
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
        }
    }
    /* ----------------- 文件操作辅助方法 ----------------- */
    private String copyUriToTempFile(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        File tempFile = File.createTempFile("temp", "mp4", getCacheDir());
        tempFile.deleteOnExit();
        OutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }
        inputStream.close();
        outputStream.close();
        return tempFile.getAbsolutePath();
    }
    /* ====================== 权限处理 ====================== */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickVideo();
            } else {
                Toast.makeText(this, "没有权限访问存储", Toast.LENGTH_SHORT).show();
            }
        }
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
        if (decodeThread != null && decodeThread.isAlive()) {
            decodeThread.interrupt();
        }
        // 释放 Native 层音频资源
        nativeReleaseAudio();
    }

}