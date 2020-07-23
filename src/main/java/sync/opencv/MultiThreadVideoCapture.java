package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;
import sync.opencv.motion.RectTreck;

import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Slf4j
public class MultiThreadVideoCapture implements Runnable {

    private static final int DEFAULT_QUEUE_SIZE = 64;
    private static final int SYNCHRONIZATION_SEARCH_DEPTH = 127;
    private static final int MULTI_CAPTURE_THREAD_SLEEP_MS = 10;  // default thread delay
    private static final int DISTORTION_THRESHOLD = 15;
    private static final int LOG_STEP = 20;

    private volatile Queue<Mat[]> multiFramesQueue;
    private int queueLimit;
    private VideoCapThread[] videoCaptures;
    private Map<Integer, Integer> delay;
    private FpsMeter fpsMeter;
    private int frames = 0;
    private boolean stopped = false;
    private Thread[] threads;
    private RectTreck[]  watchBox;
    private static Mat[] fragment = new Mat[20];

    private static ReentrantLock[] lock = new ReentrantLock[]{new ReentrantLock(),new ReentrantLock(),new ReentrantLock(),
            new ReentrantLock(),new ReentrantLock(),new ReentrantLock(),new ReentrantLock(),new ReentrantLock()
            ,new ReentrantLock(),new ReentrantLock()};

    private static CyclicBarrier BARRIER;

    public MultiThreadVideoCapture(RectTreck[] watchBox, Queue<Mat[]> multiFramesQueue, String... videoSrc) {
        this(watchBox, multiFramesQueue, DEFAULT_QUEUE_SIZE, videoSrc);
    }

    public MultiThreadVideoCapture(RectTreck[]  watchBox, Queue<Mat[]> multiFramesQueue, int queueLimit, String... videoSrc) {

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

        while (!stopped && multiFrame == null) {

            if (videoCaptures[0].more() && videoCaptures[1].more()) {

                if(watchBox[0] == null || !watchBox[0].isInited() || watchBox[1] == null || !watchBox[1].isInited() ||
                        i0 > SYNCHRONIZATION_SEARCH_DEPTH || i1 > SYNCHRONIZATION_SEARCH_DEPTH){
                    multiFrame = new Mat[]{videoCaptures[0].read(), videoCaptures[1].read()};
                    log.trace("combine asynchronous frames");
                    break;
                }

                if (f0 != videoCaptures[0].peak() || f1 != videoCaptures[1].peak()) {
                    f0 = videoCaptures[0].peak();
                    f1 = videoCaptures[1].peak();
                    i0 = 0; i1 = 0;
                    distortion = compare(f0, f1);
                    if (log.isDebugEnabled() && distortion > DISTORTION_THRESHOLD) {
                        log.debug("origin distortion " + distortion);
                    } else {
                        log.trace(String.format("origin distortion %d", distortion));
                    }
                }

                if (distortion > DISTORTION_THRESHOLD) {
                    if ((i0 = shift(f0, videoCaptures[1], 1, i0)) < 0 || (i1 = shift(f1, videoCaptures[0], 0, i1)) < 0)
                        multiFrame = new Mat[]{videoCaptures[0].read(), videoCaptures[1].read()};
                } else {
                    multiFrame = new Mat[]{videoCaptures[0].read(), videoCaptures[1].read()};
                }

            }

            if (!videoCaptures[0].more() || !videoCaptures[1].more()) sleep(MULTI_CAPTURE_THREAD_SLEEP_MS);

        }

        if (log.isDebugEnabled())
            if (++frames % LOG_STEP == 0)
                log.debug("combine(" + (System.currentTimeMillis() - t) + "): " + multiFramesQueue.size() + (fpsMeter == null ? ";" : String.format("(%.2f);", fpsMeter.getFps())) +
                        Arrays.stream(videoCaptures)
                                .map(c -> String.format("%s %d (%.2f); ", c.getName(), c.queue(), c.getFpsCur()))
                                .collect(Collectors.joining()));

        return multiFrame;
    }

    private int compare(Mat... frames) {

        if(!watchBox[0].isInited() || !watchBox[1].isInited()) return -1;

        Mat visual = frames[0].submat(new Rect(watchBox[0].getRect().x, watchBox[0].getRect().y, watchBox[0].getRect().width, watchBox[0].getRect().height));
        Mat thermal = frames[1].submat(new Rect(watchBox[1].getRect().x, watchBox[1].getRect().y, watchBox[1].getRect().width, watchBox[1].getRect().height));

        if(visual.empty() || thermal.empty()) return -1;

        lock[0].lock();
        fragment[0] = visual.clone();
        lock[0].unlock();
        lock[1].lock();
        fragment[1] = thermal.clone();
        lock[1].unlock();
//
//        lock[2].lock();
//        fragment[2] = visual.clone();
//        lock[2].unlock();
//        lock[3].lock();
//        fragment[3] = thermal.clone();
//        lock[3].unlock();

        Imgproc.cvtColor(visual, visual, Imgproc.COLOR_BGR2GRAY);
        Imgproc.cvtColor(thermal, thermal, Imgproc.COLOR_BGR2GRAY);

//        lock[4].lock();
//        fragment[4] = visual.clone();
//        lock[4].unlock();
//        lock[5].lock();
//        fragment[5] = thermal.clone();
//        lock[5].unlock();

        Imgproc.threshold(visual, visual, 150, 255, Imgproc.THRESH_BINARY);
        Imgproc.threshold(thermal, thermal, 150, 255, Imgproc.THRESH_BINARY);

//        lock[6].lock();
//        fragment[6] = visual.clone();
//        lock[6].unlock();
//        lock[7].lock();
//        fragment[7] = thermal.clone();
//        lock[7].unlock();


    /*    if(visual.size().area() < thermal.size().area())
            Imgproc.resize(visual, visual, thermal.size());
        else
            Imgproc.resize(thermal, thermal, visual.size());

        Core.absdiff(visual, thermal, diff);*/
        int distAll = diff(visual, thermal);

//        lock[8].lock();
//        fragment[8] = diff.clone();
//        lock[8].unlock();

//        int distAll = Core.countNonZero(diff);
        return distAll;

    }

    private int diff(Mat src1, Mat src2){
        Mat diff = new Mat();
        int d, minD=-1;

        for(int w=0; w<Math.abs(src1.width()-src2.width()); w++){
            for(int h=0; h<Math.abs(src1.height()-src2.height()); h++){
                Mat s1 = src1.submat(new Rect(src1.width()>src2.width()?w:0,
                                              src1.height()>src2.height()?h:0,
                                              Math.min(src1.width(), src2.width()),
                                              Math.min(src1.height(), src2.height())));
                Mat s2 = src2.submat(new Rect(src2.width()>src1.width()?w:0,
                                              src2.height()>src1.height()?h:0,
                                              Math.min(src1.width(), src2.width()),
                                              Math.min(src1.height(), src2.height())));
                Core.absdiff(s1, s2, diff);
                d = Core.countNonZero(diff);
                if(minD < 0 || d<minD){
                    minD = d;
                }
            }
        }
        return minD;
    }

 /*   private int diff(Mat src1, Mat src2, Mat minDst){
        Mat src, tmp;
        Mat diff = new Mat();
        int d, minD=-1;
        if(src1.width()>=src2.width()&&src1.height()>=src2.height()){
            src = src1.clone(); tmp = src2.clone();
        } else if(src1.width()<=src2.width()&&src1.height()<=src2.height()){
            src = src2.clone(); tmp = src1.clone();
        }else{
            return -1;
        }
        for(int i=0; i<src.width()-tmp.width(); i++){
            for(int j=0; j<src.height()-tmp.height(); j++){
                Mat s = src.submat(new Rect(i,j,tmp.width(),tmp.height()));
                Core.absdiff(s, tmp, diff);
                d = Core.countNonZero(diff);
                if(minD < 0 || d<minD){
                    minD = d;
                    diff.copyTo(minDst);
                }
            }
        }
        return minD;
    }*/

    public static byte[] getFragment(int n){
        if(fragment[n]!=null ) {

            MatOfByte buf = new MatOfByte();
            Imgproc.resize(fragment[n], fragment[n], new Size(fragment[n].width()*2, fragment[n].height()*2));
            if (Imgcodecs.imencode(".jpg", fragment[n], buf)) {
                lock[n].lock();
                fragment[n] = null;
                lock[n].unlock();
                return buf.toArray();
            }
        }else{
            sleep(10);
        }
        return null;
    }


    private int shift(Mat templateFrame, VideoCapThread cap, int c, int i) {
        if (cap.queue() > 1) {
            Iterator<Mat> it = cap.iterator();
            it.next();

            int distf = 1000;
            if (i > 0) {
                for (int j = 0; j < i; j++) it.next();
            } else {
                distf = compare(templateFrame, it.next());
                i = 1;
            }
            log.trace(String.format("dist(%d) %d/%d - %d", i, c == 0 ? 1 : 0, c, distf));

            if (distf > DISTORTION_THRESHOLD && it.hasNext()) {
                distf = compare(templateFrame, it.next());
                i++;
                log.trace(String.format("dist(%d) %d/%d - %d", i, c == 0 ? 1 : 0, c, distf));
            }

            if (distf < DISTORTION_THRESHOLD) {
                //shift
                if(i<3){
                    log.trace(String.format("skip shift %d on %d", c, i));
                }else {
                    log.debug(String.format("shift %d on %d", c, i));
                    for (int j = 0; j < i; j++) cap.read();
                }
                return -1;
            }
        }
        return i;
    }

    public double getFps() {
        return videoCaptures[0].getFps();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException();
        }
    }

    private class VideoCapThread implements Runnable {

        private static final int THREAD_SLEEP_MS = 10;  // default thread delay
        private static final int MX_QUEUE_SIZE = 128;

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
                if(frames.size()>MX_QUEUE_SIZE) frames.poll();
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
