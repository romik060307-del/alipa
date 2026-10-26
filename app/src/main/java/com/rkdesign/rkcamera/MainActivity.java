package com.rkdesign.rkcamera;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import android.hardware.camera2.CaptureRequest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextView status;
    private Button shutterButton, modeButton, flipButton, flashButton;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private Camera camera;
    private boolean videoMode = false, frontCamera = false, flashOn = false;
    private boolean hardwareStabilizationRequested = false;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean ok = Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA));
                if (Build.VERSION.SDK_INT <= 28) ok &= Boolean.TRUE.equals(result.get(Manifest.permission.WRITE_EXTERNAL_STORAGE));
                if (ok) startCamera(); else toast("Izin kamera/penyimpanan diperlukan untuk menjalankan aplikasi.");
            });

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        if (hasPermissions()) startCamera(); else requestPermissionsNow();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(8,9,11));
        TextView title = new TextView(this);
        title.setText("RK CAMERA AI"); title.setTextColor(Color.WHITE); title.setTextSize(18);
        title.setGravity(Gravity.CENTER); title.setLetterSpacing(.12f); title.setPadding(8,18,8,8);
        root.addView(title, new LinearLayout.LayoutParams(-1, 54));
        previewView = new PreviewView(this);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        root.addView(previewView, new LinearLayout.LayoutParams(-1, 0, 1));
        status = new TextView(this); status.setText("Menyiapkan kamera…"); status.setTextColor(Color.rgb(215,255,103));
        status.setTextSize(12); status.setGravity(Gravity.CENTER); status.setPadding(8,8,8,8);
        root.addView(status, new LinearLayout.LayoutParams(-1, 36));
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER); controls.setPadding(8,10,8,20);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        flipButton = button("BALIK"); modeButton = button("FOTO"); shutterButton = button("●"); flashButton = button("FLASH OFF");
        addControl(controls, flipButton, 1); addControl(controls, modeButton, 1); addControl(controls, shutterButton, 1); addControl(controls, flashButton, 1);
        root.addView(controls, new LinearLayout.LayoutParams(-1, 78));
        setContentView(root);
        flipButton.setOnClickListener(v -> { frontCamera = !frontCamera; startCamera(); });
        modeButton.setOnClickListener(v -> { if (recording != null) { toast("Hentikan rekaman terlebih dahulu."); return; } videoMode = !videoMode; modeButton.setText(videoMode ? "VIDEO" : "FOTO"); shutterButton.setText(videoMode ? "▶" : "●"); startCamera(); });
        flashButton.setOnClickListener(v -> { flashOn = !flashOn; flashButton.setText(flashOn ? "FLASH ON" : "FLASH OFF"); if (imageCapture != null) imageCapture.setFlashMode(flashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF); });
        shutterButton.setOnClickListener(v -> { if (videoMode) toggleRecording(); else takePhoto(); });
    }

    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(11);
        b.setAllCaps(false); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(38,42,46)));
        return b;
    }
    private void addControl(LinearLayout parent, Button b, int weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, 56, weight); p.setMargins(3,0,3,0); parent.addView(b,p);
    }
    private boolean hasPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return false;
        return Build.VERSION.SDK_INT > 28 || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    private void requestPermissionsNow() {
        if (Build.VERSION.SDK_INT <= 28) permissionLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE});
        else permissionLauncher.launch(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO});
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try { cameraProvider = future.get(); bindUseCases(); }
            catch (ExecutionException | InterruptedException e) { toast("Kamera gagal dibuka: " + e.getMessage()); }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindUseCases() {
        if (cameraProvider == null) return;
        CameraSelector selector = new CameraSelector.Builder().requireLensFacing(frontCamera ? CameraSelector.LENS_FACING_FRONT : CameraSelector.LENS_FACING_BACK).build();
        Preview.Builder previewBuilder = new Preview.Builder();
        // Request Android's hardware EIS when the camera HAL supports it. This request cannot create EIS on unsupported devices.
        Camera2Interop.Extender<Preview> extender = new Camera2Interop.Extender<>(previewBuilder);
        try { extender.setCaptureRequestOption(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON); hardwareStabilizationRequested = true; }
        catch (Exception ignored) { hardwareStabilizationRequested = false; }
        Preview preview = previewBuilder.build(); preview.setSurfaceProvider(previewView.getSurfaceProvider());
        imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();
        Recorder recorder = new Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build();
        videoCapture = VideoCapture.withOutput(recorder);
        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture);
            imageCapture.setFlashMode(flashOn ? ImageCapture.FLASH_MODE_ON : ImageCapture.FLASH_MODE_OFF);
            boolean flashAvailable = camera.getCameraInfo().hasFlashUnit();
            flashButton.setEnabled(flashAvailable);
            status.setText("Kamera aktif • HD • stabilisasi hardware diminta; dukungan ditentukan perangkat");
        } catch (Exception e) {
            status.setText("Kamera tidak mendukung konfigurasi ini"); toast("Tidak dapat memulai kamera: " + e.getMessage());
        }
    }

    private String stamp() { return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()); }
    private void takePhoto() {
        if (imageCapture == null) return;
        ContentValues values = new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME, "RKCamera_" + stamp()); values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(getContentResolver(), MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build();
        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) { toast("Foto tersimpan di galeri."); }
            @Override public void onError(@NonNull ImageCaptureException e) { toast("Gagal menyimpan foto: " + e.getMessage()); }
        });
    }

    private void toggleRecording() {
        if (videoCapture == null) return;
        if (recording != null) { recording.stop(); recording = null; shutterButton.setText("▶"); status.setText("Menyimpan video…"); return; }
        ContentValues values = new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME, "RKCamera_" + stamp()); values.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        MediaStoreOutputOptions output = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI).setContentValues(values).build();
        PendingRecording pending = videoCapture.getOutput().prepareRecording(this, output);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) pending = pending.withAudioEnabled();
        recording = pending.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Start) { shutterButton.setText("■"); status.setText("MEREKAM • gerakkan kamera perlahan"); }
            if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize fin = (VideoRecordEvent.Finalize) event; recording = null; shutterButton.setText("▶");
                if (fin.hasError()) { status.setText("Rekaman gagal diselesaikan"); toast("Gagal menyimpan video. Kode: " + fin.getError()); }
                else { status.setText("Video tersimpan • stabilisasi bergantung dukungan kamera"); toast("Video tersimpan di galeri."); }
            }
        });
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override protected void onDestroy() { if (recording != null) { recording.stop(); recording = null; } super.onDestroy(); }
}
