public class CatShopImpl<T extends Cat> implements CatShop<T> {
    @Override
    public T get(T obj) {
        return obj;
    }
}
