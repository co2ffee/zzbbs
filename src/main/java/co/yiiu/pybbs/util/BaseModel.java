package co.yiiu.pybbs.util;

import co.yiiu.pybbs.model.Comment;
import co.yiiu.pybbs.model.User;
import co.yiiu.pybbs.service.ISystemConfigService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Whitelist;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Component
public class BaseModel {

    private final Logger log = LoggerFactory.getLogger(BaseModel.class);

    @Resource
    private ISystemConfigService systemConfigService;

    private static final long MINUTE = 60 * 1000;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final long WEEK = 7 * DAY;
    private static final long MONTH = 31 * DAY;
    private static final long YEAR = 12 * MONTH;

    /**
     * 格式化日期
     *
     * @param date
     * @return
     */
    public String formatDate(Date date) {
        if (date == null) return "";

        long offset = System.currentTimeMillis() - date.getTime();
        if (offset > YEAR) {
            return (offset / YEAR) + "年前";
        } else if (offset > MONTH) {
            return (offset / MONTH) + "个月前";
        } else if (offset > WEEK) {
            return (offset / WEEK) + "周前";
        } else if (offset > DAY) {
            return (offset / DAY) + "天前";
        } else if (offset > HOUR) {
            return (offset / HOUR) + "小时前";
        } else if (offset > MINUTE) {
            return (offset / MINUTE) + "分钟前";
        } else {
            return "刚刚";
        }
    }

    public String formatContent(String content) {
        if (StringUtils.isEmpty(content)) return "";
        List<String> atUsers = StringUtil.fetchAtUser(content);
        if (!atUsers.isEmpty()) {
            for (String atUser : atUsers) {
                content = content.replace(atUser, "[" + atUser + "](" + systemConfigService.selectAllConfig().get("base_url")
                        .toString() + "/user/" + atUser.replace("@", "") + ")");
            }
        }
        content = MarkdownUtil.render(content);
        // 解析内容里的视频链接
        content = Jsoup.clean(content, Whitelist.relaxed().addTags("code", "pre", "video", "source", "iframe")
                .addAttributes("code", "class")
                .addAttributes("video", "class", "controls", "src")
                .addAttributes("source", "src", "type")
                .addAttributes("iframe", "src", "width", "height", "frameborder", "allowfullscreen", "class")
                .addProtocols("video", "src", "http", "https", "ftp", "data")
                .addProtocols("source", "src", "http", "https", "ftp", "data")
                .addProtocols("iframe", "src", "http", "https")
        );
        Document parse = Jsoup.parse(content, "", Parser.htmlParser());
        Elements tableElements = parse.select("table");
        tableElements.forEach(element -> element.addClass("table table-bordered"));
        // 给video标签包裹容器，确保Bootstrap正确撑开高度
        Elements videoElements = parse.select("video");
        videoElements.forEach(element -> {
            // 清理视频标签上可能存在的错误类名（防止样式冲突）
            element.removeClass("embed-responsive");
            element.removeClass("embed-responsive-16by9");
            element.addClass("embed-responsive-item");
            element.attr("width", "100%");
            element.attr("height", "100%");
            // 总是包裹在一个专用的 div 容器中
            element.wrap("<div class=\"embed-responsive embed-responsive-16by9\"></div>");
        });
        Elements aElements = parse.select("p");
        if (!aElements.isEmpty()) {
            aElements.forEach(element -> {
                try {
                    String href = element.text();
                    if (href.contains("//www.youtube.com/watch")) {
                        URL aUrl = new URL(href);
                        String query = aUrl.getQuery();
                        Map<String, Object> querys = StringUtil.formatParams(query);
                        element.text("");
                        element.addClass("embed-responsive embed-responsive-16by9");
                        element.append("<iframe class='embedded_video' src='https://www.youtube.com/embed/" + querys.get("v") +
                                "' frameborder='0' allowfullscreen></iframe>");
                    } else if (href.contains("//v.youku.com/v_show/")) {
                        element.text("");
                        URL aUrl = new URL(href);
                        String _href = "https://player.youku.com/embed/" + aUrl.getPath().replace("/v_show/id_", "").replace("" +
                                ".html", "");
                        element.addClass("embed-responsive embed-responsive-16by9");
                        element.append("<iframe class='embedded_video' src='" + _href + "' frameborder='0' " +
                                "allowfullscreen></iframe>");
                    } else if (href.contains("//www.bilibili.com/video/")) {
                        element.text("");
                        URL aUrl = new URL(href);
                        String _href = "//player.bilibili.com/player.html?aid=" + aUrl.getPath().replace("/video/av", "");
                        element.addClass("embed-responsive embed-responsive-16by9");
                        element.append("<iframe class='embedded_video' src='" + _href + "' frameborder='0' " +
                                "allowfullscreen></iframe>");
                    } else if (href.contains("//v.qq.com/x/cover/")) {
                        element.text("");
                        URL aUrl = new URL(href);
                        String _href = "https://v.qq.com/txp/iframe/player.html?vid=" + aUrl.getPath().substring(aUrl.getPath().lastIndexOf("/") + 1).replace(".html", "");
                        element.addClass("embed-responsive embed-responsive-16by9");
                        element.append("<iframe class='embedded_video' src='" + _href + "' frameborder='0' " +
                                "allowfullscreen></iframe>");
                    }
                } catch (MalformedURLException e) {
                    log.error(e.getMessage());
                }
            });
        }
        return parse.body().html();
    }

    // 将用户点赞的id从字符串转成集合
    public Set<String> getUpIds(String upIds) {
        if (StringUtils.isEmpty(upIds)) return new HashSet<>();
        return StringUtils.commaDelimitedListToSet(upIds);
    }

    public boolean isEmpty(String txt) {
        return StringUtils.isEmpty(txt);
    }

    public long commentSize(List<Comment> comments, User user) {
        return comments.stream().filter(comment -> comment.getStatus() || (user != null && comment.getUserId().equals(user.getId()) && !comment.getStatus())).count();
    }
}
