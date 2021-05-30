package server;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * @author: lazecoding
 * @date: 2021/5/23 19:30
 * @description:
 */
public class Servers {
    public static void main(String[] args) throws Exception {
        // http://www.lazy.com/
        initLocalServer(9091);
        initLocalServer(9092);
        initLocalServer(9093);
        initLocalServer(9094);
        initLocalServer(9095);
    }

    /**
     * 初始化并启动服务器
     *
     * @param port
     */
    public static void initLocalServer(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        //创建上下文监听,拦截包含/test的请求*
        server.createContext("/test", new LocalHttpHandler());
        server.createContext("/nginx", new LocalHttpHandler());
        server.createContext("/", new LocalHttpHandler());
        server.start();
    }
}
