package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.springframework.stereotype.Component;
import sync.opencv.motion.MotionDetector;
import sync.opencv.motion.MultiMotionDetector;
import sync.opencv.motion.RectTreck;

import java.util.Queue;

@Component
@Slf4j
public class FrameProcessor implements Runnable {

    private Queue<Mat[]> rowFramesQueue;
    private Queue<Mat[]> processedFramesQueue;
    private RectTreck[] watchBoxes;
    private MotionDetector visualMotionDetector = new MultiMotionDetector(0.01, 32, 25);
    private MotionDetector thermalMotionDetector = new MultiMotionDetector(0.01, 32, 25);

    boolean stopped = false;

    public FrameProcessor() {
    }

    public FrameProcessor(Queue<Mat[]> rowFramesQueue, Queue<Mat[]> processedFramesQueue, RectTreck[] watchBoxes) {
        this.rowFramesQueue = rowFramesQueue;
        this.processedFramesQueue = processedFramesQueue;
        this.watchBoxes = watchBoxes;
    }

    @Override
    public void run() {
        while(!stopped){
            try {
                detectFaces();
            }catch (Exception e){
                stopped = true;
                throw new IllegalStateException(e);
            }
        }
    }

    public void stop(){
        stopped = true;
    }

    public boolean isStopped() {
        return stopped;
    }

    private void detectFaces() throws InterruptedException {

        Mat[] rowFrames = rowFramesQueue.poll();

        if(rowFrames == null){
            Thread.sleep(10);
        } else {
            // todo put your code here instead
            Mat[] processedFrames = new Mat[rowFrames.length];
            watchBoxes[0] = visualMotionDetector.detect(rowFrames[0]);
            watchBoxes[1] = thermalMotionDetector.detect(rowFrames[1]);
            processedFramesQueue.offer(rowFrames);
        }

    }

}
