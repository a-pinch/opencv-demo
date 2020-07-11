package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

@Slf4j
public class SynchronisationWatcher {

    private final int BLINK_FRAMES_COUNT = 10;
    private final Scalar RED = new Scalar(0, 0, 255);
    private final Scalar BLUE = new Scalar(255, 0, 0);
    private final Scalar GREEN = new Scalar(0, 255, 0);

    private Rect watcher;
    private int okCounter = 0;
    private int blinkCounter = 0;
    private int distCounter = 0;
    private boolean warn = false;
    private Mat diff = new Mat();

    private Mat[] frameCopy;

    public SynchronisationWatcher(Rect watcher) {
        this.watcher = watcher;
    }

    public int check(Mat[] frames, int queueSize){
        int distortion = 0;
//        if(distCounter == 0) {
            distortion = compare(frames);
//        }
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
                log.debug(String.format("queue: %d; distortion: %d (delay %d)", queueSize, distortion, returnDist) );
                return returnDist;
            }
        }
//        log.debug(String.format("dist: %d blinkCounter: %d, worn: %b", distortion, blinkCounter, warn));
        if(blinkCounter <=0 && distortion>30){
            blinkCounter = BLINK_FRAMES_COUNT * 3;
        }
        if(blinkCounter > 0) blink(frames);
        log.trace(String.format("queue: %d; distortion: %d (%d)", queueSize, distortion, distCounter) );
        return 0;
    }

    public int compare(Mat ... frames){


        //init
        Mat vAll = frames[0].submat(new Rect(watcher.x, watcher.y, watcher.width, watcher.height));
        Mat tAll = frames[1].submat(new Rect(watcher.x, watcher.y, watcher.width, watcher.height));

        Imgproc.cvtColor(vAll, vAll, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(tAll, tAll, Imgproc.COLOR_BGR2GRAY);

        //dist
        Core.absdiff(vAll, tAll, diff);
        Imgproc.threshold(diff,diff,100,255,Imgproc.THRESH_BINARY);

        int distAll = Core.countNonZero(diff);
        return distAll;

    }

    private void blink(Mat[] frames){
        if(blinkCounter-- % BLINK_FRAMES_COUNT == 0){
            warn = !warn;
        }
        if(warn) warn(frames);
    }

    private void warn(Mat[] frames){
        Imgproc.rectangle(frames[0], watcher, RED);
        Imgproc.rectangle(frames[1], watcher, BLUE);
    }

    private void ok(Mat[] frames){
        Imgproc.rectangle(frames[0], watcher, GREEN);
        Imgproc.rectangle(frames[1], watcher, GREEN);
    }
}
