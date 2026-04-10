<form onsubmit="return;" id="uploadImageForm">
    <input type="hidden" id="type" value="image"/>
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
        z-index: 9999;
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
        z-index: 10000;
        width: 220px;
    }
</style>
<div class="upload-progress-div d-none">
    <div class="upload-progress"></div>
    <div class="upload-progress-fade"></div>
</div>
<script>
    var clientHeight = document.documentElement.clientHeight;
    var uploadProgress = document.querySelector(".upload-progress");
    var uploadImageFileEle = document.getElementById("uploadImageFileEle");

    function uploadFile(type) {
        $("#type").val(type);
        uploadImageFileEle.click();
    }

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

    uploadImageFileEle.addEventListener("change", async function () {
        var maxSizeMB = $
        {
            maxSize
            !500
        }
        ;
        var maxSize = maxSizeMB * 1024 * 1024;

        for (var i = 0; i < uploadImageFileEle.files.length; i++) {

            var file = uploadImageFileEle.files[i];

            if (file.size > maxSize) {
                err("文件不能超过 " + maxSizeMB + "MB");
                uploadImageFileEle.value = "";
                return;
            }

        }
        $(".upload-progress-div").removeClass("d-none");
        uploadProgress.css("top", clientHeight * .4 + "px");

        var type = $("#type").val();
        var ele = $(this)[0];
        //原逻辑
        // var fd = new FormData();
        //新
        var fd = ele.files[0];
        var file = ele.files[0];

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
        const uploadUrl = tdata.uploadUrl;
        const fileUrl = tdata.fileUrl;

        //OSS直连改造
        const xhr = new XMLHttpRequest();
        xhr.open("PUT", uploadUrl);
        xhr.setRequestHeader("Content-Type", file.type);

        xhr.onload = function () {
            var data = xhr.response;
            console.log(data);
            // if (data.errno !== 0) {
            //     suc("上传成功");
            // } else {
            //     var error = "";
            //     for (var k = 0; k < data.errors.length; k++) {
            //         error += data.errors[k] + "<br/>";
            //     }
            //     err(error);
            // }
            var oldContent = window.editor.getDoc().getValue();
            var insertContent = "";
            if (type === "topic") {
                insertContent += "![image](" + fileUrl + ")\n\n"
            } else if (type === "video") {
                insertContent += "<video class='embed-responsive embed-responsive-16by9' controls><source src='" + fileUrl + "' type='video/mp4'></video>\n\n";
            }

            window.editor.getDoc().setValue(oldContent + insertContent);
            window.editor.focus();
            //定位到文档的最后一个字符的位置
            window.editor.setCursor(window.editor.lineCount(), 0);
            document.getElementById("uploadImageForm").reset();
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
        xhr.send(fd);
    })
</script>
