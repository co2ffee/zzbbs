package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.util.FileUtil;
import co.yiiu.pybbs.util.Result;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 后台查看、清理dljblob（Backblaze B2）存储桶里的文件
 */
@Controller
@RequestMapping("/admin/file")
public class FileAdminController extends BaseAdminController {

    @Resource
    private FileUtil fileUtil;

    @RequiresPermissions("file:list")
    @GetMapping("/list")
    public String list(String continuationToken, Model model) {
        Map<String, Object> result = fileUtil.listBackblazeObjects(continuationToken, 50);
        model.addAttribute("files", result.get("files"));
        model.addAttribute("truncated", result.get("truncated"));
        model.addAttribute("nextContinuationToken", result.get("nextContinuationToken"));
        model.addAttribute("continuationToken", continuationToken);
        return "admin/file/list";
    }

    @RequiresPermissions("file:delete")
    @GetMapping("/delete")
    @ResponseBody
    public Result delete(String key) {
        boolean ok = fileUtil.deleteBackblazeObject(key);
        return ok ? success() : error("删除失败");
    }
}
