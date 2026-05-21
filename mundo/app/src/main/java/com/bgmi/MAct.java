package com.bgmi;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bgmi.utils.AppManager;
import com.mundo.MundoCore;
import com.mundo.entity.pm.InstallResult;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

@Obfuscate
public class MAct extends AppCompatActivity {
    static {
        try {
            System.loadLibrary("zenin");
        } catch (UnsatisfiedLinkError ignored) {}
    }

    private static final String PKG_BGMI = "com.pubg.imobile";
    private static final int USER_ID = 0;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private boolean doubleBackExit = false;

    public static native String exdate();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        doCountTimerAccount();

        findViewById(R.id.btnStart).setOnClickListener(v -> handleStart());
        findViewById(R.id.btnStop).setOnClickListener(v -> finishAffinity());
    }

    private void handleStart() {
        if (MundoCore.get() == null) return;

        if (!MundoCore.get().isInstalled(PKG_BGMI, USER_ID)) {
            Toast.makeText(this, "Installing BGMI...", Toast.LENGTH_SHORT).show();
            InstallResult res = MundoCore.get().installPackageAsUser(PKG_BGMI, USER_ID);
            if (res.success) {
                forceAutoCopyObb();
            } else {
                Toast.makeText(this, "Install Failed: " + res.msg, Toast.LENGTH_SHORT).show();
            }
        } else {
            forceAutoCopyObb();
        }
    }

    private void forceAutoCopyObb() {
        // --- PATH CONFIGURATION ---
        String internalRoot = Environment.getExternalStorageDirectory().getAbsolutePath();
        
        // SOURCE: The official game OBB folder
        File sourceFolder = new File(internalRoot + "/Android/obb/" + PKG_BGMI);
        
        // DESTINATION: Your specific custom folder path
        File destFolder = new File(internalRoot + "/Sdcard/Android/obb/" + PKG_BGMI);

        if (!destFolder.exists()) destFolder.mkdirs();

        // Check if OBB is already there
        File[] existingFiles = destFolder.listFiles((dir, name) -> name.endsWith(".obb"));
        if (existingFiles != null && existingFiles.length > 0) {
            launchGame();
            return;
        }

        Toast.makeText(this, "Forcing Auto-Copy (1m Timeout)...", Toast.LENGTH_SHORT).show();
        
        AtomicBoolean isFinished = new AtomicBoolean(false);

        // 1 Minute Timeout logic
        timerHandler.postDelayed(() -> {
            if (!isFinished.get()) {
                isFinished.set(true);
                Toast.makeText(MAct.this, "Auto-copy timed out! Please copy OBB manually.", Toast.LENGTH_LONG).show();
            }
        }, 60000);

        new Thread(() -> {
            try {
                File[] sourceFiles = sourceFolder.listFiles((dir, name) -> name.endsWith(".obb"));

                if (sourceFiles == null || sourceFiles.length == 0) {
                    if (!isFinished.get()) {
                        isFinished.set(true);
                        runOnUiThread(() -> Toast.makeText(MAct.this, "Source OBB not found! Copy manually.", Toast.LENGTH_LONG).show());
                    }
                    return;
                }

                File srcFile = sourceFiles[0];
                File destFile = new File(destFolder, srcFile.getName());

                // Fast NIO Copy
                try (FileChannel srcChannel = new FileInputStream(srcFile).getChannel();
                     FileChannel destChannel = new FileOutputStream(destFile).getChannel()) {
                    srcChannel.transferTo(0, srcChannel.size(), destChannel);
                }

                if (!isFinished.get()) {
                    isFinished.set(true);
                    runOnUiThread(() -> {
                        Toast.makeText(MAct.this, "OBB Copy Succeed!", Toast.LENGTH_SHORT).show();
                        launchGame();
                    });
                }

            } catch (Exception e) {
                if (!isFinished.get()) {
                    isFinished.set(true);
                    runOnUiThread(() -> Toast.makeText(MAct.this, "Force Copy Failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
        }).start();
    }

    private void launchGame() {
        try {
            MundoCore.get().launchApk(PKG_BGMI, USER_ID);
        } catch (Exception e) {
            Toast.makeText(this, "Launch Failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (doubleBackExit) { finishAffinity(); return; }
        this.doubleBackExit = true;
        Toast.makeText(this, "Double press BACK to exit", Toast.LENGTH_SHORT).show();
        timerHandler.postDelayed(() -> doubleBackExit = false, 2000);
    }

    private void doCountTimerAccount() {
        timerHandler.post(new Runnable() {
            @Override
            public void run() {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date expiry = sdf.parse(exdate());
                    long diff = expiry.getTime() - System.currentTimeMillis();
                    if (diff > 0) {
                        ((TextView) findViewById(R.id.tvD)).setText(String.format("%03d", diff / 86400000));
                        ((TextView) findViewById(R.id.tvH)).setText(String.format("%02d", (diff / 3600000) % 24));
                        ((TextView) findViewById(R.id.tvM)).setText(String.format("%02d", (diff / 60000) % 60));
                        ((TextView) findViewById(R.id.tvS)).setText(String.format("%02d", (diff / 1000) % 60));
                        timerHandler.postDelayed(this, 1000);
                    } else {
                        Toast.makeText(MAct.this, "Expired", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                } catch (Exception ignored) {}
            }
        });
    }
}
