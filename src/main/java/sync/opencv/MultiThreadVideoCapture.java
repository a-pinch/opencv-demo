package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Mat;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.stream.Collectors;

@Slf4j
public class MultiThreadVideoCapture implements Runnable {

    private static final int DEFAULT_QUEUE_SIZE = 64;
    private static final int MULTI_CAPTURE_THREAD_SLEEP_MS = 10;  // default thread delay
    private static final int LOG_STEP = 1;

    private volatile Queue<Mat[]> multiFramesQueue;
    private int queueLimit;
    private VideoCapThread[] videoCaptures;
    private Map<Integer, Integer> delay;
    private FpsMeter fpsMeter;
    private int frames = 0;
    private boolean stopped = false;
    Thread[] threads;

    private static CyclicBarrier BARRIER;

    public MultiThreadVideoCapture(Queue<Mat[]> multiFramesQueue, String ... videoSrc) {
        this(multiFramesQueue, DEFAULT_QUEUE_SIZE, videoSrc);
    }

    public MultiThreadVideoCapture(Queue<Mat[]> multiFramesQueue, int queueLimit, String ... videoSrc) {

        this.multiFramesQueue = multiFramesQueue;
        this.queueLimit =queueLimit;
        this.fpsMeter = new FpsMeter("combi ");
        videoCaptures = new VideoCapThread[videoSrc.length];
        delay = new HashMap<>();

        BARRIER = new CyclicBarrier(videoSrc.length);

        for(int i=0; i<videoSrc.length; i++){
            videoCaptures[i] = new VideoCapThread("cap"+i, videoSrc[i]);
        }

        threads = new Thread[videoCaptures.length];
        for(int i=0; i<videoCaptures.length; i++){
            threads[i] = new Thread(videoCaptures[i]);
        }
        for (Thread thread : threads) {
            thread.start();
        }
    }

    @Override
    public void run() {

        while (!stopped && !anyCaptureStopped()) {
            if (queueLimit > 0 && multiFramesQueue.size() > queueLimit) multiFramesQueue.clear();//multiFramesQueue.poll();
            multiFramesQueue.offer(combine());
            fpsMeter.measure();
            sleep(MULTI_CAPTURE_THREAD_SLEEP_MS);
        }

        log.debug("exit from MultiThreadVideoCapture");
        fpsMeter.summary();
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void delay(int captureIndex, int delayFrames){
        delay.compute(captureIndex, (k, v)->v==null?delayFrames: v + delayFrames);
        log.debug("Delay frames " + delayFrames + " from " + captureIndex);
    }

    public Mat[] read(){
        return multiFramesQueue.poll();
    }

    private boolean anyCaptureStopped() {
        if(Arrays.stream(videoCaptures).anyMatch(VideoCapThread::isStopped)){
            release();
            return true;
        }
        return false;
    }

    public void release(){
        Arrays.stream(videoCaptures).forEach(VideoCapThread::stop);
        stopped = true;
    }

    private Mat[] combine() {

//        if (log.isTraceEnabled())
//            log.trace("before combine: " + multiFramesQueue.size() + (fpsMeter == null ? ";" : String.format("(%.2f);", fpsMeter.getFps())) +
//                    " delay: " + delay.entrySet().stream().map(Objects::toString).collect(Collectors.joining(",")) + "; " +
//                    Arrays.stream(videoCaptures)
//                            .map(c -> String.format("%s %d (%.2f); ", c.getName(), c.queue(), c.getFpsCur()))
//                            .collect(Collectors.joining()));

        Mat[] multiFrame = new Mat[videoCaptures.length];
        boolean allCombined;
        do{
            allCombined = true;
            for(int i=0; i< videoCaptures.length; i++){
                if(multiFrame[i] == null){
                    if(delay.containsKey(i)) {
                        multiFrame[i] = videoCaptures[i].peak();
                        if(multiFrame[i] != null){
                            if(delay.get(i) > 0)
                                delay.put(i, delay.get(i) - 1);
                            else
                                delay.remove(i);
                        }
                    } else{
                        multiFrame[i] = videoCaptures[i].read();
                    }
                    allCombined = allCombined && multiFrame[i] != null;
                }
            }
            if(!allCombined) sleep(MULTI_CAPTURE_THREAD_SLEEP_MS);
        }while (!stopped && !allCombined);

        if (log.isDebugEnabled())
            if(++frames % LOG_STEP == 0)
                log.debug("combine: " + multiFramesQueue.size() + (fpsMeter == null ? ";" : String.format("(%.2f);", fpsMeter.getFps())) +
                        " delay: " + delay.entrySet().stream().map(Objects::toString).collect(Collectors.joining(",")) + "; " +
                        Arrays.stream(videoCaptures)
                                .map(c -> String.format("%s %d (%.2f); ", c.getName(), c.queue(), c.getFpsCur()))
                                .collect(Collectors.joining()));
        return multiFrame;
    }

    public double getFps() {
        return videoCaptures[0].getFps();
    }

    private void sleep(long millis){
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private class VideoCapThread implements Runnable {

        private static final int THREAD_SLEEP_MS = 36;  // default thread delay

        private volatile Queue<Mat> frames;
        private VideoCapture cap;
        private boolean stopped = false;
        private String name;
        private String link;
        private FpsMeter fpsMeter;
        private double fps = 0;
        private long threadSleep = THREAD_SLEEP_MS;

        public VideoCapThread(String name, String link) {
            this.link = link;
            this.frames = new ConcurrentLinkedQueue<>();
            this.name = name;
            fpsMeter = new FpsMeter(name);
            cap = new VideoCapture();
        }

        private void init() {
            log.trace(name+" opening ...");
            cap.open(link);
            log.trace(name+" opened");

            if (!cap.isOpened()) {
                log.error("--(!)Error opening video capture " + name);
                stopped = true;
                return;
            }
            fps = 25;//cap.get(Videoio.CAP_PROP_FPS);
            log.debug("Started video capture " + name + " with property FPS  " + fps);
            threadSleep = Math.round(1000/fps) - 4;
        }

        @Override
        public void run() {

            init();
            barrier();
            double pos;

            while (!stopped) {
                pos = cap.get(Videoio.CAP_PROP_POS_FRAMES);
                Mat frame = new Mat();

                if (!cap.read(frame) || frame.empty()) {
                    stopped = true;
                    break;
                }

                frames.offer(frame);
                fpsMeter.measure();
                log.trace(String.format("%s %d(%.0f)", name, frames.size(), pos));
                sleep(threadSleep);
            }

            cap.release();
            fpsMeter.summary();

        }

        private void barrier() {
            try {
                BARRIER.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                e.printStackTrace();
            }
        }

        public Mat read() {
            return frames.poll();
        }

        public Mat peak(){
            return frames.peek();
        }

        public void stop() {
            stopped = true;
        }

        public boolean isStopped() {
            return stopped;
        }

        public int queue() {
            return frames.size();
        }

        public boolean more(){
            return frames.size() > 0;
        }

        public String getName() {
            return name;
        }

        public double getFpsCur(){
            return fpsMeter.getFps();
        }

        public double getFpsAvg(){
            return fpsMeter.getFpsAvg();
        }

        public double getFps() {
            return fps;
        }
    }

}
