package com.informaticsuii.sayurdetection;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
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
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.informaticsuii.sayurdetection.customview.AutoFitTextureView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraConnectionFragment extends Fragment {

    private static final int MINIMUM_PREVIEW_SIZE = 320;


    private CameraDevice cameraDevice;
    private String cameraId;
    private Size previewSize;
    private final Size inputSize;
    private CaptureRequest.Builder captureRequestBuilder;

    private AutoFitTextureView textureView;
    private final int layout;


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    private ImageReader mImageReader;
    public ImageReader.OnImageAvailableListener imageListener;


    private int totalRotation;

    private final ConnectionCallback connectionCallback;
    private CameraCaptureSession previewCaptureSession;

    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            setupCamera(width, height);
            configureTransform(width, height);
            connectCamera();
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

    private final CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreviewSessionCameraSession();
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
    public void onStop() {
        super.onStop();
        Log.d("//Lifecycle", "Fragment onStop..");
    }

    @Override
    public void onPause() {
        super.onPause();
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
        stopBackgroundThread();
        Log.d("//Lifecycle", "Fragment onPause..");
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
                totalRotation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

                previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        inputSize.getWidth(), inputSize.getHeight());

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
        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        final RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        final RectF bufferRect = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();
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
                    ImageFormat.YUV_420_888, 2);
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

    protected static Size chooseOptimalSize(final Size[] choices, final int width, final int height) {
        final int minSize = Math.max(Math.min(width, height), MINIMUM_PREVIEW_SIZE);
        final Size desiredSize = new Size(width, height);

        // Collect the supported resolutions that are at least as big as the preview Surface
        boolean exactSizeFound = false;
        final List<Size> bigEnough = new ArrayList<Size>();
        final List<Size> tooSmall = new ArrayList<Size>();
        for (final Size option : choices) {
            if (option.equals(desiredSize)) {
                // Set the size but don't return yet so that remaining sizes will still be logged.
                exactSizeFound = true;
            }

            if (option.getHeight() >= minSize && option.getWidth() >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

//        LOGGER.i("Desired size: " + desiredSize + ", min size: " + minSize + "x" + minSize);
//        LOGGER.i("Valid preview sizes: [" + TextUtils.join(", ", bigEnough) + "]");
//        LOGGER.i("Rejected preview sizes: [" + TextUtils.join(", ", tooSmall) + "]");

        if (exactSizeFound) {
            //LOGGER.i("Exact size match found.");
            return desiredSize;
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            final Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
           // LOGGER.i("Chosen size: " + chosenSize.getWidth() + "x" + chosenSize.getHeight());
            return chosenSize;
        } else {
            //LOGGER.e("Couldn't find any suitable preview size");
            return choices[0];
        }
    }


    private void detectImageCaptured(Uri uri) {
        Bundle extras = new Bundle();
        extras.putInt("extra_action", DetectFromStillActivity.DETECT_CAPTURED);
        extras.putString("extra_uri", uri.toString());
        Intent detectStillIntent = new Intent(getActivity(), DetectFromStillActivity.class);
        detectStillIntent.putExtras(extras);
        startActivity(detectStillIntent);
    }

    private File createImageFileName() throws IOException {
        ContextWrapper cw = new ContextWrapper(getContext());
        File directory = cw.getDir("imageDir", Context.MODE_PRIVATE);
        return new File(directory, "picture.jpg");
    }

    public void takePicture() {
        if (cameraDevice == null)
            return;
        CameraManager cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraDevice.getId());
            Size[] jpegSizes;
            jpegSizes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(ImageFormat.JPEG);

            //Capture image with custom size
            int width = 1024;
            int height = 1024;
//            if (jpegSizes != null && jpegSizes.length > 0) {
//                width = jpegSizes[0].getWidth();
//                height = jpegSizes[0].getHeight();
//            }
            final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(reader.getSurface());
            outputSurface.add(new Surface(textureView.getSurfaceTexture()));

            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            //check orientation base on device
            int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            final File file = createImageFileName();
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null)
                            image.close();
                    }

                }

                private void save(byte[] bytes) throws IOException {
                    OutputStream outputStream = null;
                    try {
                        outputStream = new FileOutputStream(file);
                        outputStream.write(bytes);
                    } finally {
                        if (outputStream != null) {
                            outputStream.close();
                        }
                    }
                }
            };

            reader.setOnImageAvailableListener(readerListener, backgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    detectImageCaptured(Uri.fromFile(file));
                }
            };

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    try {
                        cameraCaptureSession.capture(captureBuilder.build(), captureListener, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, backgroundHandler);

        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
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