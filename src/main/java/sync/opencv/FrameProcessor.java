package sync.opencv;

import org.opencv.core.Mat;

import java.util.Queue;

public class FrameProcessor implements Runnable {

    Queue<Mat[]> rowFramesQueue;
    Queue<Mat[]> processedFramesQueue;

    boolean stopped = false;

    public FrameProcessor(Queue<Mat[]> rowFramesQueue, Queue<Mat[]> processedFramesQueue) {
        this.rowFramesQueue = rowFramesQueue;
        this.processedFramesQueue = processedFramesQueue;
    }

    @Override
    public void run() {
        while(!stopped){
            detectFaces();
        }
    }

    public void stop(){
        stopped = true;
    }

    private void detectFaces(){

        Mat[] rowFrames = rowFramesQueue.poll();

        /*try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }*/

        if(rowFrames == null){
            try {
                Thread.sleep(10);
                return;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {

            // todo put your code here instead
            Mat[] processedFrames = new Mat[rowFrames.length];
            for (int i = 0; i < rowFrames.length; i++)
                processedFrames[i] = rowFrames[i];

            processedFramesQueue.offer(processedFrames);
        }

    }

}
