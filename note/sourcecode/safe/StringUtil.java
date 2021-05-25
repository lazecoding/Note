public class StringUtil {
    
    /**
     * 对两个字符串做常量级比较
     */
    public static boolean isEqual(String a, String b) {
        return isEqual(a.getBytes(), b.getBytes());
    }

    /**
     * 对两个 byte 数组做常量级比较
     */
    public static boolean isEqual(byte[] a, byte[] b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }

        int result = 0;
        // time-constant comparison
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

}