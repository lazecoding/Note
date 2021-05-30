package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: lazecoding
 * @date: 2021/5/23 19:27
 * @description: HttpHandler
 */
public class LocalHttpHandler implements HttpHandler {

    public static String IP = "";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Map<String, String> requestParamValue = null;
        if ("GET".equals(exchange.getRequestMethod())) {
            requestParamValue = handleGetRequest(exchange);
        } else if ("POST".equals(exchange.getRequestMethod())) {
            // requestParamValue = handlePostRequest(exchange);
        }
        InetSocketAddress inetSocketAddress = exchange.getLocalAddress();
        String response = "Ip:" + getIpAddress() + "  Port:" + inetSocketAddress.getPort() + "  Context:" + exchange.getHttpContext().getPath() + "  Params:" + (requestParamValue == null ? null : requestParamValue.toString());
        exchange.sendResponseHeaders(200, 0);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes("UTF-8"));
        os.close();
    }

    /**
     * 解析 GET 请求参数
     *
     * @param httpExchange
     * @return
     */
    private Map handleGetRequest(HttpExchange httpExchange) {
        String[] params = httpExchange.getRequestURI().toString().split("\\?");
        if (params == null || params.length < 2) {
            return null;
        }
        // 分割成多组
        params = params[1].split("&");
        Map<String, String> map = new HashMap<>(16);
        for (int i = 0; i < params.length; i++) {
            String[] temp = params[i].split("=");
            if (temp == null) {
                continue;
            }
            if (temp.length > 1) {
                map.put(temp[0], temp[1]);
            } else {
                map.put(temp[0], "");
            }
        }
        return map;
    }

    /**
     * 解析 POST 请求参数 ...
     */
    private Map handlePostRequest(HttpExchange exchange) throws IOException {
        Map<String, Object> parameters = (Map<String, Object>) exchange.getAttribute("parameters");
        InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), "utf-8");
        BufferedReader br = new BufferedReader(isr);
        String query = br.readLine();
        return null;
    }

    /**
     * 获取本地 IP
     *
     * @return
     */
    public static String getIpAddress() {
        // IP 缓冲，不要每次请求都通过网卡获取 IP
        if (IP.length() > 0) {
            return IP;
        }
        try {
            Enumeration<NetworkInterface> allNetInterfaces = NetworkInterface.getNetworkInterfaces();
            InetAddress ip = null;
            while (allNetInterfaces.hasMoreElements()) {
                NetworkInterface netInterface = (NetworkInterface) allNetInterfaces.nextElement();
                if (netInterface.isLoopback() || netInterface.isVirtual() || !netInterface.isUp()) {
                    continue;
                } else {
                    Enumeration<InetAddress> addresses = netInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        ip = addresses.nextElement();
                        if (ip != null && ip instanceof Inet4Address) {
                            IP = ip.getHostAddress();
                            return IP;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("IP地址获取失败" + e.toString());
        }
        return "";
    }

}
