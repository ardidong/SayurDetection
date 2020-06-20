package com.informaticsuii.sayurdetection.classifier;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Trace;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

public class ObjectDetectionClassifier implements Classifier {

    //Only returns this many results
    private static final int NUM_DETECTIONS = 10;
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
        while ((line = br.readLine()) != null) {
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
        if (isModelQuantized) {
            numBytesPerChannel = 1; //Quantized
        } else {
            numBytesPerChannel = 4; //Floating Point
        }

        d.imageData = ByteBuffer.allocateDirect(1 * d.inputSize * d.inputSize * 3 * numBytesPerChannel);
        d.imageData.order(ByteOrder.nativeOrder());
        d.intValues = new int[d.inputSize * d.inputSize];

        d.outputLocations = new float[1][NUM_DETECTIONS][4];
        d.outputClasses = new float[1][NUM_DETECTIONS];
        d.outputScores = new float[1][NUM_DETECTIONS];
        d.numDetections = new float[1];

        return d;
    }


    @Override
    public List<Recognition> recognizeImage(Bitmap bitmap) {
        Trace.beginSection("recognize image");

        Trace.beginSection("preprocessBitmap");
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imageData.rewind();
        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imageData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imageData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imageData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imageData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imageData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imageData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                }
            }
        }

        Trace.endSection(); //end prepocessbitmap trace

        // Copy the input data into TensorFlow.
        Trace.beginSection("feed");
        outputLocations = new float[1][NUM_DETECTIONS][4];
        outputClasses = new float[1][NUM_DETECTIONS];
        outputScores = new float[1][NUM_DETECTIONS];
        numDetections = new float[1];

        Object[] inputArray = {imageData};
        Map<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, outputLocations);
        outputMap.put(1, outputClasses);
        outputMap.put(2, outputScores);
        outputMap.put(3, numDetections);
        Trace.endSection();

        Trace.beginSection("run");
        interpreter.runForMultipleInputsOutputs(inputArray, outputMap);
        Trace.endSection();

        int numDetectionsOutput = Math.min(NUM_DETECTIONS, (int) numDetections[0]);

        final ArrayList<Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
        for (int i = 0; i < numDetectionsOutput; ++i) {
            final RectF detection = new RectF(
                    outputLocations[0][i][1] * inputSize,
                    outputLocations[0][i][0] * inputSize,
                    outputLocations[0][i][3] * inputSize,
                    outputLocations[0][i][2] * inputSize);
            // SSD Mobilenet V1 Model assumes class 0 is background class
            // in label file and class labels start from 1 to number_of_classes+1,
            // while outputClasses correspond to class index from 0 to number_of_classes
            int labelOffset = 1;
            recognitions.add(
                    new Recognition(
                            "" + i,
                            labels.get((int) outputClasses[0][i] + labelOffset),
                            outputScores[0][i],
                            detection));

        }
        Trace.endSection();
        return recognitions;
    }

    @Override
    public void enableStatLogging(boolean debug) {

    }

    @Override
    public String getStatString() {
        return "";
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
