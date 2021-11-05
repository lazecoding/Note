import com.hanweb.common.util.StringUtil;
import com.hanweb.common.util.file.LocalFileUtil;
import com.hanweb.jcms.constant.CacheType;
import com.hanweb.jcms.constant.Caches;
import com.hanweb.jcms.util.CacheUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 文件下载操作
 *
 */
@Component
public class DownloadFileCommon {

    private final Log logger = LogFactory.getLog(getClass());

    /**
     * 缓存生存时间：6 小时
     */
    private final int LLT = 60 * 60 * 6;

    @Autowired
    @Qualifier("LocalFileUtil")
    private LocalFileUtil localFileUtil;

    /**
     * 获取文件标识
     *
     * @return
     */
    public String getFileTag() {
        return StringUtil.getUUIDString();
    }

    /**
     * 初始化或更新 fileInfo （相同 fileTag ，更新，不存在则新增）
     *
     * @param fileInfo
     * @return
     */
    public boolean setFileInfo(FileInfo fileInfo) {
        if (fileInfo == null) {
            return false;
        }
        String fileTag = fileInfo.getFileTag();
        CacheUtil.setValue(Caches.FILEINFO.name(), fileTag, fileInfo, LLT);
        return true;
    }

    /**
     * 删除文件信息
     *
     * @param fileTag 文件标识
     * @return
     */
    public boolean removeFileInfo(String fileTag) {
        FileInfo fileInfo = CacheUtil.getValue(Caches.FILEINFO.name(), fileTag, CacheType.fileInfo);
        return removeFileInfo(fileInfo);
    }

    /**
     * 删除文件
     *
     * @param fileInfo 文件信息
     * @return
     */
    public boolean removeFileInfo(FileInfo fileInfo) {
        if (fileInfo == null) {
            return false;
        }
        // 删除缓存再删除文件
        String fileTag = fileInfo.getFileTag();
        //校验信息
        FileInfo fileInfoCache = CacheUtil.getValue(Caches.FILEINFO.name(), fileTag, CacheType.fileInfo);
        if (fileInfoCache == null) {
            logger.info("文件不存在！ fileTag:" + fileTag);
            return false;
        }
        if (!fileInfo.getFilePath().equals(fileInfoCache.getFilePath())) {
            logger.info("文件不匹配！ fileTag:" + fileTag);
            return false;
        }
        CacheUtil.removeKey(Caches.FILEINFO.name(), fileTag);
        String filePath = fileInfo.getFilePath();
        try {
            localFileUtil.deleteFile(filePath);
        } catch (Exception e) {
            logger.error("export Error ", e);
        }
        return true;
    }

    /**
     * 延时删除 (10分钟)
     *
     * @param fileTag 文件标识
     * @return
     */
    public void delayRemoveFileInfo(String fileTag) {
        FileInfo fileInfo = CacheUtil.getValue(Caches.FILEINFO.name(), fileTag, CacheType.fileInfo);
        delayRemoveFileInfo(fileInfo);
    }

    /**
     * 延时删除 (10分钟)
     *
     * @param fileInfo
     * @return
     */
    public void delayRemoveFileInfo(FileInfo fileInfo) {
        if (fileInfo == null) {
            return;
        }
        DownloadFileExecutor.doDelayTask(new Runnable() {
            @Override
            public void run() {
                removeFileInfo(fileInfo);
            }
        });
    }

}
