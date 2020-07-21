package sync.opencv.motion;

import lombok.Getter;
import lombok.Setter;
import org.opencv.core.Rect;

@Getter @Setter
public class RectTreck {
    Rect rect;
    Integer treck = 0;

    public RectTreck(Rect rect) {
        this.rect = rect;
    }

    public void inc(){
        treck++;
    }

    public void dec(){
        treck--;
    }
}
