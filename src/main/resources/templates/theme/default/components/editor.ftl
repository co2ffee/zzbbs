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
        <div class="upload-progress">0%</div>
        <div class="upload-progress-fade"></div>
    </div>
    <script type="text/javascript">
        async function getUploadUrl(type, token) {
            const res = await fetch("/api/getUploadUrl", {
                method: "POST",
                headers: {
                    "Content-Type": "application/x-www-form-urlencoded",
                    "token": token
                },
                body: "type=" + encodeURIComponent(type)
            });
            return res.json();
        }

        async function uploadImageWithProgress(files, type, cb) {
            var file = files[0];

            var data = await getUploadUrl(type, "${_user.token!}");
            if (data.code !== 200) {
                throw new Error("获取上传地址失败");
            }

            var tdata = data.detail.urls[0];
            if (typeof tdata === "string") {
                tdata = JSON.parse(tdata);
            }

            var clientHeight = document.documentElement.clientHeight;
            var uploadProgress = $(".upload-progress");
            $(".upload-progress-div").removeClass("d-none");
            uploadProgress.css("top", clientHeight * .4 + "px");

            var xhr = new XMLHttpRequest();
            xhr.open("PUT", tdata.uploadUrl);
            xhr.setRequestHeader("Content-Type", file.type);

            xhr.onload = function () {
                cb({fileUrl: tdata.fileUrl});
            };

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
                lineNumbers: true,
                indentUnit: 4,
                tabSize: 4,
                matchBrackets: true,
                mode: 'markdown',
                lineWrapping: true,
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
                    if (uploadImageFileEle.files[i].size > maxSize) {
                        err("文件不能超过 " + maxSizeMB + "MB");
                        uploadImageFileEle.value = "";
                        return;
                    }
                }

                uploadImageWithProgress(uploadImageFileEle.files, type.value, function (data) {
                    var oldContent = window.editor.getDoc().getValue();
                    var insertContent = "";
                    var url = data.fileUrl;
                    if (type.value === "topic") {
                        insertContent = "![image](" + url + ")\n\n";
                    } else if (type.value === "video") {
                        insertContent = "<div class='embed-responsive embed-responsive-16by9'><video class='embed-responsive-item' controls><source src='" + url + "' type='video/mp4'></video></div>\n\n";
                    }
                    window.editor.getDoc().setValue(oldContent + insertContent);
                    window.editor.focus();
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
            window._E.config.customUploadImg = function (resultFiles, insertImgFn) {
                uploadImageWithProgress(resultFiles, "topic", function (data) {
                    insertImgFn(data.fileUrl);
                });
            }
        </script>
    </#if>
</#macro>
