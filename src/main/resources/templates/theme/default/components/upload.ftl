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

    uploadImageFileEle.addEventListener("change", async function () {
        var maxSizeMB = ${maxSize!500};
        var maxSize = maxSizeMB * 1024 * 1024;

        for (var i = 0; i < uploadImageFileEle.files.length; i++) {
            if (uploadImageFileEle.files[i].size > maxSize) {
                err("文件不能超过 " + maxSizeMB + "MB");
                uploadImageFileEle.value = "";
                return;
            }
        }

        var type = $("#type").val();
        var file = uploadImageFileEle.files[0];

        var data = await getUploadUrl(type, "${_user.token!}");
        if (data.code !== 200) {
            throw new Error("获取上传地址失败");
        }

        var tdata = data.detail.urls[0];
        if (typeof tdata === "string") {
            tdata = JSON.parse(tdata);
        }

        $(".upload-progress-div").removeClass("d-none");
        uploadProgress.style.top = clientHeight * .4 + "px";

        var xhr = new XMLHttpRequest();
        xhr.open("PUT", tdata.uploadUrl);
        xhr.setRequestHeader("Content-Type", file.type);

        xhr.onload = function () {
            var oldContent = window.editor.getDoc().getValue();
            var insertContent = "";
            if (type === "topic") {
                insertContent = "![image](" + tdata.fileUrl + ")\n\n";
            } else if (type === "video") {
                insertContent = "<video class='embed-responsive embed-responsive-16by9' controls><source src='" + tdata.fileUrl + "' type='video/mp4'></video>\n\n";
            }
            window.editor.getDoc().setValue(oldContent + insertContent);
            window.editor.focus();
            window.editor.setCursor(window.editor.lineCount(), 0);
            document.getElementById("uploadImageForm").reset();
        };

        xhr.upload.onprogress = function (event) {
            if (event.lengthComputable) {
                var percent = event.loaded / event.total * 100;
                uploadProgress.textContent = "正在上传(" + percent.toFixed(2) + "%)...";
                if (percent === 100) {
                    $(".upload-progress-div").addClass("d-none");
                    $(".upload-progress").text("");
                }
            }
        };
        xhr.send(file);
    });
</script>
