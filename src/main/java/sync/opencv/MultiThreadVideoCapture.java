package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
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
    private Thread[] threads;
    private Mat diff = new Mat();
    Rect watchBox;
    private ReentrantLock lock = new ReentrantLock();

    private static CyclicBarrier BARRIER;

    public MultiThreadVideoCapture(Rect watchBox, Queue<Mat[]> multiFramesQueue, String... videoSrc) {
        this(watchBox, multiFramesQueue, DEFAULT_QUEUE_SIZE, videoSrc);
    }

    public MultiThreadVideoCapture(Rect watchBox, Queue<Mat[]> multiFramesQueue, int queueLimit, String... videoSrc) {

        this.multiFramesQueue = multiFramesQueue;
        this.queueLimit = queueLimit;
        this.fpsMeter = new FpsMeter("combi ");
        videoCaptures = new VideoCapThread[videoSrc.length];
        delay = new HashMap<>();
        this.watchBox = watchBox;

        BARRIER = new CyclicBarrier(videoSrc.length);

        for (int i = 0; i < videoSrc.length; i++) {
            videoCaptures[i] = new VideoCapThread("cap" + i, videoSrc[i]);
        }

        threads = new Thread[videoCaptures.length];
        for (int i = 0; i < videoCaptures.length; i++) {
            threads[i] = new Thread(videoCaptures[i]);
        }
        for (Thread thread : threads) {
            thread.start();
        }
    }

    @Override
    public void run() {

        try {
            while (!stopped && !anyCaptureStopped()) {
                if (queueLimit > 0 && multiFramesQueue.size() > queueLimit)
                    multiFramesQueue.clear();//multiFramesQueue.poll();
                multiFramesQueue.offer(combine());
                fpsMeter.measure();
                sleep(MULTI_CAPTURE_THREAD_SLEEP_MS);
            }
        } catch(Exception e) {
            e.printStackTrace();
            release();
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

    public boolean isStopped() {
        return stopped;
    }

    public void delay(int captureIndex, int delayFrames) {
        delay.compute(captureIndex, (k, v) -> v == null ? delayFrames : v + delayFrames);
        log.debug("Delay frames " + delayFrames + " from " + captureIndex);
    }

    public Mat[] read() {
        return multiFramesQueue.poll();
    }

    private boolean anyCaptureStopped() {
        if (Arrays.stream(videoCaptures).anyMatch(VideoCapThread::isStopped)) {
            release();
            return true;
        }
        return false;
    }

    public void release() {
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

        Mat[] multiFrame = null;// = new Mat[videoCaptures.length];

        long t = System.currentTimeMillis();
        do {

            if (videoCaptures[0].more() && videoCaptures[1].more()) {
                Mat f1 = videoCaptures[0].peak();
                Mat f2 = videoCaptures[1].peak();
                int dist = compare(f1, f2);

                log.trace("origin dist " + dist);


                if (dist > 30 && videoCaptures[0].queue() > 1 && videoCaptures[1].queue() > 1) {

                    try {
                        lock.lock();
                        Iterator<Mat> i1 = videoCaptures[0].iterator();
                        Iterator<Mat> i2 = videoCaptures[1].iterator();
                        i1.next();
                        i2.next();
                        int i = 0;

                        int distf12 = compare(f1, i2.next());
                        int distf21 = compare(f2, i1.next());
                        i = 1;

                        log.trace(String.format("dist(%d) %d - %d", i, distf12, distf21));

                        while (distf12 > 30 && distf21 > 30 && i1.hasNext() && i2.hasNext()) {
                            distf12 = compare(f1, i2.next());
                            distf21 = compare(f2, i1.next());
                            i++;
                            log.trace(String.format("dist(%d) %d - %d", i, distf12, distf21));
                        }

                        if ((dist = Math.min(distf12, distf21)) < 30) {
                            //shift
                            log.trace(String.format("shift %d on %d", distf12 < distf21 ? 1 : 0, i));
                            if (distf12 < distf21) for (int j = 0; j < i; j++) videoCaptures[1].read();
                            else for (int j = 0; j < i; j++) videoCaptures[0].read();
                        } else {
                            log.trace("sleep");
                        }
                    } finally {
                        lock.unlock();
                    }
                }

                if (dist < 30) multiFrame = new Mat[]{videoCaptures[0].read(), videoCaptures[1].read()};
                else sleep(MULTI_CAPTURE_THREAD_SLEEP_MS);
            }
        } while (multiFrame == null);
/*
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
*/
        if (log.isDebugEnabled())
            if (++frames % LOG_STEP == 0)
                log.debug("combine("+(System.currentTimeMillis()-t)+"): " + multiFramesQueue.size() + (fpsMeter == null ? ";" : String.format("(%.2f);", fpsMeter.getFps())) +
//                        " delay: " + delay.entrySet().stream().map(Objects::toString).collect(Collectors.joining(",")) + "; " +
                        Arrays.stream(videoCaptures)
                                .map(c -> String.format("%s %d (%.2f); ", c.getName(), c.queue(), c.getFpsCur()))
                                .collect(Collectors.joining()));
        return multiFrame;
    }

    private int compare(Mat ... frames){


        Mat visual = frames[0].submat(new Rect(watchBox.x, watchBox.y, watchBox.width, watchBox.height));
        Mat thermal = frames[1].submat(new Rect(watchBox.x, watchBox.y, watchBox.width, watchBox.height));

        Imgproc.cvtColor(visual, visual, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(thermal, thermal, Imgproc.COLOR_BGR2GRAY);

        Core.absdiff(visual, thermal, diff);

        Imgproc.threshold(diff,diff,100,255,Imgproc.THRESH_BINARY);

        int distAll = Core.countNonZero(diff);
        return distAll;

    }

    public double getFps() {
        return videoCaptures[0].getFps();
    }

    private void sleep(long millis) {
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
            log.trace(name + " opening ...");
            cap.open(link);
            log.trace(name + " opened");

            if (!cap.isOpened()) {
                log.error("--(!)Error opening video capture " + name);
                stopped = true;
                return;
            }
            fps = 25;//cap.get(Videoio.CAP_PROP_FPS);
            log.debug("Started video capture " + name + " with property FPS  " + fps);
            threadSleep = Math.round(1000 / fps) - 4;
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

                lock.lock();
                frames.offer(frame);
                lock.unlock();
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

        public Mat peak() {
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

        public boolean more() {
            return frames.size() > 0;
        }

        public Iterator<Mat> iterator() {
            return frames.iterator();
        }

        public String getName() {
            return name;
        }

        public double getFpsCur() {
            return fpsMeter.getFps();
        }

        public double getFpsAvg() {
            return fpsMeter.getFpsAvg();
        }

        public double getFps() {
            return fps;
        }
    }

}
