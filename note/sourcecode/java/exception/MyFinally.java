public class MyFinally {
    public static void main(String[] args) {
        int i = 4;
        while (i > 0) {
            System.out.println("main i:" + func(i));
            i--;
        }
    }

    public static int func(int i) {

        try {
            i *= i;
            if (i == 9) {
                System.out.println("return i:" + i);
                return i;
            }
            if (i == 4) {
                System.out.println("throw i:" + i);
                throw new Exception();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("finally i:" + i);
        }
        return i;
    }
}