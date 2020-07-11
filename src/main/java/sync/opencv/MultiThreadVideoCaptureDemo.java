package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.highgui.HighGui;
import org.opencv.imgproc.Imgproc;
import org.yaml.snakeyaml.Yaml;
import sync.opencv.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
public class MultiThreadVideoCaptureDemo {

    private static final int DEFAULT_FRAMES_DELAY = 5;

    public static void main(String[] args) {

        Queue<Mat[]> rowFramesQueue = new ConcurrentLinkedQueue<>();
        Queue<Mat[]> processedFramesQueue = new ConcurrentLinkedQueue<>();
        Config config = new Config();

        // Load the native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        Yaml yaml = new Yaml();
        try {
            try (InputStream in = MultiThreadVideoCaptureDemo.class.getClassLoader().getResourceAsStream("application.yml")) {
                config = yaml.loadAs(in, Config.class);
                log.debug(config.toString());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        FpsMeter fpsMeter = new FpsMeter("show");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(rowFramesQueue,
//                "http://93.87.72.254:8090/mjpg/video.mjpg?COUNTER#.Wt9InV_SGHE.link",
//                "http://93.87.72.254:8090/mjpg/video.mjpg?COUNTER#.Wt9InV_SGHE.link");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(rowFramesQueue, fpsMeter,
//                "https://hls-stream.fever-screener.altoros.com/14/1/video/stream.m3u8",
//                "https://hls-stream.fever-screener.altoros.com/14/2/video/stream.m3u8");

//        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(rowFramesQueue,
//                "rtsp://admin:123admin123@172.16.16.12:33380/cam/realmonitor?channel=1&subtype=0 ",
//                "rtsp://admin:123admin123@172.16.16.12:33380/cam/realmonitor?channel=2&subtype=0");

        MultiThreadVideoCapture cap = new MultiThreadVideoCapture(rowFramesQueue,
                "rtsp://admin:123admin123@82.209.244.52:33554/cam/realmonitor?channel=1&subtype=0 ",
                "rtsp://admin:123admin123@82.209.244.52:33554/cam/realmonitor?channel=2&subtype=0");

        Thread videoCaptureThread = new Thread(cap);
        videoCaptureThread.start();

        FrameProcessor frameProcessor = new FrameProcessor(rowFramesQueue, processedFramesQueue);
        Thread frameProcessorThread = new Thread(frameProcessor);
        frameProcessorThread.start();

        SynchronisationWatcher synchWatch = new SynchronisationWatcher(config.getWatcher());

        double fps = 0;
        do {
            fps = cap.getFps();//*/6;
            sleep(10);
        } while (fps == 0);

        int key = 0;
        boolean inited = false;
        Mat[] frame;
        do {
            try {
                frame = processedFramesQueue.poll();
                if (frame == null) {
                    sleep((int) Math.round(1000 / fps));
                } else {

                    fpsMeter.measure();
//                    log.trace(String.format("processedFramesQueue %d (%.2f) ", processedFramesQueue.size(), fpsMeter.getFps()));

                    int delay = synchWatch.check(frame, processedFramesQueue.size());
                    if(!inited) {
                        if (delay > 0) cap.delay(1, delay);
                        if (delay < 0) cap.delay(0, -delay);
                        if(delay != 0) inited = true;
                    }

                    Mat multiFrame = concatenate(frame, 5. / 8);
                    HighGui.imshow("Cap", multiFrame);

                    key = HighGui.waitKey((int) Math.round(700 / fps));
                    if (key == 37) {
                        cap.delay(0, DEFAULT_FRAMES_DELAY);
                    } else if (key == 39) {
                        cap.delay(1, DEFAULT_FRAMES_DELAY);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                key = 27;
            }

        } while (key != 27);

        cap.release();
        frameProcessor.stop();
        try {
            videoCaptureThread.join();
            frameProcessorThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        fpsMeter.summary();
        System.exit(0);
    }

    private static Mat concatenate(Mat[] frames, Double resize) {
        Mat dst = new Mat();
        List<Mat> resized = new ArrayList<>(frames.length);

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
