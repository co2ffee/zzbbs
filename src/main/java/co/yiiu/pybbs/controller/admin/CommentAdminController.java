package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.model.Comment;
import co.yiiu.pybbs.model.Topic;
import co.yiiu.pybbs.service.ICommentService;
import co.yiiu.pybbs.service.ITopicService;
import co.yiiu.pybbs.util.MyPage;
import co.yiiu.pybbs.util.Result;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Controller
@RequestMapping("/admin/comment")
public class CommentAdminController extends BaseAdminController {

    @Resource
    private ICommentService commentService;
    @Resource
    private ITopicService topicService;

    @RequiresPermissions("comment:list")
    @GetMapping("/list")
    @ResponseBody
    public Result list(@RequestParam(defaultValue = "1") Integer pageNo, String startDate, String endDate, String username) {
        if (username != null) username = username.replace("\"", "").replace("'", "");
        if (StringUtils.isEmpty(startDate)) startDate = null;
        if (StringUtils.isEmpty(endDate)) endDate = null;
        if (StringUtils.isEmpty(username)) username = null;
        MyPage<Map<String, Object>> page = commentService.selectAllForAdmin(pageNo, startDate, endDate, username);
        return success(page);
    }

    @RequiresPermissions("comment:edit")
    @GetMapping("/edit")
    @ResponseBody
    public Result edit(Integer id) {
        Comment comment = commentService.selectByIdForAdmin(id);
        Topic topic = topicService.selectByIdForAdmin(comment.getTopicId());
        Map<String, Object> data = new HashMap<>();
        data.put("comment", comment);
        data.put("topic", topic);
        return success(data);
    }

    @RequiresPermissions("comment:edit")
    @PostMapping("/edit")
    @ResponseBody
    public Result update(Integer id, String content) {
        Comment comment = commentService.selectByIdForAdmin(id);
        comment.setContent(content);
        commentService.update(comment);
        return success();
    }

    @RequiresPermissions("comment:examine")
    @GetMapping("/examine")
    @ResponseBody
    public Result examine(Integer id) {
        Comment comment = commentService.selectByIdForAdmin(id);
        comment.setStatus(true);
        commentService.update(comment);
        return success();
    }

    @RequiresPermissions("comment:delete")
    @GetMapping("/delete")
    @ResponseBody
    public Result delete(Integer id) {
        Comment comment = commentService.selectByIdForAdmin(id);
        commentService.delete(comment);
        return success();
    }

    @RequiresPermissions("comment:delete")
    @GetMapping("/restore")
    @ResponseBody
    public Result restore(Integer id) {
        Comment comment = commentService.selectByIdForAdmin(id);
        commentService.restore(comment);
        return success();
    }
}
