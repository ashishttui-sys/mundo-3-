package com.bgmi;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.security.MessageDigest;

import com.bgmi.utils.Downtwo;
import com.bgmi.utils.Prefs;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class LogAct extends AppCompatActivity {

    static {
        try {
            // Using "zenin" library as per your second file
            System.loadLibrary("akshit");
        } catch (UnsatisfiedLinkError ignored) {
        }
    }

    private Prefs prefs;
    private final String USER = "USER";

    private EditText textUsername;
    private Button btnLogin;
    private ImageView pasteBtn;
    private TextView getKey;

    private Dialog loadingDialog;

    private static final int REQUEST_MANAGE_STORAGE_PERMISSION = 100;
    private static final int REQUEST_MANAGE_UNKNOWN_APP_SOURCES = 200;
    private static final String PREFS_NAME = "com.bgmi.prefs";
    private static final String PREF_PERMISSIONS_GRANTED = "permissions_granted";

    public static native boolean nativeVerifySignature(Context context);
    private static final String EXPECTED_SIGNATURE =
            "77f05d53ce8bf1855caef38ce87f13a8bb2b1b2cdd2d48da9d3ba897eac4549e";

    private String sha256(byte[] cert) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(cert);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        prefs = new Prefs(this);
        checkAndRequestPermissions();

        textUsername = findViewById(R.id.userkey);
        btnLogin = findViewById(R.id.login);
        pasteBtn = findViewById(R.id.paste);
        getKey = findViewById(R.id.GetKey);

        textUsername.setText(prefs.getSt(USER, ""));

        getKey.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(GetKey()));
            startActivity(intent);
        });

        btnLogin.setOnClickListener(v -> {
            String userKey = textUsername.getText().toString().trim();
            if (!userKey.isEmpty()) {
                prefs.setSt(USER, userKey);
                Login(this, userKey);
            } else {
                textUsername.setError("Please enter key");
            }
        });

        pasteBtn.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    String pasted = clip.getItemAt(0).getText().toString();
                    if (pasted.length() > 5) {
                        textUsername.setText(pasted);
                    } else {
                        Toast.makeText(this, "Invalid key in clipboard", Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(this, "Clipboard empty", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkAndRequestPermissions() {
        if (!isStoragePermissionGranted()) {
            requestStoragePermissionDirect();
        } else if (!canRequestPackageInstalls()) {
            requestUnknownAppPermissionsDirect();
        }
    }

    private boolean isStoragePermissionGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    private void requestStoragePermissionDirect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivityForResult(intent, REQUEST_MANAGE_STORAGE_PERMISSION);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_MANAGE_STORAGE_PERMISSION);
        }
    }

    private boolean canRequestPackageInstalls() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || getPackageManager().canRequestPackageInstalls();
    }

    private void requestUnknownAppPermissionsDirect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_MANAGE_UNKNOWN_APP_SOURCES);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        checkAndRequestPermissions();
    }

    // 🔑 FIXED LOGIN: Key verification is now mandatory
        // 🔑 FIXED LOGIN: Mandatory verification + Auto-Copy Key
    private void Login(final Context m_Context, final String userKey) {
        showLoadingDialog("Checking key...", false);

        Handler loginHandler = new Handler(msg -> {
            dismissLoadingDialog();
            if (msg.what == 0) {
                // --- AUTO COPY LOGIC START ---
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("UserKey", userKey);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    // Optional: Toast to let the user know it was copied
                    Toast.makeText(m_Context, "Key copied to clipboard", Toast.LENGTH_SHORT).show();
                }
                // --- AUTO COPY LOGIC END ---

                // Key is VALID ("OK"), move to download/launch
                startDownload(m_Context);
            } else if (msg.what == 1) {
                // Key is INVALID, show error and STOP
                showLoadingDialog((String) msg.obj, true);
            }
            return true;
        });

        new Thread(() -> {
            String result = Check(m_Context, userKey); 
            if ("OK".equals(result)) {
                loginHandler.sendEmptyMessage(0);
            } else {
                Message msg = Message.obtain();
                msg.what = 1;
                msg.obj = result;
                loginHandler.sendMessage(msg);
            }
        }).start();
    }


    // 📁 FIXED DOWNLOAD: Proceed to MAct even if download fails
    private void startDownload(Context m_Context) {
        showLoadingDialog("Checking resources...", false);

        Downtwo task = new Downtwo(LogAct.this, success -> {
            dismissLoadingDialog();
            
            if (!success) {
                // Just show a toast if it fails, but don't stop the app
                Toast.makeText(LogAct.this, "Download failed! Proceeding anyway...", Toast.LENGTH_SHORT).show();
            }
            
            // ALWAYS continue to MAct
            Intent i = new Intent(m_Context, MAct.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            m_Context.startActivity(i);
            finish();
        });

        task.setProgressListener(progress -> runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) {
                ProgressBar progressBar = loadingDialog.findViewById(R.id.progressBar);
                TextView progressText = loadingDialog.findViewById(R.id.progressText);
                if (progressBar != null) {
                    progressBar.setIndeterminate(false);
                    progressBar.setMax(100);
                    progressBar.setProgress(progress);
                }
                if (progressText != null) {
                    progressText.setText("Downloading... " + progress + "%");
                }
            }
        }));

        try {
            task.execute(Downtwo.Link());
        } catch (Exception e) {
            // Fallback if the download task crashes
            Intent i = new Intent(m_Context, MAct.class);
            startActivity(i);
            finish();
        }
    }

    private void showLoadingDialog(String message, boolean isError) {
        if (loadingDialog == null) {
            loadingDialog = new Dialog(this);
            loadingDialog.setContentView(R.layout.ios_loading);
            loadingDialog.setCancelable(false);
            if (loadingDialog.getWindow() != null) {
                loadingDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }
        }

        TextView loadingText = loadingDialog.findViewById(R.id.loadingText);
        ProgressBar progressBar = loadingDialog.findViewById(R.id.progressBar);
        Button okButton = loadingDialog.findViewById(R.id.okButton);

        if (isError) {
            progressBar.setVisibility(View.GONE);
            okButton.setVisibility(View.VISIBLE);
            loadingText.setText("Login Error: " + message);
            okButton.setOnClickListener(v -> dismissLoadingDialog());
        } else {
            progressBar.setVisibility(View.VISIBLE);
            okButton.setVisibility(View.GONE);
            loadingText.setText(message != null ? message : "Loading...");
        }

        loadingDialog.show();
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private static native String Check(Context mContext, String userKey);
    private native String GetKey();
}
