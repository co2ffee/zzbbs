<#macro editor _type="" _content="" style="MD">
    <form onsubmit="return;" id="uploadImageForm">
        <input type="hidden" id="type" value="${_type}"/>
        <input type="file" class="d-none" id="uploadImageFileEle" multiple
               accept="image/jpeg,image/jpg,image/png,image/gif,video/mp4"/>
    </form>
    <style>
        .upload-progress-fade {
            width: 100%;
            height: 100%;
            position: fixed;
            top: 0;
            bottom: 0;
            left: 0;
            right: 0;
            background-color: #000;
            z-index: 123456;
            opacity: .3;
            vertical-align: middle;
            text-align: center;
            color: #000;
            padding-top: 200px;
        }

        .upload-progress {
            position: fixed;
            left: 0;
            right: 0;
            top: 220px;
            border: 1px solid #d3d4d3;
            background-color: #fff;
            margin: 0 auto;
            text-align: center;
            padding: 30px 20px;
            opacity: 1;
            z-index: 1234567;
            width: 220px;
        }
    </style>
    <div class="upload-progress-div d-none">
        <div class="upload-progress">100%</div>
        <div class="upload-progress-fade"></div>
    </div>
    <script type="text/javascript">
        async function getUploadUrl(file, type, token) {
            const fd = new FormData();
            fd.append("file", file);
            fd.append("type", type);

            const res = await fetch("/api/getUploadUrl", {
                method: "POST",
                headers: {
                    "token": token   // 加上 token
                },
                body: fd
            });

            return res.json();
        }

        async function uploadImageWithProgress(files, cb) {
            var clientHeight = document.documentElement.clientHeight;
            var uploadProgress = $(".upload-progress");

            $(".upload-progress-div").removeClass("d-none");
            uploadProgress.css("top", clientHeight * .4 + "px");
            // var _m = layer.msg('上传中(' + upload_progress + '%)...', {icon: 16, shade: 0.5, time: -1});
            var type = $("#type").val();
            //原逻辑
            // var fd = new FormData();
            //新
            var file = files[0];
            var type = document.getElementById("type").value;
            // for (var i = 0; i < files.length; i++) {
            //     fd.append("file", files[i]);
            // }
            // fd.append("type", type);

            // 获取 presigned URL
            let data = await getUploadUrl(file, type, "${_user.token!}");
            console.log(data);
            if (data.code !== 200) {
                throw new Error("获取上传地址失败");
            }
            console.log(data);
            // 如果后端返回的是字符串
            let tdata = data.detail.urls[0];
            if (typeof data.detail.urls[0] === "string") {
                tdata = JSON.parse(data.detail.urls[0]);
            }
            console.log(tdata);
            const uploadUrl = tdata.uploadUrl;
            const fileUrl = tdata.fileUrl;

            //OSS直连改造
            const xhr = new XMLHttpRequest();
            xhr.open("PUT", uploadUrl);
            xhr.setRequestHeader("Content-Type", file.type);

            xhr.onload = function () {
                cb({fileUrl: fileUrl});
            };

            // 获取上传进度
            xhr.upload.onprogress = function (event) {
                if (event.lengthComputable) {
                    var percent = event.loaded / event.total * 100;
                    uploadProgress.text("正在上传(" + percent.toFixed(2) + "%)...");
                    if (percent === 100) {
                        $(".upload-progress-div").addClass("d-none");
                        $(".upload-progress").text("");
                    }
                }
            };
            xhr.send(file);
        }
    </script>
    <#if style=="MD">
        <link href="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.47.0/codemirror.min.css" rel="stylesheet">
        <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.47.0/codemirror.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.47.0/mode/markdown/markdown.min.js"></script>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/codemirror/5.47.0/addon/display/placeholder.min.js"></script>
        <textarea name="content" id="content" class="form-control"
                  placeholder="内容，支持Markdown语法">${_content!?html}</textarea>
        <script type="text/javascript">
            CodeMirror.keyMap.default["Shift-Tab"] = "indentLess";
            CodeMirror.keyMap.default["Tab"] = "indentMore";
            window.editor = CodeMirror.fromTextArea(document.getElementById("content"), {
                lineNumbers: true,     // 显示行数
                indentUnit: 4,         // 缩进单位为4
                tabSize: 4,
                matchBrackets: true,   // 括号匹配
                mode: 'markdown',     // Markdown模式
                lineWrapping: true,    // 自动换行
            });
            window.editor.setSize('auto', '450px');
            var uploadImageFileEle = document.getElementById("uploadImageFileEle");
            var type = document.getElementById("type");

            function uploadFile(t) {
                type.value = t;
                uploadImageFileEle.click();
            }

            uploadImageFileEle.addEventListener("change", function () {
                var maxSizeMB = ${maxSize!500};
                var maxSize = maxSizeMB * 1024 * 1024;

                for (var i = 0; i < uploadImageFileEle.files.length; i++) {

                    var file = uploadImageFileEle.files[i];

                    if (file.size > maxSize) {
                        err("文件不能超过 " + maxSizeMB + "MB");
                        uploadImageFileEle.value = "";
                        return;
                    }

                }

                uploadImageWithProgress(uploadImageFileEle.files, function (data) {
                    var oldContent = window.editor.getDoc().getValue();
                    // if (oldContent) oldContent += '\n\n';
                    var insertContent = "";
                    var url = data.fileUrl;
                    if (type.value === "topic") {
                        insertContent += "![image](" + url + ")\n\n"
                    } else if (type.value === "video") {
                        insertContent += "<video class='embed-responsive embed-responsive-16by9' controls><source src='" + url + "' type='video/mp4'></video>\n\n";
                    }
                    window.editor.getDoc().setValue(oldContent + insertContent);
                    window.editor.focus();
                    //定位到文档的最后一个字符的位置
                    window.editor.setCursor(window.editor.lineCount(), 0);
                    uploadImageFileEle.value = "";
                });
            });
        </script>
    </#if>

    <#if style=="RICH">
        <div id="content">${_content!}</div>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/wangEditor/3.1.1/wangEditor.min.js"></script>
        <script type="text/javascript">
            const E = window.wangEditor;
            window._E = new E(document.getElementById("content"));
            window._E.create();
            window._E.config.height = 500;
            // editor.config.uploadImgServer = '/api/upload';
            // editor.config.uploadFileName = 'file';
            window._E.config.customUploadImg = function (resultFiles, insertImgFn) {
                // resultFiles 是 input 中选中的文件列表
                // insertImgFn 是获取图片 url 后，插入到编辑器的方法
                uploadImageWithProgress(resultFiles, function (data) {
                    var url = data.fileUrl;
                    // 上传图片，返回结果，将图片插入到编辑器中
                    insertImgFn(url);
                });
            }
        </script>
    </#if>
</#macro>