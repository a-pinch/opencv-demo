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
    private static final int LOG_STEP = 20;

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
        } catch (Exception e) {
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

        Mat[] multiFrame = null;

        long t = System.currentTimeMillis();
        int i0 = 0; int i1 = 0;
        Mat f0 = null; Mat f1= null;
        int distortion = 0;
        do {

            if (videoCaptures[0].more() && videoCaptures[1].more()) {
                if(f0==null || f1==null) {
                    f0 = videoCaptures[0].peak();
                    f1 = videoCaptures[1].peak();
                    distortion = compare(f0, f1);
                    if(log.isDebugEnabled() && distortion > 30) {
                        log.debug("origin distortion " + distortion);
                    }else{
                        log.trace("origin distortion " + distortion);
                    }
                }

                if (distortion > 30) {
                    if((i0=shift(f0, videoCaptures[1], 1, i0)) < 0 || (i1=shift(f1, videoCaptures[0], 0, i1)) < 0)
                        multiFrame = new Mat[]{videoCaptures[0].read(), videoCaptures[1].read()};
                } else {
                    multiFrame = new Mat[]{videoCaptures[0].read(), videoCaptures[1].read()};
                }
            }

            if (multiFrame == null) sleep(MULTI_CAPTURE_THREAD_SLEEP_MS);
        } while (multiFrame == null);

        if (log.isDebugEnabled())
            if (++frames % LOG_STEP == 0)
                log.debug("combine(" + (System.currentTimeMillis() - t) + "): " + multiFramesQueue.size() + (fpsMeter == null ? ";" : String.format("(%.2f);", fpsMeter.getFps())) +
                        Arrays.stream(videoCaptures)
                                .map(c -> String.format("%s %d (%.2f); ", c.getName(), c.queue(), c.getFpsCur()))
                                .collect(Collectors.joining()));

        return multiFrame;
    }

    private int compare(Mat... frames) {


        Mat visual = frames[0].submat(new Rect(watchBox.x, watchBox.y, watchBox.width, watchBox.height));
        Mat thermal = frames[1].submat(new Rect(watchBox.x, watchBox.y, watchBox.width, watchBox.height));

        Imgproc.cvtColor(visual, visual, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(thermal, thermal, Imgproc.COLOR_BGR2GRAY);

        Core.absdiff(visual, thermal, diff);

        Imgproc.threshold(diff, diff, 100, 255, Imgproc.THRESH_BINARY);

        int distAll = Core.countNonZero(diff);
        return distAll;

    }

    private int shift(Mat templateFrame, VideoCapThread cap, int c, int i) {
        if (cap.queue() > 1) {
                Iterator<Mat> it = cap.iterator();
                it.next();

                int distf = 100;
                if(i>0){
                    for(int j=0;j<i;j++) it.next();
                } else {
                    distf = compare(templateFrame, it.next());
                    i = 1;
                }
                log.trace(String.format("dist(%d) %d/%d - %d", i, c == 0 ? 1 : 0, c, distf));

                while (distf > 30 && it.hasNext()) {
                    distf = compare(templateFrame, it.next());
                    i++;
                    log.trace(String.format("dist(%d) %d/%d - %d", i, c == 0 ? 1 : 0, c, distf));
                }

                if (distf < 30) {
                    //shift
                    log.debug(String.format("shift %d on %d", c, i));
                    for (int j = 0; j < i; j++) cap.read();
                    return -1;
                }
        }
        return i;
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

        private static final int THREAD_SLEEP_MS = 10;  // default thread delay

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
