<#include "../layout/layout.ftl">
<#include "../layout/paginate.ftl">
<@html page_title="文件管理" page_tab="file">
    <section class="content-header">
        <h1>
            文件管理
            <small>dljblob 存储桶</small>
        </h1>
        <ol class="breadcrumb">
            <li><a href="/admin/index"><i class="fa fa-dashboard"></i> 首页</a></li>
            <li class="active">文件管理</li>
        </ol>
    </section>
    <section class="content">
        <div class="box box-info">
            <div class="box-header with-border">
                <h3 class="box-title">文件列表</h3>
                <span class="pull-right">共 ${totalCount} 个文件，总大小 <span id="totalSizeText"></span></span>
            </div>
            <div class="box-body">
                <table class="table table-bordered">
                    <thead>
                    <tr>
                        <th>预览</th>
                        <th>
                            <a href="?sortBy=key&sortOrder=<#if sortBy == 'key' && sortOrder == 'asc'>desc<#else>asc</#if>">
                                Key<#if sortBy == 'key'><#if sortOrder == 'asc'> ▲<#else> ▼</#if></#if>
                            </a>
                        </th>
                        <th>
                            <a href="?sortBy=size&sortOrder=<#if sortBy == 'size' && sortOrder == 'asc'>desc<#else>asc</#if>">
                                大小<#if sortBy == 'size'><#if sortOrder == 'asc'> ▲<#else> ▼</#if></#if>
                            </a>
                        </th>
                        <th>
                            <a href="?sortBy=lastModified&sortOrder=<#if sortBy == 'lastModified' && sortOrder == 'asc'>desc<#else>asc</#if>">
                                最后修改时间<#if sortBy == 'lastModified'><#if sortOrder == 'asc'> ▲<#else> ▼</#if></#if>
                            </a>
                        </th>
                        <th>操作</th>
                    </tr>
                    </thead>
                    <tbody>
                    <#list files as file>
                        <tr>
                            <td>
                                <#if file.key?lower_case?ends_with(".mp4")>
                                    <a href="${file.url}" target="_blank">视频</a>
                                <#else>
                                    <a href="${file.url}" target="_blank"><img src="${file.url}" style="height:40px"/></a>
                                </#if>
                            </td>
                            <td>${file.key}</td>
                            <td class="file-size" data-bytes="${file.size?c}"></td>
                            <td>${file.lastModified}</td>
                            <td>
                                <#if sec.hasPermission("file:delete")>
                                    <button onclick="deleteFile('${file.key?js_string}')" class="btn btn-xs btn-danger">
                                        删除
                                    </button>
                                </#if>
                            </td>
                        </tr>
                    </#list>
                    </tbody>
                </table>
            </div>
        </div>
        <@paginate currentPage=pageNo totalPage=totalPage actionUrl="/admin/file/list"
        urlParas="&sortBy=${sortBy}&sortOrder=${sortOrder}"/>
    </section>
    <script>
        function formatBytes(bytes) {
            if (bytes < 1024) return bytes + " B";
            var units = ["KB", "MB", "GB", "TB"];
            var size = bytes;
            var i = -1;
            do {
                size /= 1024;
                i++;
            } while (size >= 1024 && i < units.length - 1);
            return size.toFixed(2) + " " + units[i];
        }

        $("#totalSizeText").text(formatBytes(${totalSize?c}));
        $(".file-size").each(function () {
            $(this).text(formatBytes(parseInt($(this).data("bytes"))));
        });
    </script>
    <#if sec.hasPermission("file:delete")>
        <script>
            function deleteFile(key) {
                if (confirm("确定要删除这个文件吗？删除后无法恢复")) {
                    $.get("/admin/file/delete?key=" + encodeURIComponent(key), function (data) {
                        if (data.code === 200) {
                            toast("成功", "success");
                            setTimeout(function () {
                                window.location.reload();
                            }, 700);
                        } else {
                            toast(data.description);
                        }
                    })
                }
            }
        </script>
    </#if>
</@html>
