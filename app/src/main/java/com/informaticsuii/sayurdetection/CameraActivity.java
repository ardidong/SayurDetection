package com.informaticsuii.sayurdetection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.informaticsuii.sayurdetection.env.ImageUtils;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static android.content.Intent.ACTION_PICK;

public abstract class CameraActivity extends AppCompatActivity implements ImageReader.OnImageAvailableListener,
        View.OnClickListener {
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    private static final int IMAGE_INPUT_CODE = 2;
    protected int previewWidth = 0;
    protected int previewHeight = 0;

    private boolean cameraPermission = false;
    private boolean storagePermission = false;
    private boolean debug = false;
    private boolean isProcessingFrame = false;

    private byte[][] yuvBytes = new byte[3][];
    private int[] rgbBytes = null;
    private int yRowStride;

    private Handler handler;
    private HandlerThread handlerThread;
    private Runnable postInferenceCallback;
    private Runnable imageConverter;

    private Button btnCapture;
    private Button btnDetectStillImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(this.getClass().getSimpleName(), "onCreate...");
        setContentView(R.layout.activity_main);

        btnDetectStillImage = findViewById(R.id.btn_detect_still_image);
        btnDetectStillImage.setOnClickListener(this);

        if (hasCameraPersmission()) {
            setFragment();
        } else {
            requestPermission();
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected int getLuminanceStride() {
        return yRowStride;
    }

    protected byte[] getLuminance() {
        return yuvBytes[0];
    }


    protected void fillBytes(final Image.Plane[] planes, final byte[][] yuvBytes) {
        // Because of the variable row stride it's not possible to know in
        // advance the actual necessary dimensions of the yuv planes.
        for (int i = 0; i < planes.length; ++i) {
            final ByteBuffer buffer = planes[i].getBuffer();
            if (yuvBytes[i] == null) {
                Log.d("//CameraActivity",
                        String.format("Initializing buffer %d at size %d", i, buffer.capacity()));
                yuvBytes[i] = new byte[buffer.capacity()];
            }
            buffer.get(yuvBytes[i]);
        }
    }


    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Log.d("//DEBUGPROCESS", "onImageAvailable Called");

        if (previewWidth == 0 || previewHeight == 0) {
            return;
        }
        if (rgbBytes == null) {
            rgbBytes = new int[previewWidth * previewHeight];
        }

        final Image image = imageReader.acquireLatestImage();
        if (image == null) {
            return;
        }

        if (isProcessingFrame) {
            image.close();
            return;
        }
        isProcessingFrame = true;

        Trace.beginSection("imageAvailable");
        final Image.Plane[] planes = image.getPlanes();
        fillBytes(planes, yuvBytes);
        yRowStride = planes[0].getRowStride();
        final int uvRowStride = planes[1].getRowStride();
        final int uvPixelStride = planes[1].getPixelStride();

        imageConverter =
                new Runnable() {
                    @Override
                    public void run() {
                        ImageUtils.convertYUV420ToARGB8888(
                                yuvBytes[0],
                                yuvBytes[1],
                                yuvBytes[2],
                                previewWidth,
                                previewHeight,
                                yRowStride,
                                uvRowStride,
                                uvPixelStride,
                                rgbBytes);
                    }
                };

        postInferenceCallback = new Runnable() {
            @Override
            public void run() {
                image.close();
                isProcessingFrame = false;
            }
        };
        processImage();
        Trace.endSection();
    }

    protected void setFragment() {
        Fragment fragment;
        fragment = CameraConnectionFragment.newInstance(
                new CameraConnectionFragment.ConnectionCallback() {
                    @Override
                    public void onPreviewSizeChosen(Size size, int cameraRotation) {
                        previewHeight = size.getHeight();
                        previewWidth = size.getWidth();
                        CameraActivity.this.onPreviewSizeChosen(size, cameraRotation);
                    }
                }, this,
                R.layout.fragment_camera_connection,
                getDesiredPreviewFrameSize());

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.beginTransaction().replace(R.id.container, fragment).commit();

    }

    protected void detectStillImage() {
        Toast.makeText(this, "KLIK BUTTON", Toast.LENGTH_SHORT).show();
        quitHandler();
        Intent detectStillIntent = new Intent(getApplicationContext(), DetectFromStillActivity.class);
        detectStillIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(detectStillIntent);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_detect_still_image:
                detectStillImage();
                break;
        }
    }


    @Override
    public synchronized void onResume() {
        super.onResume();
        Log.d(this.getClass().getSimpleName(),"onResume...");
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());

    }

    @Override
    public synchronized void onPause() {
        quitHandler();
        super.onPause();
    }

    public void quitHandler() {
        if (handlerThread != null) {
            handlerThread.quitSafely();
            try {
                handlerThread.join();
                handlerThread = null;
                handler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    private boolean hasCameraPersmission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED) {
                cameraPermission = true;
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    private boolean hasStoragePersmission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED) {

                storagePermission = true;
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    private void requestPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!cameraPermission) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                    Toast.makeText(this, "This App request access to camera",
                            Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_RESULT);
            } else if (!storagePermission) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    Toast.makeText(this, "App needs to be able to save images",
                            Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setFragment();
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Camera Permission")
                        .setMessage("This app needs device's camera permission!");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finishAffinity();
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();

            }
        }
    }


    public boolean isDebug() {
        return debug;
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }


    protected abstract void processImage();

    protected abstract void onPreviewSizeChosen(final Size size, final int rotation);

    protected abstract int getLayoutId();

    protected abstract Size getDesiredPreviewFrameSize();

    protected abstract void setNumThreads(int numThreads);

    protected abstract void setUseNNAPI(boolean isChecked);
}
