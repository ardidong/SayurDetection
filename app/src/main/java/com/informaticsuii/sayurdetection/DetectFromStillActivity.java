package com.informaticsuii.sayurdetection;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.informaticsuii.sayurdetection.classifier.Classifier;
import com.informaticsuii.sayurdetection.classifier.ObjectDetectionClassifier;
import com.informaticsuii.sayurdetection.env.ImageUtils;
import com.informaticsuii.sayurdetection.tracker.MultiBoxTracker;
import com.theartofdev.edmodo.cropper.CropImage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import static android.content.Intent.ACTION_PICK;

public class DetectFromStillActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int IMAGE_INPUT_CODE = 2;
    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 1024;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";

    public static final String EXTRA_ACTION = "extra_action";
    public static final String EXTRA_URI = "extra_action";
    public static final int PICK_IMAGE = 0;
    public static final int DETECT_CAPTURED = 1;


    private Bitmap bitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap finalBitmap = null;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private Uri imageUri;
    private Uri croppedUri;
    private Classifier classifier;
    MultiBoxTracker tracker;

    private String currentPhotoPath;

    private ImageView ivStillImage;
    private Button btnPickImage;
    private Button btnSaveImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_from_still);

        ivStillImage = findViewById(R.id.iv_still_image);
        btnPickImage = findViewById(R.id.btn_pick_image);
        btnSaveImage = findViewById(R.id.btn_save_image);

        btnSaveImage.setEnabled(false);
        btnSaveImage.setOnClickListener(this);
        btnPickImage.setOnClickListener(this);

        int action = getIntent().getIntExtra(EXTRA_ACTION, 0);
        switch (action) {
            case PICK_IMAGE:
                pickImage();
                break;

            case DETECT_CAPTURED:
                Uri uri = Uri.parse(getIntent().getExtras().getString("extra_uri"));
                try {
                    displayCapturedImage(uri);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;

        }


    }

    private void displayCapturedImage(Uri uri) throws IOException {

        ExifInterface ei = new ExifInterface(uri.getPath());
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED);

        Bitmap bmp = MediaStore.Images.Media.getBitmap(this.getContentResolver(), uri);

        switch (orientation) {

            case ExifInterface.ORIENTATION_ROTATE_90:
                croppedBitmap = rotateImage(bmp, 90);
                break;

            case ExifInterface.ORIENTATION_ROTATE_180:
                croppedBitmap = rotateImage(bmp, 180);
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                croppedBitmap = rotateImage(bmp, 270);
                break;

            case ExifInterface.ORIENTATION_NORMAL:
            default:
                croppedBitmap = bmp;
        }

        prepareDetect();
    }

    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.btn_pick_image) {
            pickImage();
        } else if (view.getId() == R.id.btn_save_image) {
            try {
                saveImage();
            } catch (IOException e) {
                Toast.makeText(this, "Cannot save image!", Toast.LENGTH_SHORT).show();
            }
        }

    }

    private void pickImage() {
        Intent imageIntent = new Intent();
        imageIntent.setAction(ACTION_PICK);
        imageIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        imageIntent.setType("image/*");
        startActivityForResult(Intent.createChooser(imageIntent, "Pilih Gambar"), IMAGE_INPUT_CODE);
    }

    private File createImageFile() throws IOException {
        File storageDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES + File.separator + "SayurDetection/");
        if (!storageDir.exists()) {
            storageDir.mkdirs();
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";

        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(currentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    private void saveImage() throws IOException {
        File file = createImageFile();
        FileOutputStream out = new FileOutputStream(file);
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
        out.flush();
        out.close();
        galleryAddPic();

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

            //croppedBitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), croppedUri);
            bitmap = getResizedBitmap(croppedBitmap, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE);

            tracker = new MultiBoxTracker(this);
            tracker.setFrameConfiguration(croppedBitmap.getWidth(), croppedBitmap.getHeight(), 0);

            finalBitmap = croppedBitmap.copy(croppedBitmap.getConfig(), true);
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
        final Canvas mCanvas = new Canvas(finalBitmap);
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

        showImage();
    }

    private void showImage() {
        if (finalBitmap != null) {
            ivStillImage.setImageBitmap(finalBitmap);
            btnSaveImage.setEnabled(true);
        }
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
                    try {
                        displayCapturedImage(croppedUri);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } else {
                Toast.makeText(this, "Error cropping image", Toast.LENGTH_SHORT).show();
            }
        }
    }
}