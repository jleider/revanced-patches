package android.os;

/** JVM stand-in: android.jar's Build fields are not initialised off-device. */
public class Build {
    public static class VERSION {
        public static final String RELEASE = "14";
        public static final int SDK_INT = 34;
    }
}
