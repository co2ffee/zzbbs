package co.yiiu.pybbs.it;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把 src/main/resources/db/migration 里的 MySQL 迁移脚本翻译成 H2（MySQL 兼容模式）能执行的版本，
 * 输出到 target/h2-migration。迁移脚本本身保持不动，仍然是唯一的数据来源，测试只做机械的方言转换。
 */
public final class H2Migrations {

    private static final Path SOURCE = Paths.get("src/main/resources/db/migration");
    private static final Path TARGET = Paths.get("target/h2-migration");
    private static boolean prepared = false;

    private H2Migrations() {
    }

    /** 生成转换后的脚本，并让 Flyway 读取它们（通过 site.flyway_locations）。幂等。 */
    public static synchronized void prepare() {
        if (prepared) return;
        try {
            Files.createDirectories(TARGET);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(SOURCE, "*.sql")) {
                for (Path file : files) {
                    String sql = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                    Files.write(TARGET.resolve(file.getFileName()), translate(sql).getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("生成 H2 迁移脚本失败", e);
        }
        System.setProperty("site.flyway_locations", "filesystem:" + TARGET.toAbsolutePath());
        prepared = true;
    }

    private static final Pattern CREATE_TABLE = Pattern.compile("(?i)^\\s*CREATE TABLE\\s+`?(\\w+)`?.*");
    private static final Pattern FOREIGN_KEY = Pattern.compile("(?i)^\\s*(CONSTRAINT\\s+.*FOREIGN KEY.*?),?\\s*$");

    static String translate(String sql) {
        List<String> out = new ArrayList<>();
        List<String> foreignKeys = new ArrayList<>();   // 建表语句里的外键，统一挪到所有表建完之后再加
        String table = null;
        for (String line : sql.replace("\r\n", "\n").split("\n")) {
            String t = line.trim();
            Matcher ct = CREATE_TABLE.matcher(line);
            if (ct.matches()) table = ct.group(1);
            Matcher fk = FOREIGN_KEY.matcher(line);
            if (table != null && fk.matches()) {   // H2 建外键时被引用的表必须已存在，而导出脚本里有前向引用
                foreignKeys.add("ALTER TABLE `" + table + "` ADD " + fk.group(1) + ";");
                continue;
            }
            if (t.startsWith("#")) continue;                                  // MySQL 的 # 注释
            // 导出脚本靠 FOREIGN_KEY_CHECKS=0 暂时关闭外键检查（建表顺序里有前向引用），H2 里对应 REFERENTIAL_INTEGRITY
            if (t.startsWith("/*!40014 SET @OLD_FOREIGN_KEY_CHECKS")) { out.add("SET REFERENTIAL_INTEGRITY FALSE;"); continue; }
            if (t.startsWith("/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS")) { out.add("SET REFERENTIAL_INTEGRITY TRUE;"); continue; }
            if (t.startsWith("/*!")) continue;                                // 其余 MySQL 条件注释（SET NAMES、DISABLE KEYS ...）
            if (t.toUpperCase().startsWith("LOCK TABLES") || t.toUpperCase().startsWith("UNLOCK TABLES")) continue;
            // MySQL 的索引名只在表内唯一，H2 是全库唯一，所以给建表里的 KEY 名加上表名前缀
            if (table != null) line = line.replaceFirst("(?i)^(\\s*(?:UNIQUE\\s+)?KEY\\s+)`(\\w+)`", "$1`" + table + "_$2`");
            line = line.replaceAll("(?i)\\s+CHARACTER SET\\s+\\w+", "")       // 列级字符集/排序规则
                    .replaceAll("(?i)\\s+COLLATE\\s+\\w+", "")
                    // H2 把 text/longtext 当成 CLOB，resultType=map 的查询会返回 Clob 对象，JSON 序列化不了；MySQL 里直接是字符串。
                    // 测试环境统一用大 varchar，行为和 MySQL 一致
                    .replaceAll("(?i)\\b(longtext|mediumtext)\\b", "varchar(1000000)")
                    .replaceAll("(?i)(`\\w+`\\s+)text\\b", "$1varchar(1000000)")
                    .replaceAll("(?i)\\s+AFTER\\s+`\\w+`", "")                      // 列顺序对测试没有意义，H2 的 CHANGE COLUMN 不支持 AFTER
                    .replaceAll("(?i)^(\\s*)ALTER\\s+`(\\w+)`\\s+DROP DEFAULT", "$1ALTER COLUMN `$2` DROP DEFAULT")
                    .replace("b'0'", "0").replace("b'1'", "1");                // BIT(1) 默认值写法
            line = doubleQuotesToSingle(line);                                // MySQL 允许 "字符串"，H2 把它当标识符
            out.add(line);
        }
        // 外键被摘掉后，上一行如果以逗号结尾、下一行是 ")"，要去掉多余的逗号
        for (int i = 0; i < out.size() - 1; i++) {
            if (out.get(i).trim().endsWith(",") && out.get(i + 1).trim().startsWith(")")) {
                String l = out.get(i).trim();
                out.set(i, out.get(i).substring(0, out.get(i).lastIndexOf(',')));
            }
        }
        out.addAll(foreignKeys);
        return splitAlterTable(String.join("\n", out));
    }

    /**
     * 把 MySQL 的字符串写法转成标准 SQL：
     * - 单引号字符串里的 \' 转成 ''，\" 转成 "
     * - 单引号之外的 "字符串" 转成 '字符串'（MySQL 允许，H2 会把它当成标识符）
     * 反引号包起来的标识符原样保留。
     */
    static String doubleQuotesToSingle(String line) {
        StringBuilder out = new StringBuilder();
        char quote = 0;                       // 当前所在的引号：' " ` 或 0（不在引号里）
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote == 0) {
                if (c == '\'' || c == '`') { quote = c; out.append(c); }
                else if (c == '"') { quote = '"'; out.append('\''); }
                else out.append(c);
                continue;
            }
            if (quote == '`') { out.append(c); if (c == '`') quote = 0; continue; }
            if (c == '\\' && i + 1 < line.length()) {          // 反斜杠转义
                char n = line.charAt(i + 1);
                if (n == '\'') { out.append("''"); i++; continue; }
                if (n == '"') { out.append(quote == '"' ? "\"" : "\""); i++; continue; }
                out.append(c);
                continue;
            }
            if (quote == '\'' && c == '\'') {
                if (i + 1 < line.length() && line.charAt(i + 1) == '\'') { out.append("''"); i++; continue; }  // 已经是 ''
                quote = 0; out.append(c); continue;
            }
            if (quote == '"') {
                if (c == '"') { quote = 0; out.append('\''); continue; }
                if (c == '\'') { out.append("''"); continue; }     // 原来的双引号字符串里的单引号需要转义
            }
            out.append(c);
        }
        return out.toString();
    }

    private static final Pattern ALTER_TABLE = Pattern.compile("(?is)ALTER TABLE\\s+(`?\\w+`?)\\s+(.*?);");

    /** MySQL 允许一条 ALTER TABLE 里写多个子句（用逗号分隔），H2 一条只能做一个，这里拆成多条。 */
    static String splitAlterTable(String sql) {
        Matcher m = ALTER_TABLE.matcher(sql);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            StringBuilder replacement = new StringBuilder();
            for (String clause : splitTopLevel(m.group(2))) {
                replacement.append("ALTER TABLE ").append(m.group(1)).append(" ").append(clause.trim()).append(";\n");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement.toString().trim()));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static List<String> splitTopLevel(String body) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inQuote = false;
        StringBuilder cur = new StringBuilder();
        for (char c : body.toCharArray()) {
            if (c == '\'') inQuote = !inQuote;
            if (!inQuote) {
                if (c == '(') depth++;
                if (c == ')') depth--;
                if (c == ',' && depth == 0) { parts.add(cur.toString()); cur.setLength(0); continue; }
            }
            cur.append(c);
        }
        parts.add(cur.toString());
        return parts;
    }
}
