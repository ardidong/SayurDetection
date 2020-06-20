package com.informaticsuii.sayurdetection.classifier;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.util.List;
import java.util.Vector;

public class ObjectDetectionClassifier implements Classifier {

    //Jumlah Hasil Deteksi
    private static final int NUM_DETECTTIONS = 10;
    //Float Model
    private static final float IMAGE_MEAN = 128.0f;
    private static final float IMAGE_STD = 128.0f;
    // Number of threads in the java app
    private static final int NUM_THREADS = 4;
    private boolean isModelQuantized;
    // Config values.
    private int inputSize;
    // Pre-allocated buffers.
    private Vector<String> labels = new Vector<String>();
    private int[] intValues;
    // outputLocations: array of shape [Batchsize, NUM_DETECTIONS,4]
    // contains the location of detected boxes
    private float[][][] outputLocations;
    // outputClasses: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the classes of detected boxes
    private float[][] outputClasses;
    // outputScores: array of shape [Batchsize, NUM_DETECTIONS]
    // contains the scores of detected boxes
    private float[][] outputScores;
    // numDetections: array of shape [Batchsize]
    // contains the number of detected boxes
    private float[] numDetections;

    ByteBuffer imageData;
    private Interpreter interpreter;
    private MappedByteBuffer tfLiteModel;

    public ObjectDetectionClassifier() {
    }

    public static Classifier create(final Activity activity, final AssetManager assetManager,
                                    final String modelFileName, final String labelFileName,
                                    final int inputSize, final boolean isModelQuantized) throws IOException {

        final ObjectDetectionClassifier d = new ObjectDetectionClassifier();
        //Menginput labels ke dalam vector
        String actualFileName = labelFileName.split("file:///android_asset/")[1];
        InputStream labelsInput = assetManager.open(actualFileName);
        BufferedReader br = new BufferedReader(new InputStreamReader(labelsInput));
        String line;
        while ((line = br.readLine()) != null){
            d.labels.add(line);
        }
        br.close();

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(5);
        options.setUseNNAPI(true);

        d.inputSize = inputSize;
        d.tfLiteModel = FileUtil.loadMappedFile(activity, modelFileName);
        d.interpreter = new Interpreter(d.tfLiteModel, options);

        d.isModelQuantized = isModelQuantized;

        //pre-allocate buffer
        int numBytesPerChannel;
        if(isModelQuantized){
            numBytesPerChannel = 1; //Quantized
        } else {
            numBytesPerChannel = 4; //Floating Point
        }

        d.imageData = ByteBuffer.allocateDirect(1 * d.inputSize * d.inputSize * 3 * numBytesPerChannel);
        d.imageData.order(ByteOrder.nativeOrder());
        d.intValues = new int[d.inputSize * d.inputSize];

        d.outputLocations = new float[1][NUM_DETECTTIONS][4];
        d.outputClasses = new float[1][NUM_DETECTTIONS];
        d.outputScores = new float[1][NUM_DETECTTIONS];
        d.numDetections = new float[1];

        return d;
    }


    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        return null;
    }

    @Override
    public void enableStatLogging(boolean debug) {

    }

    @Override
    public String getStatString() {
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public void setNumThreads(int num_threads) {

    }

    @Override
    public void setUseNNAPI(boolean isChecked) {

    }
}
