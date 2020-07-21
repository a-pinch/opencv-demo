package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

import static org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY;

@Slf4j
public class SingleMotionDetector {

    Mat bg;
    double accumWeight;
    int frameCount;
    int tVal = 25;
    int totalFrames = 0;

    public SingleMotionDetector(double accumWeight, int frameCount, int threshold) {
        this.accumWeight = accumWeight;
        this.frameCount = frameCount;
        this.tVal = threshold;
    }

    public void update(Mat image){
        if(bg == null){
            bg = new Mat();
            image.copyTo(bg);
            bg.convertTo(bg, CvType.CV_32F);
            return;
        }
        Imgproc.accumulateWeighted(image, bg, accumWeight);
    }


    public Mat detect(Mat image){

        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(7, 7), 0);

        if(totalFrames > frameCount) {

            Mat delta = new Mat();
            Mat bgUint8 = new Mat();
            bg.convertTo(bgUint8, CvType.CV_8U);
            Core.absdiff(bgUint8, gray, delta);

            Imgproc.threshold(delta, delta, tVal, 255, Imgproc.THRESH_BINARY);

//            Imgproc.erode(delta, delta, new Mat(), new Point(-1, -1), 1);
            Imgproc.dilate(delta, delta, new Mat(), new Point(-1, -1), 2);

            Mat hierarchy = new Mat();
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(delta, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            int[] p = {-1, -1, -1, -1};
            contours.forEach(m -> {
                Rect r = Imgproc.boundingRect(m);
                p[0] = p[0] == -1 ? r.x : Math.min(p[0], r.x);
                p[1] = p[1] == -1 ? r.y : Math.min(p[1], r.y);
                p[2] = Math.max(p[2], r.x+r.width);
                p[3] = Math.max(p[3], r.y+r.height);
            });
            Imgproc.rectangle(image, new Rect(p[0], p[1], p[2]-p[0], p[3]-p[1]), new Scalar(0, 0, 255), 2);

            hierarchy.release();
            delta.release();
            bgUint8.release();
        }

        update(gray);
        totalFrames++;
        gray.release();

        return image;
    }
}
