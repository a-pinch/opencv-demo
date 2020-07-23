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

    private final int LOG_STEP = 20;

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

            Map<Rect, List<Rect>> processed = new HashMap<>();
            StringBuilder sb = new StringBuilder("find: ");

            contours.forEach(m -> {
                Rect r = Imgproc.boundingRect(m);
                Rect cr = findClosest(r, trackedRects.stream().map(RectTreck::getRect).collect(Collectors.toList()));

                if (cr == null) {
                    trackedRects.add(new RectTreck(r));
                    processed.put(r, null);
                }else {
                    //check rects intersection
                    if (overlap(cr, r)) {
//                        cr.set(new double[]{rcts[0].x, rcts[0].y, rcts[0].width, rcts[0].height});
                        List<Rect> mappedRects = processed.get(cr);
                        if(mappedRects == null) mappedRects = new ArrayList<>();
                        mappedRects.add(r);
                        processed.put(cr, mappedRects);
                    } else {
                        trackedRects.add(new RectTreck(r));
                        processed.put(r,  null);
                    }
                }

                sb.append(r.toString()).append("; ");
            });
            log.trace(sb.toString());

            boundToTrackedRects(processed, trackedRects);
            reduceTrackedRects(trackedRects);

            StringBuilder sb2 = new StringBuilder("tracked: ");
            for (RectTreck r : trackedRects) {
//                Imgproc.rectangle(image, r.getRect(), new Scalar(0, 0, 255), 2);
                if(r.getTreck()>watchBox.getTreck()) watchBox = r;
                sb2.append(r.getRect()).append("(").append(r.getTreck()).append("); ");
            }
            if(totalFrames % LOG_STEP == 0) log.debug(sb2.toString());

            hierarchy.release();
            delta.release();
            bgUint8.release();
        }

        update(gray);
        totalFrames++;
        gray.release();

        return watchBox;
    }

    private void boundToTrackedRects(Map<Rect, List<Rect>> processed, List<RectTreck> trackedRects){
        Iterator<RectTreck> i = trackedRects.iterator();
        while (i.hasNext()) {
            RectTreck r = i.next();
            if (processed.containsKey(r.getRect())){
                if(processed.get(r.getRect()) != null) {
                    Rect boundedRect = getBoundRect(processed.get(r.getRect()));
                    Rect avgRect = getAvgRect(r.getRect(), boundedRect);
                    r.setRect(avgRect);
                }
                r.inc();
            }
            else {
                if (r.getTreck() <= 1) i.remove();
                else r.dec();
            }
        }
    }

    private void reduceTrackedRects(List<RectTreck> trackedRects){
        boolean b;
        do{
            Iterator<RectTreck> i = trackedRects.iterator();
            if(!i.hasNext()) break;
            RectTreck prev = i.next();
            b = false;
            while (i.hasNext()){
                RectTreck cur = i.next();
                if(overlap(prev.getRect(), cur.getRect())){
                    prev.setRect(getBoundRect(Arrays.asList(prev.getRect(), cur.getRect())));
                    if(prev.getTreck()<cur.getTreck()) prev.setTreck(cur.getTreck());
                    i.remove();
                    b = true;
                }else{
                    prev = cur;
                }

            }
        }while (b);
    }

    private Rect getBoundRect(List<Rect> rects){
        int[] p = {-1, -1, -1, -1};
        rects.forEach(r -> {
            p[0] = p[0] == -1 ? r.x : Math.min(p[0], r.x);
            p[1] = p[1] == -1 ? r.y : Math.min(p[1], r.y);
            p[2] = Math.max(p[2], r.x+r.width);
            p[3] = Math.max(p[3], r.y+r.height);
        });
        return new Rect(p[0],p[1], p[2] - p[0], p[3] - p[1]);
    }

    private Rect getAvgRect(Rect r1, Rect r2){
        if(r1.width*r1.height<r2.width*r2.height) return getBoundRect(Arrays.asList(r1, r2));
        double k = 0.03;
        int x1 = r1.x +(int)Math.round((r2.x-r1.x)*k);
        int y1 = r1.y + (int)Math.round((r2.y-r1.y)*k);
        int x2 = r1.x + r1.width + (int)Math.round((r2.x+r2.width-(r1.x+r1.width))*k);
        int y2 = r1.y + r1.height + (int)Math.round((r2.y+r2.height-(r1.y+r1.height))*k);
        return new Rect(x1, y1, x2-x1, y2-y1);
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
        sort(r);
        if(r[1].x - r[1].width > r[0].x + r[0].width || r[0].x > r[1].x + 2 * r[1].width)
            return false;

        if(r[1].y - r[1].height > r[0].y + r[0].height || r[0].y > r[1].y + 2 * r[1].height)
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
