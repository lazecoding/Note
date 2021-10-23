

/**
 * 生产语音播报单元代码 
 */
public class MakeVoiceUnit {
    /**
     * 服务
     */
    public final static String SERVER = "http://xxx.xxx.com/voice";


    /**
     * 语言播报 API
     */
    public final static String API = "/interface/trans/txt2voice.do";

    /**
     * 本地调试
     */
    private final static String LOCATTEST = "";

    /**
     * 本地语音请求
     */
    public final static String MAKEVOICEAPI = "/interface/voice/makevoice.do?infoId=${infoId}&cataId=${cataId}&name=${name}&type=${type}";

    /**
     * 多信息正文字段
     */
    public final static String INFOFIELDNAME = "vc_content";

    /**
     * 长文本类型
     */
    public final static int TEXTFIELDTYPE = 7;

    /**
     * 语音播报缓存头
     */
    public static final String CACHENAME = "VOICE";

    /**
     * 占位符 id
     */
    public final static String IDPLACER = "${id}";

    /**
     * 占位符 content
     */
    public final static String CONTENTPLACER = "${content}";

    /**
     * 占位符 content
     */
    public final static String PATHPLACER = "${path}";

    /**
     * 占位符 infoId
     */
    public final static String INFOPLACER = "${infoId}";

    /**
     * 占位符 cataId
     */
    public final static String CATAPLACER = "${cataId}";

    /**
     * 占位符 name
     */
    public final static String NAMEPLACER = "${name}";

    /**
     * 占位符 type :0 生成 1 预览
     */
    public final static String TYPEPLACER = "${type}";

    public static final String TAG = "_";

    /**
     * 句号
     */
    public static final String ENDPOINT = "。";

    /**
     * 多信息、单语音播报字段
     */
    public static final HashMap<String, String> ARTICLEFIELDMAP = new HashMap<String, String>() {
        {
            put("标题", "vc_title");
            put("副标题", "vc_sectitle");
            put("引题", "vc_thdtitle");
            put("链接标题", "vc_linktitle");
            put("信息内容", "vc_content");
            put("来源", "vc_source");
            put("显示时间", "c_deploytime");
            // 成文日期,信息类别编号,信息公开节点,主题分类名称,体裁分类名称,发布机构,公开方式,五公开分类编码,项目公开分类编码,索引号,有效性,规范性文件登记号,文件编号,信息顺序号
            // 信息公开属性
            put("成文日期", "c_complatedate");

            put("信息类别编号", "vc_number"); // 组配通过编码查询
            put("信息公开节点", "xxgkjd");
            put("主题分类名称", "vc_ztflname");
            put("体裁分类名称", "vc_servicename");
            put("发布机构", "vc_fbjg");
            put("公开方式", "vc_openmodel");
            put("五公开分类编码", "vc_wgkfl");            // 存的主键
            put("项目公开分类编码", "vc_xmgkfl"); // 不解析的嘛？
            put("索引号", "vc_indexcode");
            put("有效性", "vc_validate");                     // 通过主键去查
            put("规范性文件登记号", "vc_standardfile");
            // put("服务对象", "<!--xxgkservice-->");
            put("文件编号", "vc_filenumber");
            put("信息顺序号", "i_infoordernumber");
        }
    };

    /**
     * 效果源码 调用 TTS
     */
    public final static String HTMLFORTTS = "<div>\n" +
            "  <audio controls id=\"" + IDPLACER + "\">\n" +
            "    <source src=\"\" type=\"audio/mpeg\">\n" +
            "  </audio>\n" +
            "  <script type=\"text/javascript\">\n" +
            "    $.ajax({\n" +
            "      url: '" + SERVER + API + "',\n" +
            "      type: 'POST',\n" +
            //     "      async: false,\n" +
            "      data: {\n" +
            "        \"text\": \"" + CONTENTPLACER + "\",\n" +
            "        \"download\": \"1\",\n" +
            "        \"spd\": \"5\"\n" +
            "      },\n" +
            "      success: function (path) {\n" +
            "        console.log('path:' + path);\n" +
            "        var obj = document.getElementById(\"" + IDPLACER + "\");\n" +
            "        obj.src = '" + SERVER + "' + path;\n" +
            "      }\n" +
            "    })\n" +
            "  </script>\n" +
            "</div>";

    /**
     * 效果源码 调用本地
     */
    public final static String HTMLFORLOCAL =
            "<div>\n" +
                    "  <audio controls id=\"" + IDPLACER + "\">\n" +
                    "    <source src=\"\" type=\"audio/mpeg\">\n" +
                    "  </audio>\n" +
                    "  <script type=\"text/javascript\">\n" +
                    "    $.ajax({\n" +
                    "      url: '" + LOCATTEST + MAKEVOICEAPI + "',\n" +
                    "      type: 'GET',\n" +
                    //    "      async: false,\n" +
                    "      success: function (path) {\n" +
                    "        console.log('path:' + path);\n" +
                    "        var obj = document.getElementById(\"" + IDPLACER + "\");\n" +
                    "        obj.src = path;\n" +
                    "      }\n" +
                    "    })\n" +
                    "  </script>\n" +
                    "</div>";

    String a = "<audio controls>\n" +
            "    <source src=\"" + LOCATTEST + MAKEVOICEAPI + "\" type=\"audio/mpeg\">\n" +
            "</audio>";

    /**
     * 获取 TTS 请求路径
     *
     * @return
     */
    public static String getAPIPath() {
        return SERVER + API;
    }

    /**
     * 获取文件路径
     *
     * @param path
     * @return
     */
    public static String getFilePath(String path) {
        return SERVER + path;
    }

    /**
     * 获取缓存的 名(废弃，和项目沟通，每个文章页只能设置一个语音播报的标签，避免 removeAll,即 keys)
     *
     * @param infoId
     * @param cataId
     * @param type
     * @return
     */
    public static String getCacheName(int infoId, int cataId, int type) {
        return Caches.VOICE.name() + ":" + cataId + TAG + infoId + TAG + type;
    }

    /**
     * 获取缓存的 key (和项目沟通，每个文章页只能设置一个语音播报的标签)
     *
     * @return
     */
    public static String getCacheKey(int infoId, int cataId, int type) {
        return cataId + TAG + infoId + TAG + type;
    }

    /**
     * 生成语音播报代码 FOR TTS
     *
     * @param id      audio 标签 id
     * @param content 内容
     * @return
     */
    public static String getUnit(String id, String content) {
        String unit = HTMLFORTTS;
        id = id + NumberUtil.getInt(Math.random() * 100000);
        unit = unit.replace(IDPLACER, id).replace(CONTENTPLACER, content);
        return unit;
    }

    /**
     * 生成语音播报代码 FOR LOCAL
     *
     * @param infoId audio 标签 id
     * @param cataId 内容
     * @param name   表名
     * @return
     */
    public static String getUnit(int infoId, int cataId, String name, int type) {
        String unit = HTMLFORLOCAL;
        String audioId = name.replace(",","") + NumberUtil.getInt(Math.random() * 100000);
        unit = unit.replace(INFOPLACER, StringUtil.getString(infoId))
                .replace(CATAPLACER, StringUtil.getString(cataId))
                .replace(NAMEPLACER, name)
                .replace(IDPLACER, audioId)
                .replace(TYPEPLACER, StringUtil.getString(type));
        return unit;
    }

    /**
     * 获取所有的 <!--(VOICE) (VOICE)--> 标签内容 (VOICE)(VOICE)
     * @param script
     * @return
     */
    public static ArrayList getVoiceTagUnit(String script) {
        if (StringUtil.isEmpty(script)) {
            return null;
        }

        String indexStartTag = "<!--(VOICE)";
        String indexEndTag = "(VOICE)-->";
        ArrayList<String> fieldList = new ArrayList<>();

        String html = script;
        while (html.contains(indexStartTag) && html.contains(indexEndTag)
                && (html.indexOf(indexStartTag) < html.indexOf(indexEndTag))) {
            int length = html.length();
            int startIndex = html.indexOf(indexStartTag);
            int endIndex = html.indexOf(indexEndTag);
            int end = endIndex + 10;
            // 理论上 while 的判断保证了 end 不会大于 length 了 其实
            if (endIndex != -1 && end <= length) {
                // 截取
                fieldList.add(html.substring(startIndex, end));
            }
            // 截取后的内容
            html = html.substring(end);
        }
        return fieldList;
    }
}
