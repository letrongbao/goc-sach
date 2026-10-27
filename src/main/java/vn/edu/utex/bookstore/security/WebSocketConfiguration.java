package vn.edu.utex.bookstore.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

/** Same-origin authenticated updates, without public topics or client-selected user IDs. */
@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
  private final OrderNotificationSocket socket;
  public WebSocketConfiguration(OrderNotificationSocket socket){this.socket=socket;}
  @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(socket,"/ws").addInterceptors(new HttpSessionHandshakeInterceptor(){
      @Override public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,
          org.springframework.http.server.ServerHttpResponse response,org.springframework.web.socket.WebSocketHandler handler,
          java.util.Map<String,Object> attributes) throws Exception {
        if(request instanceof org.springframework.http.server.ServletServerHttpRequest servlet) {
          var session=servlet.getServletRequest().getSession(false);
          if(session==null)return false;attributes.put("httpSession",session);
        }
        return super.beforeHandshake(request,response,handler,attributes);
      }
    });
  }
}
