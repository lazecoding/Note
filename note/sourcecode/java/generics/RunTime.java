import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class RunTime {
    public static void main(String[] args) throws ClassNotFoundException, InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        List<Integer> list = new ArrayList();
        list.add(2020);
        Class c = Class.forName("java.util.ArrayList");
        Method m = c.getMethod("add", Object.class);
        m.invoke(list, "2021");
        System.out.println(list); // [2020, 2021]
    }
}
