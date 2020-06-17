package com.informaticsuii.sayurdetection;


import android.util.Size;

public class MainActivity extends CameraActivity {
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

    @Override
    protected void processImage() {

    }

    @Override
    protected void onPreviewSizeChosen(Size size, int rotation) {

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