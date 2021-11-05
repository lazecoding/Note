
import java.io.Serializable;

/**
 * 文件信息
 *
 */
public class FileInfo implements Serializable {
    /**
     * 文件标识  UUID
     */
    private String fileTag = "";

    /**
     * 文件路径(全路径)
     */
    private String filePath = "";

    /**
     * 文件名
     */
    private String fileName = "";

    /**
     * 文件状态： 0 未完成； 1 已完成。 （即可以下载）
     */
    private int state = 0;

    public String getFileTag() {
        return fileTag;
    }

    public void setFileTag(String fileTag) {
        this.fileTag = fileTag;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "fileTag='" + fileTag + '\'' +
                ", filePath='" + filePath + '\'' +
                ", fileName='" + fileName + '\'' +
                ", state=" + state +
                '}';
    }
}
