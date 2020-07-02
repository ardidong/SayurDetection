package com.informaticsuii.sayurdetection;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;

import java.io.FileInputStream;

public class DetectFromStillActivity extends AppCompatActivity {
    public static final String EXTRA_IMAGE = "extra_image";

    private ImageView ivStillImage;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detect_from_still);

        Bitmap bmp = null;
        String filename = getIntent().getStringExtra(EXTRA_IMAGE);
        try {
            FileInputStream inputStream = this.openFileInput(filename);
            bmp = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        ivStillImage = findViewById(R.id.iv_still_image);
        ivStillImage.setImageBitmap(bmp);

    }
}