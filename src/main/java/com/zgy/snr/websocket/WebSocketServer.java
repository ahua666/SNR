package com.zgy.snr.websocket;

import com.zgy.snr.common.utils.NettyByteAndStringUtils;
import com.zgy.snr.datasource.SendProcess;
import com.zgy.snr.netty.NettyServerHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import static com.zgy.snr.netty.NettyServerHandler.getConst;
import static io.netty.channel.ChannelInboundHandlerAdapter.*;

/**
 * @author zgy
 * @data 2021/3/15 15:45
 */

@Slf4j
@ServerEndpoint(value = "/ws/asset")
@Component
public class WebSocketServer {


    @PostConstruct
    public void init() {
        System.out.println("websocket 加载");
    }

    private static final AtomicInteger OnlineCount = new AtomicInteger(0);
    // concurrent包的线程安全Set，用来存放每个客户端对应的Session对象。
    private static CopyOnWriteArraySet<Session> SessionSet = new CopyOnWriteArraySet<Session>();

    NettyServerHandler cs = new NettyServerHandler();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session) {
        SessionSet.add(session);
        // 在线数加1
        int cnt = OnlineCount.incrementAndGet();
        log.info(String.valueOf(session.getRequestURI()));
        log.info("有连接加入，当前连接数为：{},sessionId={}", cnt, session.getId());
        SendMessage(session, "连接成功");
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose(Session session) {
        SessionSet.remove(session);
        int cnt = OnlineCount.decrementAndGet();
        log.info("有连接关闭，当前连接数为：{}", cnt);
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) throws NoSuchFieldException, IllegalAccessException, UnsupportedEncodingException {
        log.info("来自前端客户端的消息：{}", message);
        SendMessage(session, "websocket服务端收到消息：" + message);
        log.info(String.valueOf(cs.CHANNEL_MAP));
        /**
         * 接受到数据后下发内容进行数据验证（数据验证 数据库操作 等等） 这里我没有做数据验证。
         * 这里我直接群发数据(后续需要判断对应的网关发送数据，防止网关堵塞。)
         */
        ConcurrentHashMap.KeySetView<ChannelId, ChannelHandlerContext> map = NettyServerHandler.CHANNEL_MAP.keySet();
        for (ChannelId key : map) {
            ChannelHandlerContext value = cs.CHANNEL_MAP.get(key);
            System.out.println(key + "->>>>" + value);
            value.writeAndFlush(message.getBytes("GBK"));
        }


    }

    /**
     * 出现错误
     *
     * @param session
     * @param error
     */
    @OnError
    public void onError(Session session, Throwable error) {
        log.error("发生错误：{}，Session ID： {}", error.getMessage(), session.getId());
        error.printStackTrace();
    }

    /**
     * 发送消息，实践表明，每次浏览器刷新，session会发生变化。
     *
     * @param session
     * @param message
     */
    public static void SendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("发送消息出错：{}", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 群发消息
     *
     * @param message
     * @throws IOException
     */
    public static void BroadCastInfo(String message) throws IOException {
        for (Session session : SessionSet) {
            if (session.isOpen()) {
                SendMessage(session, message);
            }
        }
    }

    /**
     * 指定Session发送消息
     *
     * @param sessionId
     * @param message
     * @throws IOException
     */
    public static void SendMessage(String message, String sessionId) throws IOException {
        Session session = null;
        for (Session s : SessionSet) {
            if (s.getId().equals(sessionId)) {
                session = s;
                break;
            }
        }
        if (session != null) {
            SendMessage(session, message);
        } else {
            log.warn("没有找到你指定ID的会话：{}", sessionId);
        }
    }
}
