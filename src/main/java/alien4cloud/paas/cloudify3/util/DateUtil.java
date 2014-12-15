package alien4cloud.paas.cloudify3.util;

import java.util.Date;

public class DateUtil {

    public static int compare(Date left, Date right) {
        if (left == null) {
            if (right != null) {
                return -1;
            } else {
                return 0;
            }
        } else {
            if (right != null) {
                return left.compareTo(right);
            } else {
                return 1;
            }
        }
    }
}
