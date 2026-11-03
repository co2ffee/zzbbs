package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.exception.ApiAssert;
import co.yiiu.pybbs.model.Tag;
import co.yiiu.pybbs.model.Topic;
import co.yiiu.pybbs.service.IIndexedService;
import co.yiiu.pybbs.service.ITagService;
import co.yiiu.pybbs.service.ITopicService;
import co.yiiu.pybbs.util.MyPage;
import co.yiiu.pybbs.util.DateUtil;
import co.yiiu.pybbs.util.Result;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Controller
@RequestMapping("/admin/topic")
public class TopicAdminController extends BaseAdminController {

    @Resource
    private ITopicService topicService;
    @Resource
    private ITagService tagService;
    @Resource
    private IIndexedService indexedService;

    @RequiresPermissions("topic:list")
    @GetMapping("/list")
    @ResponseBody
    public Result list(@RequestParam(defaultValue = "1") Integer pageNo, String startDate, String endDate, String username) {
        if (username != null) username = username.replace("\"", "").replace("'", "");
        // 只填一端也要生效；结束日期包含当天（SQL 里是 < 结束日期的后一天 00:00）
        startDate = StringUtils.isEmpty(startDate) ? null : DateUtil.dayBoundary(startDate, 0);
        endDate = StringUtils.isEmpty(endDate) ? null : DateUtil.dayBoundary(endDate, 1);
        if (StringUtils.isEmpty(username)) username = null;
        MyPage<Map<String, Object>> page = topicService.selectAllForAdmin(pageNo, startDate, endDate, username);
        return success(page);
    }

    @RequiresPermissions("topic:edit")
    @GetMapping("/edit")
    @ResponseBody
    public Result edit(Integer id) {
        // 将标签集合转成逗号隔开的字符串
        List<Tag> tagList = tagService.selectByTopicId(id);
        String tags = StringUtils.collectionToCommaDelimitedString(tagList.stream().map(Tag::getName).collect(Collectors
                .toList()));
        Map<String, Object> data = new HashMap<>();
        data.put("topic", topicService.selectByIdForAdmin(id));
        data.put("tags", tags);
        return success(data);
    }

    @RequiresPermissions("topic:edit")
    @PostMapping("edit")
    @ResponseBody
    public Result update(Integer id, String title, String content, String tags) {
        title = Jsoup.clean(title, Whitelist.basic());
        ApiAssert.notEmpty(title, "话题标题不能为空");

        Topic topic = topicService.selectByIdForAdmin(id);
        topic.setTitle(title);
        topic.setContent(content);
        topic.setModifyTime(new Date());
        topicService.update(topic, tags);
        return success();
    }

    @RequiresPermissions("topic:good")
    @GetMapping("/good")
    @ResponseBody
    public Result good(Integer id) {
        Topic topic = topicService.selectByIdForAdmin(id);
        topic.setGood(!topic.getGood());
        topicService.update(topic, null);
        return success();
    }

    @RequiresPermissions("topic:top")
    @GetMapping("/top")
    @ResponseBody
    public Result top(Integer id) {
        Topic topic = topicService.selectByIdForAdmin(id);
        topic.setTop(!topic.getTop());
        topicService.update(topic, null);
        return success();
    }

    @RequiresPermissions("topic:delete")
    @GetMapping("/delete")
    @ResponseBody
    public Result delete(Integer id) {
        Topic topic = topicService.selectByIdForAdmin(id);
        topicService.delete(topic);
        return success();
    }

    @RequiresPermissions("topic:delete")
    @GetMapping("/restore")
    @ResponseBody
    public Result restore(Integer id) {
        Topic topic = topicService.selectByIdForAdmin(id);
        topicService.restore(topic);
        return success();
    }

    @RequiresPermissions("topic:physical_delete")
    @GetMapping("/physical_delete")
    @ResponseBody
    public Result physicalDelete(Integer id) {
        Topic topic = topicService.selectByIdForAdmin(id);
        ApiAssert.notNull(topic, "话题不存在");
        ApiAssert.isTrue(topic.getDeleted() == 1, "请先删除话题后再彻底删除");
        topicService.physicalDelete(topic);
        return success();
    }

    @RequiresPermissions("topic:index")
    @GetMapping("/index")
    @ResponseBody
    public Result index(Integer id) {
        Topic topic = topicService.selectByIdForAdmin(id);
        indexedService.indexTopic(String.valueOf(topic.getId()), topic.getTitle(), topic.getContent());
        return success();
    }

    @RequiresPermissions("topic:index_all")
    @GetMapping("/index_all")
    @ResponseBody
    public Result index_all() {
        indexedService.indexAllTopic();
        return success();
    }

    @RequiresPermissions("topic:delete_index")
    @GetMapping("/delete_index")
    @ResponseBody
    public Result delete_index(String id) { // ajax传过来的id，这用String接收，不用再转一次了
        indexedService.deleteTopicIndex(id);
        return success();
    }

    @RequiresPermissions("topic:delete_all_index")
    @GetMapping("/delete_all_index")
    @ResponseBody
    public Result delete_all_index() {
        indexedService.batchDeleteIndex();
        return success();
    }
}
