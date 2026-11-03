package co.yiiu.pybbs.controller.admin;

import co.yiiu.pybbs.util.FileUtil;
import co.yiiu.pybbs.util.Result;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台查看、清理dljblob（Backblaze B2）存储桶里的文件
 */
@Controller
@RequestMapping("/admin/file")
public class FileAdminController extends BaseAdminController {

    private static final int PAGE_SIZE = 50;

    @Resource
    private FileUtil fileUtil;

    @RequiresPermissions("file:list")
    @GetMapping("/list")
    @ResponseBody
    public Result list(@RequestParam(defaultValue = "1") Integer pageNo,
                        @RequestParam(defaultValue = "size") String sortBy,
                        @RequestParam(defaultValue = "desc") String sortOrder) {
        List<Map<String, Object>> files = fileUtil.listAllBackblazeObjects();
        long totalSize = files.stream().mapToLong(f -> (Long) f.get("size")).sum();

        Comparator<Map<String, Object>> comparator;
        if ("key".equals(sortBy)) {
            comparator = Comparator.comparing(f -> (String) f.get("key"));
        } else if ("lastModified".equals(sortBy)) {
            comparator = Comparator.comparing(f -> (String) f.get("lastModified"));
        } else {
            sortBy = "size";
            comparator = Comparator.comparing(f -> (Long) f.get("size"));
        }
        if (!"asc".equals(sortOrder)) {
            sortOrder = "desc";
            comparator = comparator.reversed();
        }
        files.sort(comparator);

        int total = files.size();
        int totalPage = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        if (pageNo < 1) pageNo = 1;
        if (totalPage > 0 && pageNo > totalPage) pageNo = totalPage;
        int fromIndex = (pageNo - 1) * PAGE_SIZE;
        int toIndex = Math.min(fromIndex + PAGE_SIZE, total);
        List<Map<String, Object>> pageFiles = fromIndex < toIndex ? files.subList(fromIndex, toIndex) : Collections.emptyList();

        Map<String, Object> data = new HashMap<>();
        data.put("files", pageFiles);
        data.put("totalCount", total);
        data.put("totalSize", totalSize);
        data.put("pageNo", pageNo);
        data.put("totalPage", totalPage);
        data.put("sortBy", sortBy);
        data.put("sortOrder", sortOrder);
        return success(data);
    }

    @RequiresPermissions("file:delete")
    @GetMapping("/delete")
    @ResponseBody
    public Result delete(String key) {
        boolean ok = fileUtil.deleteBackblazeObject(key);
        return ok ? success() : error("删除失败");
    }
}
