import java.util.HashMap;
import java.util.Map;

public class SingletonRegistered {
    private static Map<String, SingletonRegistered> map = new HashMap<String, SingletonRegistered>();

    static {
        SingletonRegistered singleton = new SingletonRegistered();
        map.put(singleton.getClass().getName(), singleton);
    }

    private SingletonRegistered() {

    }

    public static SingletonRegistered getInstance(String name) {
        return map.get(name);
    }
}
