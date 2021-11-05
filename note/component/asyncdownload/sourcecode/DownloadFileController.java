
import com.hanweb.common.annotation.Permission;
import com.hanweb.common.util.StringUtil;
import com.hanweb.common.util.file.LocalFileUtil;
import com.hanweb.common.util.mvc.FileResource;
import com.hanweb.common.util.mvc.JsonResult;
import com.hanweb.jcms.constant.CacheType;
import com.hanweb.jcms.constant.Caches;
import com.hanweb.jcms.util.CacheUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * 文件下载
 *
 */
@Controller
@Permission
@RequestMapping("manager/filedownload")
public class DownloadFileController {

    private final Log logger = LogFactory.getLog(getClass());

    /**
     * 失败 CODE
     */
    private final int FAILCODE = -1;

    @Autowired
    @Qualifier("LocalFileUtil")
    private LocalFileUtil localFileUtil;

    @Autowired
    private DownloadFileCommon downloadFileCommon;

    /**
     * 文件下载
     *
     * @param fileTag
     * @return
     */
    @RequestMapping(value = "file_download")
    public FileResource downloadFile(String fileTag) {
        fileTag = StringUtil.getSafeString(fileTag);
        if (StringUtil.isEmpty(fileTag)) {
            return null;
        }

        FileInfo fileInfo = CacheUtil.getValue(Caches.FILEINFO.name(), fileTag, CacheType.fileInfo);
        if (fileInfo == null) {
            logger.info("文件不存在！ fileTag:" + fileTag);
            return null;
        }

        String filePath = fileInfo.getFilePath();
        String strFileName = fileInfo.getFileName();
        FileResource fileResouce;

        try {
            fileResouce = localFileUtil.getFileResource(filePath, strFileName);
        } catch (Exception e) {
            logger.error("export Error ", e);
            return null;
        } finally {
            // 删除文件
            downloadFileCommon.removeFileInfo(fileInfo);
        }
        return fileResouce;
    }

    /**
     * 文件信息状态检查。0 未完成，1已完成，-1 文件不存在。
     **/
    @RequestMapping("filestate_check")
    @ResponseBody
    public JsonResult checkFileState(String fileTag) {
        fileTag = StringUtil.getSafeString(fileTag);
        JsonResult jsonResult = JsonResult.getInstance();
        if (StringUtil.isEmpty(fileTag)) {
            jsonResult.setSuccess(false);
            jsonResult.setCode(FAILCODE + "");
            jsonResult.setMessage("标识为空");
            return jsonResult;
        }
        FileInfo fileInfo = CacheUtil.getValue(Caches.FILEINFO.name(), fileTag, CacheType.fileInfo);
        if (fileInfo == null) {
            jsonResult.setSuccess(false);
            jsonResult.setCode(FAILCODE + "");
            jsonResult.setMessage("文件不存在");
            return jsonResult;
        }
        int state = fileInfo.getState();
        if (state == 0) {
            jsonResult.setSuccess(false);
            jsonResult.setCode(state + "");
            jsonResult.setMessage("文件生成中");
        } else {
            jsonResult.setSuccess(true);
            jsonResult.setCode(state + "");
            jsonResult.setMessage("文件生成完毕");
        }
        return jsonResult;
    }
}
