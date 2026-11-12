package co.yiiu.pybbs.util;

import co.yiiu.pybbs.config.SiteConfig;

import java.net.URLDecoder;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * “今天”的时间范围，给仪表盘的“今日新增”统计用。
 * <p>
 * 原来的 SQL 用 MySQL 的 curdate()，它按 MySQL 服务器自己的时区算“今天”；而 in_time 是 Java 按 JDBC 连接串里的
 * serverTimezone（默认 Asia/Shanghai）写进去的。两边时区不一致时（比如 MySQL 容器是 UTC），北京时间凌晨到早上八点
 * 之间新增的数据会被算到“昨天”，统计出来全是 0。
 * 所以改成在 Java 里按同一个时区算出今天的起止时刻，作为参数传给 SQL。
 */
public final class TodayRange {

    private static final Pattern SERVER_TIMEZONE = Pattern.compile("[?&]serverTimezone=([^&]+)");

    private TodayRange() {
    }

    /** 业务时区：取数据库连接串里的 serverTimezone；没有的话用 JVM 默认时区 */
    public static ZoneId zone() {
        try {
            String url = SpringContextUtil.getBean(SiteConfig.class).getDatasource_url();
            if (url != null) {
                Matcher m = SERVER_TIMEZONE.matcher(url);
                if (m.find()) return ZoneId.of(URLDecoder.decode(m.group(1), "UTF-8"));
            }
        } catch (Exception ignored) {
            // 取不到配置或时区名不合法，回退到默认时区
        }
        return ZoneId.systemDefault();
    }

    /** 今天 00:00:00（含） */
    public static Date start() {
        ZoneId zone = zone();
        return Date.from(LocalDate.now(zone).atStartOfDay(zone).toInstant());
    }

    /** 明天 00:00:00（不含） */
    public static Date end() {
        ZoneId zone = zone();
        return Date.from(LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant());
    }
}
