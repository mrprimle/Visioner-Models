package com.example.opencv34test;

import androidx.appcompat.app.AppCompatActivity;

//import android.graphics.Point;
import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Toast;

//import com.example.opencv34test.ml.Detect;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.JavaCameraView;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.*;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

//import org.tensorflow.lite.DataType;
//import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;



public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    CameraBridgeViewBase cameraBridgeViewBase;
    BaseLoaderCallback baseLoaderCallback;

    /*
    // Configuration values for the prepackaged SSD model.
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = true;
    private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
    // Minimum detection confidence to track a detection.
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final android.util.Size DESIRED_PREVIEW_SIZE = new android.util.Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    private Integer sensorOrientation;

    private Detect detector;

     */


    boolean startCanny = false;
    int startGC = 200;
    static Point[] avgLeft = {new Point(0, 0), new Point(0, 0)};
    static Point[] avgRight = {new Point(0, 0), new Point(0, 0)};




    public void Canny(View Button) {
        startCanny = (!startCanny);
    }



    public static Point seg_intersect (Point a1, Point a2, Point b1, Point b2) {
        Point da = new Point((a2.x - a1.x), (a2.y - a1.y));
        Point db = new Point((b2.x - b1.x), (b2.y - b1.y));
        Point dp = new Point((a1.x - b1.x), (a1.y - b1.y));
        Point dap = new Point(-da.y, da.x);
        double denom = dap.x * db.x + dap.y * db.y;
        double num = dap.x * dp.x + dap.y * dp.y;
        double m = num / denom;
        return new Point(db.x * m + b1.x, db.y * m + b1.y);
    }

    public static double movingAverage (double avg, double new_sample) {
        if (avg == 0) {
            return new_sample;
        }
        avg -= avg / 20;
        avg += new_sample / 20;
        return avg;
    }

    public static Point[][] draw_lines (Mat img, Mat lines) {
        // state variables to keep track of most dominant segment
        double largestLeftLineSize = 0;
        double largestRightLineSize = 0;
        Point[] largestLeftLine = {new Point(0, 0), new Point(0, 0)};
        Point[] largestRightLine = {new Point(0, 0), new Point(0, 0)};

        if (lines == null) {
            Imgproc.line(img, avgLeft[0], avgLeft[1], new Scalar(255, 255, 255, 200), 12);
            Imgproc.line(img, avgRight[0], avgRight[1], new Scalar(255, 255, 255, 200), 12);
            return new Point[][]{avgLeft, avgRight};
        }

        for (int x = 0; x < lines.rows(); x++) {
            double[] vec = lines.get(x, 0);
            double x1 = vec[0],
                    y1 = vec[1],
                    x2 = vec[2],
                    y2 = vec[3];
            Point start = new Point(x1, y1);
            Point end = new Point(x2, y2);
            double dx = x2 - x1;
            double dy = y2 - y1;
            double size = Math.sqrt(dx * dx + dy * dy);
            double slope = dy / dx;

            // Filter slope based on incline and
            // find the most dominent segment based on length
            if (slope < -0.5) { // right
                if (size > largestRightLineSize) {
                    largestRightLine = new Point[]{start, end};
                }
                Imgproc.line(img, start, end, new Scalar(255, 0, 0, 80), 2);
            } else if (slope > 0.5) { // left
                if (size > largestLeftLineSize) {
                    largestLeftLine = new Point[]{start, end};
                }
                Imgproc.line(img, start, end, new Scalar(255, 0, 0, 80), 2);
            }
        }


        // Define an imaginary horizontal line in the center of the screen
        // and at the bottom of the image, to extrapolate determined segment
        int imgWidth = img.cols();
        int imgHeight = img.rows();
        Point upLinePoint1 = new Point(0, Math.round(imgHeight - (imgHeight/3.0)));
        Point upLinePoint2 = new Point(imgWidth, Math.round(imgHeight - (imgHeight/3.0)));
        Point downLinePoint1 = new Point(0, imgHeight);
        Point downLinePoint2 = new Point(imgWidth, imgHeight);

        // CALC LEFT LINE
        // Find the intersection of dominant lane with an imaginary horizontal line
        // in the middle of the image and at the bottom of the image.
        Point p3 = largestLeftLine[0];
        Point p4 = largestLeftLine[1];
        Point upLeftPoint = seg_intersect(upLinePoint1, upLinePoint2, p3, p4);
        Point downLeftPoint = seg_intersect(downLinePoint1,downLinePoint2, p3,p4);
        // If no intersection - base case from prev iter
        if (Double.isNaN(upLeftPoint.x) || Double.isNaN(downLeftPoint.x)) {
            Imgproc.line(img, avgLeft[0], avgLeft[1], new Scalar(255, 255, 255, 200), 12);
            Imgproc.line(img, avgRight[0], avgRight[1], new Scalar(255, 255, 255, 200), 12);
            return new Point[][]{avgLeft, avgRight};
        }
        Imgproc.line(img, upLeftPoint, downLeftPoint, new Scalar(0, 0, 255, 100), 8);

        // MAIN LEFT Calculate the average position of detected left lane over multiple video frames and draw
        double avgx1 = avgLeft[0].x;
        double avgy1 = avgLeft[0].y;
        double avgx2 = avgLeft[1].x;
        double avgy2 = avgLeft[1].y;
        avgLeft = new Point[]{new Point(movingAverage(avgx1, upLeftPoint.x), movingAverage(avgy1, upLeftPoint.y)), new Point(movingAverage(avgx2, downLeftPoint.x), movingAverage(avgy2, downLeftPoint.y))};
        Imgproc.line(img, avgLeft[0], avgLeft[1], new Scalar(255, 255, 255, 200), 12);



        // CALC RIGHT LINE
        Point p5 = largestRightLine[0];
        Point p6 = largestRightLine[1];
        Point upRightPoint = seg_intersect(upLinePoint1,upLinePoint2, p5,p6);
        Point downRightPoint = seg_intersect(downLinePoint1,downLinePoint2, p5,p6);
        if (Double.isNaN(upRightPoint.x) || Double.isNaN(downRightPoint.x)) {
            Imgproc.line(img, avgLeft[0], avgLeft[1], new Scalar(255, 255, 255, 200), 12);
            Imgproc.line(img, avgRight[0], avgRight[1], new Scalar(255, 255, 255, 200), 12);
            return new Point[][]{avgLeft, avgRight};
        }
        Imgproc.line(img, upRightPoint, downRightPoint, new Scalar(0, 0, 255, 100), 8);

        // MAIN RIGHT Calculate the average position of detected right lane over multiple video frames and draw
        avgx1 = avgRight[0].x;
        avgy1 = avgRight[0].y;
        avgx2 = avgRight[1].x;
        avgy2 = avgRight[1].y;
        avgRight = new Point[]{new Point(movingAverage(avgx1, upRightPoint.x), movingAverage(avgy1, upRightPoint.y)), new Point(movingAverage(avgx2, downRightPoint.x), movingAverage(avgy2, downRightPoint.y))};
        Imgproc.line(img, avgRight[0], avgRight[1], new Scalar(255, 255, 255, 200), 12);

        return new Point[][]{avgLeft, avgRight};
    }


    public static Mat roi(Mat image) {
        int frameWidth = image.cols();
        int frameHeight = image.rows();
        int lineType = 8;
        int shift = 0;
        Mat mask = Mat.zeros(frameHeight, frameWidth, CvType.CV_8UC1);

        List<Point> points = new ArrayList<>();
        points.add(new Point(Math.round(4*frameWidth/7.0), Math.round(3*frameHeight/5.0)));
        points.add(new Point(Math.round(3*frameWidth/7.0), Math.round(3*frameHeight/5.0)));
        points.add(new Point(40, frameHeight - 20));
        points.add(new Point(frameWidth - 40, frameHeight - 20));
        MatOfPoint matPt = new MatOfPoint();
        matPt.fromList(points);
        List<MatOfPoint> ppt = new ArrayList<MatOfPoint>();
        ppt.add(matPt);

        Imgproc.fillPoly(mask,
                ppt,
                new Scalar( 255 ),
                lineType,
                shift,
                new Point(0,0) );

        Mat masked = new Mat(image.size(), image.type());

        Core.bitwise_and(image, mask, masked);

        return masked;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        cameraBridgeViewBase = (JavaCameraView)findViewById(R.id.CameraView);
        cameraBridgeViewBase.setVisibility(SurfaceView.VISIBLE);
        cameraBridgeViewBase.setCvCameraViewListener(this);


        //System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        baseLoaderCallback = new BaseLoaderCallback(this) {
            @Override
            public void onManagerConnected(int status) {
                super.onManagerConnected(status);

                if (status == BaseLoaderCallback.SUCCESS) {
                    cameraBridgeViewBase.enableView();
                } else {
                    super.onManagerConnected(status);
                }


            }

        };




    }

    @SuppressLint("DefaultLocale")
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        Mat frame = inputFrame.rgba();
        Mat frameInit = frame.clone();

        /*String logS = String.format("Width: %1$d", frame.cols());
        Log.i("SizeF", logS);
        logS = String.format("Height: %1$d", frame.rows());
        Log.i("SizeF", logS);

         */
        /*int pid = android.os.Process.myPid();
        String whiteList = "logcat -P '" + pid + "'";
        Log.i("SizeF", whiteList);
         */

        /*try {
            Detect model = Detect.newInstance(context);

            // Creates inputs for reference.
            TensorBuffer image = TensorBuffer.createFixedSize(new int[]{1, 300, 300, 3}, DataType.UINT8);
            image.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            Detect.Outputs outputs = model.process(image);
            TensorBuffer location = outputs.getLocationAsTensorBuffer();
            TensorBuffer category = outputs.getCategoryAsTensorBuffer();
            TensorBuffer score = outputs.getScoreAsTensorBuffer();
            TensorBuffer numberOfDetections = outputs.getNumberOfDetectionsAsTensorBuffer();

            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // do Handle the exception
        }

         */



        /*try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            this,
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing Detector!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Detector could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }
        
         */







        if (startCanny) {
            int frameWidth = frame.cols();
            int frameHeight = frame.rows();
            int centerL = (int) Math.round(frameWidth * 0.2);
            int centerR = (int) Math.round(frameWidth * 0.8);
            int offsetH = (int) Math.round(0.8 * frameHeight);


            Point vert1 = new Point(Math.round(4*frameWidth/7.0), Math.round(3*frameHeight/5.0));
            Point vert2 = new Point(Math.round(3*frameWidth/7.0), Math.round(3*frameHeight/5.0));
            Point vert3 = new Point(40, frameHeight - 20);
            Point vert4 = new Point(frameWidth - 40, frameHeight - 20);

            Imgproc.line(frameInit, vert1, vert2, new Scalar(230,57,168, 50),2); // top ROI
            Imgproc.line(frameInit, vert2, vert3, new Scalar(230,57,168, 50),2); // left ROI
            Imgproc.line(frameInit, vert3, vert4, new Scalar(230,57,168, 50),2); // bottom ROI
            Imgproc.line(frameInit, vert1, vert4, new Scalar(230,57,168, 50),2); // right ROI


            Mat lines = new Mat();
            int threshold = 40;
            int minLineSize = 30;
            int lineGap = 200;

            Imgproc.cvtColor(frame, frame, Imgproc.COLOR_RGBA2GRAY);
            Imgproc.blur(frame, frame, new Size(5, 5));
            Imgproc.Canny(frame, frame, 100, 80);
            frame = roi(frame);
            Imgproc.HoughLinesP(frame, lines, 1, Math.PI/180, threshold,
                    minLineSize, lineGap);
            /*String logS = String.format("shape: %d", );
            Log.i("shape canny", logS);*/



            Point[][] mainLines = draw_lines(frameInit, lines);

            int tempL = (int) Math.round((mainLines[0][0].x + mainLines[0][1].x) / 2);
            int tempR = (int) Math.round((mainLines[1][0].x + mainLines[1][1].x) / 2);
            Imgproc.line(frameInit, new Point(0, offsetH), new Point(frameWidth, offsetH), new Scalar(0,255, 0, 50),2);

            Imgproc.line(frameInit, new Point(centerL, offsetH - 25), new Point(centerL, offsetH + 25), new Scalar(255,0, 0, 50),4);
            Imgproc.line(frameInit, new Point(centerR, offsetH - 25), new Point(centerR, offsetH + 25), new Scalar(255,0, 0, 50),4);
            Imgproc.line(frameInit, new Point(tempL, offsetH - 10), new Point(tempL, offsetH + 10), new Scalar(0,0, 250, 50),4);
            Imgproc.line(frameInit, new Point(tempR, offsetH - 10), new Point(tempR, offsetH + 10), new Scalar(0,0, 250, 50),4);

            double offcenter = (tempL - centerL + tempR - centerR) / 2.0;
            String logS = String.format("Offset: %.1f", offcenter);
            Imgproc.putText (
                    frameInit,
                    logS,
                    new Point(20, 80),
                    Core.FONT_HERSHEY_SIMPLEX ,
                    3,
                    new Scalar(255, 255, 255),
                    5
            );



        }


        // Implicit Garbage Collection to prevent memleak
        startGC--;
        if (startGC==0) {
            Log.i("GARB COLL", "done SUCCESFULLY!");
            System.gc();
            System.runFinalization();
            startGC=200;
        }

        return frameInit;
    }


    @Override
    public void onCameraViewStarted(int width, int height) {

    }


    @Override
    public void onCameraViewStopped() {

    }


    @Override
    protected void onResume() {
        super.onResume();

        if (!OpenCVLoader.initDebug()){
            Toast.makeText(getApplicationContext(),"[OPENCV LOADER]: Something's wrong", Toast.LENGTH_SHORT).show();
        }

        else
        {
            baseLoaderCallback.onManagerConnected(baseLoaderCallback.SUCCESS);
        }



    }

    @Override
    protected void onPause() {
        super.onPause();
        if(cameraBridgeViewBase!=null){

            cameraBridgeViewBase.disableView();
        }

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraBridgeViewBase!=null){
            cameraBridgeViewBase.disableView();
        }
    }
}