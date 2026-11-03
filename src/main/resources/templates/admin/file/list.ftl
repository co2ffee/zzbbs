<#include "../layout/layout.ftl">
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
            </div>
            <div class="box-body">
                <table class="table table-bordered">
                    <thead>
                    <tr>
                        <th>预览</th>
                        <th>Key</th>
                        <th>大小</th>
                        <th>最后修改时间</th>
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
                            <td>${(file.size / 1024)?round} KB</td>
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
                <#if continuationToken?? && continuationToken != "">
                    <a href="/admin/file/list" class="btn btn-default btn-sm">回到第一页</a>
                </#if>
                <#if truncated>
                    <a href="/admin/file/list?continuationToken=${nextContinuationToken?url('UTF-8')}"
                       class="btn btn-primary btn-sm">下一页</a>
                </#if>
            </div>
        </div>
    </section>
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
