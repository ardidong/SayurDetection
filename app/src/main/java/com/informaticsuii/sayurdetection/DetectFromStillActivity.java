package com.informaticsuii.sayurdetection;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.informaticsuii.sayurdetection.classifier.Classifier;
import com.informaticsuii.sayurdetection.classifier.ObjectDetectionClassifier;
import com.informaticsuii.sayurdetection.env.ImageUtils;
import com.informaticsuii.sayurdetection.tracker.MultiBoxTracker;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static android.content.Intent.ACTION_PICK;

public class DetectFromStillActivity extends AppCompatActivity {
    private static final int IMAGE_INPUT_CODE = 2;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
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

    private void detectImage() {
        try {
            croppedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), croppedUri);
            classifier = ObjectDetectionClassifier.create(
                    this,
                    getAssets(),
                    TF_OD_API_MODEL_FILE,
                    TF_OD_API_LABELS_FILE,
                    TF_OD_API_INPUT_SIZE,
                    TF_OD_API_IS_QUANTIZED);

            if (croppedBitmap != null) {
                bitmap = getResizedBitmap(croppedBitmap, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        processImage();
    }

    private void cropImage() {
        CropImage.activity(imageUri)
                .setAspectRatio(1, 1)
                .start(this);
    }

    private void processImage() {
        final List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);

        cropCopyBitmap = croppedBitmap.copy(croppedBitmap.getConfig(), true);
        Bitmap btm = Bitmap.createBitmap(croppedBitmap.getWidth(), croppedBitmap.getHeight(), croppedBitmap.getConfig());
        final Canvas canvas = new Canvas(cropCopyBitmap);
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.0f);

        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;

        final List<Classifier.Recognition> mappedRecognitions = new LinkedList<>();

        for (final Classifier.Recognition result : results) {
            final RectF location = result.getLocation();
            if (location != null && result.getConfidence() >= minimumConfidence) {
                //canvas.drawRect(location, paint);

                //cropToFrameTransform.mapRect(location);
                result.setLocation(location);
                mappedRecognitions.add(result);
            }
        }

        classifier.close();
        MultiBoxTracker tracker = new MultiBoxTracker(this);
        tracker.setFrameConfiguration(croppedBitmap.getWidth(), croppedBitmap.getHeight(), 0);
        tracker.trackResults(mappedRecognitions, 0);
        tracker.draw(canvas);

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
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        return resizedBitmap;
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
                    detectImage();
                }
            } else {
                Toast.makeText(this, "Error cropping image", Toast.LENGTH_SHORT).show();
            }
        }
    }
}