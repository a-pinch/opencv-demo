package sync.opencv;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.Core;

@Slf4j
public class FpsMeter {

    private static final int    STEP              = 20;

    private long                 mFramesCouner;
    private double              mFrequency;
    private long                mprevFrameTime;
    private long                minitFrameTime;
    private boolean             mIsInitialized = false;
    private String              name = "";
    private double              fps;
    private double              fpsAvg;

    public FpsMeter() {
    }

    public FpsMeter(String name) {
        this.name = name;
    }

    public void init() {
        mFramesCouner = 0;
        mFrequency = Core.getTickFrequency();
        mprevFrameTime = Core.getTickCount();
        minitFrameTime = mprevFrameTime;
    }

    public void measure() {
        measure(null);
    }

    public void measure(String msg) {
        if (!mIsInitialized) {
            init();
            mIsInitialized = true;
        } else {
            mFramesCouner++;
            if (mFramesCouner % STEP == 0) {
                long time = Core.getTickCount();
                fps = STEP * mFrequency / (time - mprevFrameTime);
                fpsAvg = mFramesCouner * mFrequency / (time - minitFrameTime);
                mprevFrameTime = time;
                log.trace((msg==null?"": msg)+"FPS (" + name + "): " + fps);
            }
        }
    }

    public void summary(){
        double time = (Core.getTickCount() - minitFrameTime) / mFrequency;
        fpsAvg = mFramesCouner / time;
        log.debug("FPS (" + name + ") elapsed time:" + time);
        log.debug("FPS (" + name + ") average FPS:" + fpsAvg);
    }

    public double getFps(){
        return fps;
    }

    public double getFpsAvg() {
        return fpsAvg;
    }
}
