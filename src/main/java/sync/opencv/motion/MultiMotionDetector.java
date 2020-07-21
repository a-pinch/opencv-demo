package sync.opencv.motion;

import lombok.extern.slf4j.Slf4j;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static org.opencv.imgproc.Imgproc.COLOR_BGR2GRAY;

@Slf4j
@Component
public class MultiMotionDetector implements MotionDetector {

    private Mat bg;
    private double accumWeight;
    private int frameCount;
    private int tVal = 25;
    private int totalFrames = 0;
    private List<RectTreck> trackedRects = new ArrayList<>();
    RectTreck watchBox = new RectTreck(new Rect());

    public MultiMotionDetector() {
    }

    public MultiMotionDetector(double accumWeight, int frameCount, int threshold) {
        this.accumWeight = accumWeight;
        this.frameCount = frameCount;
        this.tVal = threshold;
    }

    private void update(Mat image){
        if(bg == null){
            bg = new Mat();
            image.copyTo(bg);
            bg.convertTo(bg, CvType.CV_32F);
            return;
        }
        Imgproc.accumulateWeighted(image, bg, accumWeight);
    }

    public RectTreck detect(Mat image){
        log.trace("detect: " + totalFrames +"; " +frameCount);

        Mat gray = new Mat();
        Imgproc.cvtColor(image, gray, COLOR_BGR2GRAY);
        Imgproc.GaussianBlur(gray, gray, new Size(7, 7), 0);

        if(totalFrames > frameCount) {

            Mat delta = new Mat();
            Mat bgUint8 = new Mat();
            bg.convertTo(bgUint8, CvType.CV_8U);
            Core.absdiff(bgUint8, gray, delta);

            Imgproc.threshold(delta, delta, tVal, 255, Imgproc.THRESH_BINARY);

//            Imgproc.erode(delta, delta, new Mat(), new Point(-1, -1), 1);
            Imgproc.dilate(delta, delta, new Mat(), new Point(-1, -1), 2);

            Mat hierarchy = new Mat();
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(delta, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            Set<Rect> processed = new HashSet<>();
            StringBuilder sb = new StringBuilder("find: ");

            contours.forEach(m -> {
                Rect r = Imgproc.boundingRect(m);
                Rect cr = findClosest(r, trackedRects.stream().map(RectTreck::getRect).collect(Collectors.toList()));

                if (cr == null) {
                    trackedRects.add(new RectTreck(r));
                    processed.add(r);
                }else {
                    //check rects intersection
                    Rect[] rcts = {cr, r};
                    sort(rcts);
                    if (overlap(rcts)) {
                        cr.set(new double[]{rcts[0].x, rcts[0].y, rcts[0].width, rcts[0].height});
                        processed.add(cr);
                    } else {
                        trackedRects.add(new RectTreck(r));
                        processed.add(r);
                    }
                }

                sb.append(r.toString()).append("; ");
            });
            log.debug(sb.toString());

            Iterator<RectTreck> i = trackedRects.iterator();
            while (i.hasNext()) {
                RectTreck r = i.next();
                if (processed.contains(r.getRect())){
                    r.inc();
                }
                else {
                    if (r.getTreck() <= 1) i.remove();
                    else r.dec();
                }
            }

            StringBuilder sb2 = new StringBuilder("tracked: ");
            for (RectTreck r : trackedRects) {
                if(r.getTreck()>watchBox.getTreck()) watchBox = r;
                sb2.append(r.getRect()).append("(").append(r.getTreck()).append("); ");
            }
            log.debug(sb2.toString());

            hierarchy.release();
            delta.release();
            bgUint8.release();
        }

        update(gray);
        totalFrames++;
        gray.release();

        return watchBox;
    }

    private Rect findClosest(Rect r, Collection<Rect> rects){

        Rect cr = null;
        double dst = -1;
        for(Rect rect:rects){
            double d = dist(r, rect);
            if(dst < 0 || d < dst){
                dst = d;
                cr = rect;
            }
        }
        return cr;

    }

    private double dist(Rect a, Rect b){
        double ax = a.x + a.width/2.;
        double ay = a.y + a.height/2.;
        double bx = b.x + b.width/2.;
        double by = b.y + b.height/2.;

        return Math.sqrt((by-ay)*(by-ay) + (bx-ax)*(bx-ax));
    }

    private boolean overlap(Rect ... r){
        if(r[1].x > r[0].x + r[0].width || r[0].x > r[1].x + 2 * r[1].width)
            return false;

        if(r[1].y > r[0].y + r[0].height || r[0].y > r[1].y + 2 * r[1].height)
            return false;

        return true;
    }

    private void sort(Rect ... r){
        boolean s;
        Rect t;
        do {
            s = true;
            for (int i = 1; i < r.length; i++) {
                if(r[i].width*r[i].height > r[i-1].width*r[i-1].height){
                    t=r[i-1];
                    r[i-1]=r[i];
                    r[i]=t;
                    s = false;
                }
            }
        }while (!s);
    }
}
