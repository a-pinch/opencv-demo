package sync.opencv.motion;

import lombok.Getter;
import lombok.Setter;
import org.opencv.core.Rect;

@Getter @Setter
public class RectTreck {

    public static final int WATCHBOX_THRESHOLD_MIN = 50;
    public static final int WATCHBOX_THRESHOLD_MAX = 250;

    private Rect rect;
    private Integer treck = 0;
    private boolean inited = false;

    public RectTreck(Rect rect) {
        this.rect = rect;
    }

    public void inc(){
        treck++;
        inited |= treck > WATCHBOX_THRESHOLD_MAX;
    }

    public void dec(){
        treck--;
        inited &= treck > WATCHBOX_THRESHOLD_MIN;
    }
}
