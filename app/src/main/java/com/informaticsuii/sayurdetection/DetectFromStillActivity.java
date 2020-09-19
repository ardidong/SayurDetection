package com.informaticsuii.sayurdetection;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.informaticsuii.sayurdetection.classifier.Classifier;
import com.informaticsuii.sayurdetection.classifier.ObjectDetectionClassifier;
import com.informaticsuii.sayurdetection.env.ImageUtils;
import com.informaticsuii.sayurdetection.tracker.MultiBoxTracker;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static android.content.Intent.ACTION_PICK;

public class DetectFromStillActivity extends AppCompatActivity {
    private static final int IMAGE_INPUT_CODE = 2;
    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 1024;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

    private Bitmap bitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private Uri imageUri;
    private Uri croppedUri;
    private Classifier classifier;

    MultiBoxTracker tracker;

    private ImageView ivStillImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_from_still);

        ivStillImage = findViewById(R.id.iv_still_image);

        Intent imageIntent = new Intent();
        imageIntent.setAction(ACTION_PICK);
        imageIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        imageIntent.setType("image/*");
        startActivityForResult(Intent.createChooser(imageIntent, "Pilih Gambar"), IMAGE_INPUT_CODE);

    }

    private void prepareDetect() {

        try {
            classifier = ObjectDetectionClassifier.create(
                    this,
                    getAssets(),
                    TF_OD_API_MODEL_FILE,
                    TF_OD_API_LABELS_FILE,
                    TF_OD_API_INPUT_SIZE,
                    TF_OD_API_IS_QUANTIZED);

            croppedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), croppedUri);
            bitmap = getResizedBitmap(croppedBitmap, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE);

            tracker = new MultiBoxTracker(this);
            tracker.setFrameConfiguration(croppedBitmap.getWidth(), croppedBitmap.getHeight(), 0);

            cropCopyBitmap = croppedBitmap.copy(croppedBitmap.getConfig(), true);
            int cropSize = TF_OD_API_INPUT_SIZE;
            frameToCropTransform = ImageUtils.getTransformationMatrix(
                    croppedBitmap.getWidth(), croppedBitmap.getHeight(),
                    cropSize, cropSize,
                    0, false);

            cropToFrameTransform = new Matrix();
            frameToCropTransform.invert(cropToFrameTransform);

        } catch (IOException e) {
            e.printStackTrace();
        }

        processImage();
    }


    private void processImage() {
        final Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(croppedBitmap, frameToCropTransform, null);

        final List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);
        final Canvas mCanvas = new Canvas(cropCopyBitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        final List<Classifier.Recognition> mappedRecognitions = new LinkedList<>();

        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                cropToFrameTransform.mapRect(location);
                result.setLocation(location);
                mappedRecognitions.add(result);
            }
        }

        classifier.close();
        tracker.trackResults(mappedRecognitions, 0);
        tracker.draw(mCanvas);

        ivStillImage.setImageBitmap(cropCopyBitmap);
    }

    // resizes bitmap to given dimensions
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);
        return Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
    }

    private void cropImage() {
        CropImage.activity(imageUri)
                .setAspectRatio(1, 1)
                .start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == IMAGE_INPUT_CODE) {
            if (resultCode == RESULT_OK) {
                imageUri = data.getData();
                if (imageUri != null) {
                    cropImage();
                } else {
                    finish();
                }
            }
        }

        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                if (result != null) {
                    croppedUri = result.getUri();
                    prepareDetect();
                }
            } else {
                Toast.makeText(this, "Error cropping image", Toast.LENGTH_SHORT).show();
            }
        }
    }
}