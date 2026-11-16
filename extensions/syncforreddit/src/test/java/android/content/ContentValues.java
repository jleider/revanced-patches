package android.content;

/** Desktop-only substitute for the Android value container. */
public final class ContentValues {
    private final java.util.Map<String, Object> values = new java.util.HashMap<>();
    public void put(String key, String value) { values.put(key, value); }
    public void put(String key, Integer value) { values.put(key, value); }
    public void remove(String key) { values.remove(key); }
    public String getAsString(String key) { Object v = values.get(key); return v == null ? null : v.toString(); }
}
