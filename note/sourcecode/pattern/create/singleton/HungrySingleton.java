public class HungrySingleton {

    private HungrySingleton() {

    }

    private static final HungrySingleton singleton = new HungrySingleton();

    public static HungrySingleton getInstance() {
        return singleton;
    }
}
