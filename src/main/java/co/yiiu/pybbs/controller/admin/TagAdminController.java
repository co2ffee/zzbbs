package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.exception.ApiAssert;
import co.yiiu.pybbs.model.Tag;
import co.yiiu.pybbs.service.ITagService;
import co.yiiu.pybbs.util.FileUtil;
import co.yiiu.pybbs.util.Result;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * Created by tomoya.
 * Copyright (c) 2018, All Rights Reserved.
 * https://atjiu.github.io
 */
@Controller
@RequestMapping("/admin/tag")
public class TagAdminController extends BaseAdminController {

    @Resource
    private ITagService tagService;
    @Resource
    private FileUtil fileUtil;

    @RequiresPermissions("tag:list")
    @GetMapping("/list")
    @ResponseBody
    public Result list(@RequestParam(defaultValue = "1") Integer pageNo, String name) {
        if (name != null) name = name.replace("\"", "").replace("'", "");
        if (StringUtils.isEmpty(name)) name = null;
        IPage<Tag> page = tagService.selectAll(pageNo, null, name);
        return success(page);
    }

    @RequiresPermissions("tag:edit")
    @GetMapping("/edit")
    @ResponseBody
    public Result edit(Integer id) {
        return success(tagService.selectById(id));
    }

    // 图标上传和用户端一样走“预签名直传”：先调 /upload_url 拿地址，浏览器直接 PUT 到存储桶，
    // 然后提交时只带 icon（fileUrl）。原来这里在服务端调 fileUtil.upload，线上启用 AWS 上传插件后，
    // 不管有没有选文件都会返回一整串预签名 JSON，被当成 icon 写进数据库，超出列长度导致保存失败。
    @RequiresPermissions("tag:edit")
    @PostMapping("/edit")
    @ResponseBody
    public Result update(Integer id, String name, String description, Integer topicCount, String icon) {
        Tag tag = tagService.selectById(id);
        ApiAssert.notNull(tag, "标签不存在");
        tag.setName(name);
        tag.setDescription(description);
        tag.setTopicCount(topicCount);
        // 没有传新图标：保持原来的图标不变
        if (!StringUtils.isEmpty(icon)) {
            ApiAssert.isTrue(fileUtil.isOwnStorageUrl(icon), "图标地址不合法");
            tag.setIcon(icon);
        }
        tagService.update(tag);
        return success();
    }

    // 获取标签图标的上传地址（预签名，10 分钟有效）
    @RequiresPermissions("tag:edit")
    @GetMapping("/upload_url")
    @ResponseBody
    public Result uploadUrl() {
        String json;
        try {
            json = fileUtil.awsBlazeUpload(null, "avatar", null);
        } catch (Exception e) {
            return error("获取上传地址失败，请检查 Backblaze 配置");
        }
        return success(JSON.parseObject(json));
    }

    @RequiresPermissions("tag:delete")
    @GetMapping("/delete")
    @ResponseBody
    public Result delete(Integer id) {
        Tag tag = tagService.selectById(id);
        if (tag.getTopicCount() > 0) return error("标签还关联着话题，要先把相关联的话题都删了，这个标签才能删除");
        tagService.delete(id);
        return success();
    }

    // 同步标签的话题数
    @RequiresPermissions("tag:async")
    @GetMapping("/async")
    @ResponseBody
    public Result async() {
        tagService.async();
        return success();
    }
}
