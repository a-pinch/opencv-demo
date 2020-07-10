package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import sync.opencv.config.Watcher;

@Slf4j
public class SynchronisationWatcher {

    private final int BLINK_FRAMES_COUNT = 10;
    private final Scalar RED = new Scalar(0, 0, 255);
    private final Scalar BLUE = new Scalar(255, 0, 0);
    private final Scalar GREEN = new Scalar(0, 255, 0);

    private Watcher watcher;
    private int okCounter = 0;
    private int blinkCounter = 0;
    private int distCounter = 0;
    private boolean warn = false;
//    private Mat diff = new Mat();
    private Mat dSec = new Mat();
    private Mat dDsec = new Mat();
    private Mat dMin = new Mat();

    private Mat[] frameCopy;

    public SynchronisationWatcher(Watcher watcher) {
        this.watcher = watcher;
    }

    public int check(Mat[] frames){
        int distortion = 0;
        if(distCounter == 0) {
            distortion = compare(frames);
        }
        boolean b = false;
        if(distortion > 30 && distCounter == 0) {
            frameCopy = new Mat[frames.length];
            for (int i = 0; i < frames.length; i++) frameCopy[i] = frames[i].clone();
            distCounter = 1;
            b = true;
        }
        if(!b && distCounter > 0){
            distCounter++;
            int leftDist = compare(new Mat[]{frameCopy[0], frames[1]});
            int rightDist = compare(new Mat[]{frameCopy[1], frames[0]});
            if(leftDist < 30 || rightDist < 30) {
                int returnDist = leftDist < 30 ? -distCounter : distCounter;
                distCounter = 0;
                okCounter = BLINK_FRAMES_COUNT;
                return returnDist;
            }
        }
//        log.debug(String.format("dist: %d blinkCounter: %d, worn: %b", distortion, blinkCounter, warn));
        if(blinkCounter <=0 && distortion>30){
            blinkCounter = BLINK_FRAMES_COUNT * 3;
        }
        if(blinkCounter > 0) blink(frames);
        return 0;
    }

    private int compare(Mat[] frames){


        //init
        Mat vSec = frames[0].submat(new Rect(watcher.getSec().x, watcher.getSec().y, watcher.getSec().width, watcher.getSec().height));
        Mat vDSec = frames[0].submat(new Rect(watcher.getDsec().x, watcher.getDsec().y, watcher.getDsec().width, watcher.getDsec().height));
        Mat vMin = frames[0].submat(new Rect(watcher.getMin().x, watcher.getMin().y, watcher.getMin().width, watcher.getMin().height));
//        Mat vAll = frames[0].submat(new Rect(watcher.getAll().x, watcher.getAll().y, watcher.getAll().width, watcher.getAll().height));

        Mat tSec = frames[1].submat(new Rect(watcher.getSec().x, watcher.getSec().y, watcher.getSec().width, watcher.getSec().height));
        Mat tDSec = frames[1].submat(new Rect(watcher.getDsec().x, watcher.getDsec().y, watcher.getDsec().width, watcher.getDsec().height));
        Mat tMin = frames[1].submat(new Rect(watcher.getMin().x, watcher.getMin().y, watcher.getMin().width, watcher.getMin().height));
//        Mat tAll = frames[1].submat(new Rect(watcher.getAll().x, watcher.getAll().y, watcher.getAll().width, watcher.getAll().height));

        Imgproc.cvtColor(vSec, vSec, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(vDSec, vDSec, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(vMin, vMin, Imgproc.COLOR_BGR2GRAY);
//        Imgproc.cvtColor(vAll, vAll, Imgproc.COLOR_BGR2GRAY);

        Imgproc.cvtColor(tSec, tSec, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(tDSec, tDSec, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(tMin, tMin, Imgproc.COLOR_BGR2GRAY);
//        Imgproc.cvtColor(tAll, tAll, Imgproc.COLOR_BGR2GRAY);

        //dist
        Core.absdiff(vSec, tSec, dSec);
        Core.absdiff(vDSec, tDSec, dDsec);
        Core.absdiff(vMin, tMin, dMin);
//        Core.absdiff(vAll, tAll, diff);

        Imgproc.threshold(dSec,dSec,100,255,Imgproc.THRESH_BINARY);
        Imgproc.threshold(dDsec,dDsec,100,255,Imgproc.THRESH_BINARY);
        Imgproc.threshold(dMin,dMin,100,255,Imgproc.THRESH_BINARY);
//        Imgproc.threshold(diff,diff,100,255,Imgproc.THRESH_BINARY);

        int distSec = Core.countNonZero(dSec);
        int distDsec = Core.countNonZero(dDsec);
        int distMin = Core.countNonZero(dMin);
//        int distAll = Core.countNonZero(diff);
        int dist = distMin+distDsec+distSec;

        log.debug(String.format("distortion: %d, %d, %d {%d}", distMin, distDsec, distSec, dist) );

        return dist;

    }

    private void blink(Mat[] frames){
        if(blinkCounter-- % BLINK_FRAMES_COUNT == 0){
            warn = !warn;
        }
        if(warn) warn(frames);
    }

    private void warn(Mat[] frames){
//        Imgproc.rectangle(frames[0], watcher.getSec(), RED);
//        Imgproc.rectangle(frames[0], watcher.getDsec(), RED);
//        Imgproc.rectangle(frames[0], watcher.getMin(), RED);
        Imgproc.rectangle(frames[0], watcher.getAll(), RED);

//        Imgproc.rectangle(frames[1], watcher.getSec(), BLUE);
//        Imgproc.rectangle(frames[1], watcher.getDsec(), BLUE);
//        Imgproc.rectangle(frames[1], watcher.getMin(), BLUE);
        Imgproc.rectangle(frames[1], watcher.getAll(), BLUE);
    }

    private void ok(Mat[] frames){
//        Imgproc.rectangle(frames[0], watcher.getSec(), GREEN);
//        Imgproc.rectangle(frames[0], watcher.getDsec(), GREEN);
//        Imgproc.rectangle(frames[0], watcher.getMin(), GREEN);
        Imgproc.rectangle(frames[0], watcher.getAll(), GREEN);

//        Imgproc.rectangle(frames[1], watcher.getSec(), GREEN);
//        Imgproc.rectangle(frames[1], watcher.getDsec(), GREEN);
//        Imgproc.rectangle(frames[1], watcher.getMin(), GREEN);
        Imgproc.rectangle(frames[1], watcher.getAll(), GREEN);
    }
}
