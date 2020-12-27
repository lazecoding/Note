import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ListOfInt {
    public static void main(String[] args) {
        List<Integer> li = IntStream.range(38, 48)
                .boxed() // Converts ints to Integers
                .collect(Collectors.toList());
        System.out.println(li);
    }
}
/* Output:
[38, 39, 40, 41, 42, 43, 44, 45, 46, 47]
*/
