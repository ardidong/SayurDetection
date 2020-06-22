package com.informaticsuii.sayurdetection;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.ImageButton;
import android.widget.Toast;

import com.informaticsuii.sayurdetection.classifier.Classifier;
import com.informaticsuii.sayurdetection.classifier.ObjectDetectionClassifier;
import com.informaticsuii.sayurdetection.customview.OverlayView;
import com.informaticsuii.sayurdetection.env.BorderedText;
import com.informaticsuii.sayurdetection.env.ImageUtils;
import com.informaticsuii.sayurdetection.tracker.MultiBoxTracker;

import java.io.IOException;

public class MainActivity extends CameraActivity {
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier classifier;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private BorderedText borderedText;
    private MultiBoxTracker tracker;

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation) {
        Log.d("//DEBUGPROCESS", "onPreviewSizeChoosen Called");
        final float textSizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE;

        try {
            classifier = ObjectDetectionClassifier.create(
                    this,
                    getAssets(),
                    TF_OD_API_MODEL_FILE,
                    TF_OD_API_LABELS_FILE,
                    TF_OD_API_INPUT_SIZE,
                    TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
            Toast.makeText(this, "Classifier berhasil diinisiasi", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Classifier gagal diinisiasi", Toast.LENGTH_SHORT).show();
            finish();
        }



    }

    @Override
    protected void processImage() {

    }


    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected int getLayoutId() {
        return 0;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return new Size(640, 480);
    }

    @Override
    protected void setNumThreads(int numThreads) {

    }

    @Override
    protected void setUseNNAPI(boolean isChecked) {

    }
}