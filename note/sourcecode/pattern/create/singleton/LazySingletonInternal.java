public class LazySingletonInternal {
    private static class lazyHolder {
        /**
         *  静态内部类创建实例
         */
        private static final LazySingletonInternal singleton =
                new LazySingletonInternal();
    }

    private LazySingletonInternal() {

    }

    /**
     * 获取实例，也叫静态工厂
     *
     * @return
     */
    public static LazySingletonInternal getInstance() {
        return lazyHolder.singleton;
    }
}
