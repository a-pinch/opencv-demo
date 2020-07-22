package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.yaml.snakeyaml.Yaml;
import sync.opencv.config.Config;
import sync.opencv.motion.RectTreck;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@ComponentScan
@EnableAutoConfiguration
public class MultiThreadVideoCaptureDemo {

    private static final int DEFAULT_FRAMES_DELAY = 5;

    private static Queue<Mat[]> rowFramesQueue = new ConcurrentLinkedQueue<>();
    private static Queue<Mat[]> processedFramesQueue = new ConcurrentLinkedQueue<>();
    private static RectTreck[] watchBoxes = new RectTreck[2];
    private static boolean watchBox = true;
//    private static Config config = new Config();

    public static int getQueueSize(){
        return processedFramesQueue.size();
    }


    public static boolean toggleWatchBox(){
        watchBox = !watchBox;
        return watchBox;
    }

    public static void main(String[] args) {

        SpringApplication.run(MultiThreadVideoCaptureDemo.class, args);


        // Load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
/*
        Yaml yaml = new Yaml();
        try {
            try (InputStream in = MultiThreadVideoCaptureDemo.class.getClassLoader().getResourceAsStream("application.yml")) {
                config = yaml.loadAs(in, Config.class);
                log.debug(config.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        FpsMeter fpsMeter = new FpsMeter("show");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(null, rowFramesQueue,
//                "http://93.87.72.254:8090/mjpg/video.mjpg?COUNTER#.Wt9InV_SGHE.link",
//                "http://93.87.72.254:8090/mjpg/video.mjpg?COUNTER#.Wt9InV_SGHE.link");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(rowFramesQueue, fpsMeter,
//                "https://hls-stream.fever-screener.altoros.com/14/1/video/stream.m3u8",
//                "https://hls-stream.fever-screener.altoros.com/14/2/video/stream.m3u8");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(config.getWatchBox(), rowFramesQueue, processedFramesQueue,
//                "rtsp://admin:123admin123@172.16.16.12:33380/cam/realmonitor?channel=1&subtype=0 ",
//                "rtsp://admin:123admin123@172.16.16.12:33380/cam/realmonitor?channel=2&subtype=0");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(watchBoxes, rowFramesQueue,
//                "rtsp://admin:Altoros2020FS@82.209.244.52:33380/cam/realmonitor?channel=1&subtype=0 ",
//                "rtsp://admin:Altoros2020FS@82.209.244.52:33380/cam/realmonitor?channel=2&subtype=0");
//
        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(watchBoxes, rowFramesQueue,
                "/Users/x_pinchan1/projects/test/facedetection/opencvJava/doc/visual.mov",
                "/Users/x_pinchan1/projects/test/facedetection/opencvJava/doc/thermal.mov");

        Thread videoCaptureThread = new Thread(cap);
        videoCaptureThread.start();

        FrameProcessor frameProcessor = new FrameProcessor(rowFramesQueue, processedFramesQueue, watchBoxes);
        Thread frameProcessorThread = new Thread(frameProcessor);
        frameProcessorThread.start();
/*
        double fps = 0;
        do {
            fps = cap.getFps();//6;
            sleep(10);
        } while (fps == 0);

        int key = 0;
        Mat[] frame;
        boolean watchBox = false;
        do {
            try {
                frame = processedFramesQueue.poll();
                if (frame == null) {
                    sleep((int) Math.round(1000 / fps));
                } else {

                    fpsMeter.measure();
                    log.trace(String.format("processedFramesQueue %d (%.2f) ", processedFramesQueue.size(), fpsMeter.getFps()));

                    Mat multiFrame = concatenate(frame, 5. / 8, watchBox ? config.getWatchBox() : null);
                    HighGui.imshow("Cap", multiFrame);

                    key = HighGui.waitKey((int) Math.round(700 / fps));
                    if (key == 66) {
                        watchBox = !watchBox;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                key = 27;
            }

        } while (!cap.isStopped() && key != 27);
*/

       while (!frameProcessor.isStopped() && !cap.isStopped()){
           sleep(100);
       }

        cap.release();
        log.debug("captures released");
        frameProcessor.stop();
        log.debug("frame processor stopped");

        try {
            frameProcessorThread.join();
            videoCaptureThread.join();

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        fpsMeter.summary();
        System.exit(0);
    }

    public static byte[] get(){
        Mat[] frames = processedFramesQueue.poll();
        if(frames != null) {
            Mat frame = concatenate(frames, 1., watchBox ? watchBoxes : null);
            MatOfByte buf = new MatOfByte();
            if (Imgcodecs.imencode(".jpg", frame, buf)) {
                return buf.toArray();
            }
        }
        return null;
    }

    private static Mat concatenate(Mat[] frames, Double resize, RectTreck[] watchBox) {
        Mat dst = new Mat();
        List<Mat> resized = new ArrayList<>(frames.length);

        if(watchBox != null){
            if(watchBox[0].getTreck()>250)
                Imgproc.rectangle(frames[0], watchBox[0].getRect(), new Scalar(0, 255, 0)); //green
            else if(watchBox[0].getTreck()>100)
                Imgproc.rectangle(frames[0], watchBox[0].getRect(), new Scalar(0, 255, 255)); //yellow
            if(watchBox[1].getTreck()>250)
                Imgproc.rectangle(frames[1], watchBox[1].getRect(), new Scalar(0, 255, 0)); //green
            else if(watchBox[1].getTreck()>100)
                Imgproc.rectangle(frames[1], watchBox[1].getRect(), new Scalar(0, 255, 255)); //yellow
//            Imgproc.rectangle(frames[1], watchBox, new Scalar(255, 0, 0)); //blue
        }

        if (resize != null) {
            Size sz = new Size(frames[0].width() * resize, frames[0].height() * resize);
            for (int i = 0; i < frames.length; i++) {
                resized.add(new Mat());
                Imgproc.resize(frames[i], resized.get(i), sz);
            }
        }

        Core.hconcat(resized, dst);

        return dst;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
