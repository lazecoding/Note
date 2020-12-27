import java.util.ArrayList;
import java.util.List;

public class UseObject {
    public static void main(String[] args) {
        List list = new ArrayList();
        list.add(2020);
        list.add("2020");
        int num1 = (int)list.get(0);
        int num2 = (int)list.get(1); // java.lang.String cannot be cast to java.lang.Integer
    }
}
