package sync.opencv.config;

import lombok.Getter;
import lombok.Setter;
import org.opencv.core.Rect;

@Getter @Setter
public class Watcher {

    private Rect sec;
    private Rect dsec;
    private Rect min;
    private Rect all;

}
