package sync.opencv;

import org.opencv.core.Mat;

import java.util.Queue;

public class FrameProcessor implements Runnable {

    Queue<Mat[]> rowFramesQueue;
    Queue<Mat[]> processedFramesQueue;
    SingleMotionDetector visualMotionDetector = new SingleMotionDetector(0.01, 32, 25);
    SingleMotionDetector thermalMotionDetector = new SingleMotionDetector(0.01, 32, 25);

    boolean stopped = false;

    public FrameProcessor(Queue<Mat[]> rowFramesQueue, Queue<Mat[]> processedFramesQueue) {
        this.rowFramesQueue = rowFramesQueue;
        this.processedFramesQueue = processedFramesQueue;
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
            processedFrames[0] = visualMotionDetector.detect(rowFrames[0]);
            processedFrames[1] = thermalMotionDetector.detect(rowFrames[1]);
            processedFramesQueue.offer(processedFrames);
        }

    }

}
