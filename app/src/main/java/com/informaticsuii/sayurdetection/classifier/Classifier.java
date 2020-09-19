package com.informaticsuii.sayurdetection.classifier;

import android.graphics.Bitmap;
import android.graphics.RectF;

import java.util.List;

public interface Classifier {
    List<Recognition> recognizeImage(Bitmap bitmap);

    void enableStatLogging(final boolean debug);

    String getStatString();

    void close();

    void setNumThreads(int num_threads);

    void setUseNNAPI(boolean isChecked);

    class Recognition {
        private String id;
        private String title;
        private Float confidence;
        private RectF location;


        public Recognition(final String id, final String title, final Float confidence, final RectF location) {
            this.id = id;
            this.title = title;
            this.confidence = confidence;
            this.location = location;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public Float getConfidence() {
            return confidence;
        }

        public RectF getLocation() {
            return location;
        }

        public void setLocation(RectF location) {
            this.location = location;
        }

        @Override
        public String toString() {
            return "Recognition{" +
                    "id='" + id + '\'' +
                    ", title='" + title + '\'' +
                    ", confidence=" + String.format("(%.1f%%) ", confidence * 100.0f) +
                    ", location=" + location +
                    '}';
        }
    }


}


