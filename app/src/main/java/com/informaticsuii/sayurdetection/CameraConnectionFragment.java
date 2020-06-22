package com.informaticsuii.sayurdetection;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.informaticsuii.sayurdetection.customview.AutoFitTextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraConnectionFragment extends Fragment {
    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAIT_LOCK = 1;
    private int captureState = STATE_PREVIEW;
    private static final int MINIMUM_PREVIEW_SIZE = 320;
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;


    private CameraDevice cameraDevice;
    private String cameraId;
    private Size previewSize;
    private Size imageSize;
    private Size inputSize;
    private CaptureRequest.Builder captureRequestBuilder;

    private AutoFitTextureView textureView;
    private final int layout;


    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }

    private class ImageSaver implements Runnable {
        private final Image mImage;

        public ImageSaver(Image image) {
            this.mImage = image;
        }

        @Override
        public void run() {
            ByteBuffer byteBuffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);

            FileOutputStream fileOutputStream = null;
            try {
                fileOutputStream = new FileOutputStream(imageFileName);
                fileOutputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (fileOutputStream != null) {
                    try {
                        fileOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private ImageReader mImageReader;
    public ImageReader.OnImageAvailableListener imageListener;
    private File imageFolder;
    private String imageFileName;


    private int totalRotation;

    private final ConnectionCallback connectionCallback;
    private CameraCaptureSession previewCaptureSession;
    private CameraCaptureSession.CaptureCallback previewCaptureCallback =
            new CameraCaptureSession.CaptureCallback() {

                private void process(CaptureResult captureResult) {
                    switch (captureState) {
                        case STATE_PREVIEW:
                            //do nothing
                            break;
                        case STATE_WAIT_LOCK:
                            captureState = STATE_PREVIEW;
                            Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                            if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                    afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                Toast.makeText(getActivity(), "AF Locked", Toast.LENGTH_SHORT).show();
                                //startStillCaptureRequest();
                            }
                            break;
                    }
                }

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);

                    //process(result);
                }

                ;
            };


    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera(width, height);
            connectCamera();
            configureTransform(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreviewSessionCameraSession();
            //Toast.makeText(MainActivity.this, "Camera connected", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int i) {
            camera.close();
            cameraDevice = null;
        }
    };


    private CameraConnectionFragment(
            final ConnectionCallback connectionCallback,
            final ImageReader.OnImageAvailableListener imageListener,
            final int layout,
            final Size inputSize) {
        this.connectionCallback = connectionCallback;
        this.imageListener = imageListener;
        this.layout = layout;
        this.inputSize = inputSize;
    }

    public static CameraConnectionFragment newInstance(
            final ConnectionCallback callback,
            final ImageReader.OnImageAvailableListener imageListener,
            final int layout,
            final Size inputSize) {
        return new CameraConnectionFragment(callback, imageListener, layout, inputSize);
    }

    /**
     * Callback for Activities to use to initialize their data once the selected preview size is
     * known.
     */
    public interface ConnectionCallback {
        void onPreviewSizeChosen(Size size, int cameraRotation);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera_connection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        textureView = view.findViewById(R.id.texture);

    }

    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        if (textureView.isAvailable()) {
            setupCamera(textureView.getWidth(), textureView.getHeight());
            connectCamera();
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        if (previewCaptureSession != null) {
            previewCaptureSession.close();
            previewCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        super.onPause();


    }

    private void startBackgroundThread() {
        backgroundHandlerThread = new HandlerThread("Camera");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundHandlerThread.quitSafely();
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @SuppressLint("MissingPermission")
    private void connectCamera() {
        final Activity activity = getActivity();
        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            assert cameraManager != null;
            cameraManager.openCamera(cameraId, cameraDeviceStateCallback, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void setupCamera(int width, int height) {
        final Activity activity = getActivity();
        CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String camId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(camId);
                if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                cameraId = camId;
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

                int deviceOrientation = activity.getWindowManager().getDefaultDisplay().getRotation();
                totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);

                Point displaySize = new Point();
                activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

                boolean swapRotation = totalRotation == 90 || totalRotation == 270;
                int rotatedWidth = width;
                int rotatedHeight = height;

                if (swapRotation) {
                    rotatedWidth = height;
                    rotatedHeight = width;
                }

                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                //SIze
                Size largest = Collections.max(
                        Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)),
                        new CompareSizesByArea());

                imageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG),
                        rotatedWidth, rotatedHeight, maxPreviewWidth, maxPreviewHeight, largest);


                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        inputSize.getWidth(), inputSize.getHeight(), maxPreviewWidth, maxPreviewHeight, largest);

                final int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                } else {
                    textureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                }

                connectionCallback.onPreviewSizeChosen(previewSize, totalRotation);
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void configureTransform(int viewWidth, int viewHeight) {
        final Activity activity = getActivity();
        if (null == textureView || null == previewSize) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / previewSize.getHeight(),
                    (float) viewWidth / previewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        deviceOrientation = ORIENTATIONS.get(deviceOrientation);
        return (sensorOrientation + deviceOrientation + 360) % 360;

    }

    private void startPreviewSessionCameraSession() {
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        // We configure the size of default buffer to be the size of camera preview we want.
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        // This is the output Surface we need to start preview.
        Surface previewSurface = new Surface(surfaceTexture);


        try {
            // We set up a CaptureRequest.Builder with the output Surface.
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            mImageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(),
                    ImageFormat.YUV_420_888, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(
                    imageListener, backgroundHandler);
            captureRequestBuilder.addTarget(mImageReader.getSurface());

            // Here, we create a CameraCaptureSession for camera preview.
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                            if (cameraDevice == null) {
                                return;
                            }

                            previewCaptureSession = cameraCaptureSession;
                            try {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                previewCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),
                                        null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getActivity(),
                                    "Unable to setup camera preview", Toast.LENGTH_SHORT).show();
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height, int maxWidth, int maxHeight, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= width &&
                        option.getHeight() >= height) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e("Camera2", "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum(
                    (long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());

        }
    }
}