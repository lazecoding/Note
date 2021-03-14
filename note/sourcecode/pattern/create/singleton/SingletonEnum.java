public class SingletonEnum {
    private SingletonEnum() {

    }

    static enum Enum {
        INSTANCE;
        private SingletonEnum singleton;

        private Enum() {
            singleton = new SingletonEnum();
        }

        public SingletonEnum getInstnce() {
            return singleton;
        }
    }

    public static SingletonEnum getInstance() {
        return Enum.INSTANCE.getInstnce();
    }
}
