package vn.edu.utex.bookstore.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.config.App;

@Component
public class OrderNotificationSocket extends TextWebSocketHandler {
  private record Connection(WebSocketSession session,Identity identity) {}
  private final App app;
  private final ObjectMapper json;
  private final Map<String,Connection> connections=new ConcurrentHashMap<>();
  private final Map<String,String> previous=new ConcurrentHashMap<>();
  public OrderNotificationSocket(App app,ObjectMapper json){this.app=app;this.json=json;}
  @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    Identity actor=session.getPrincipal() instanceof Authentication a && a.getPrincipal() instanceof Identity i?i:null;
    if(actor==null||app.auth.sessionIdentity(actor)==null){session.close(CloseStatus.POLICY_VIOLATION);return;}
    connections.put(session.getId(),new Connection(new ConcurrentWebSocketSessionDecorator(session,3000,65536),actor));
  }
  @Override protected void handleTextMessage(WebSocketSession session,TextMessage message) throws Exception {
    if(!"ping".equals(message.getPayload())){session.close(CloseStatus.POLICY_VIOLATION);connections.remove(session.getId());previous.remove(session.getId());}
  }
  @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status){connections.remove(session.getId());previous.remove(session.getId());}
  @Scheduled(fixedDelay=5000,initialDelay=5000)
  public void publishChanges() {
    for(var entry:connections.entrySet()) {
      var c=entry.getValue();
      try {
        var http=(jakarta.servlet.http.HttpSession)c.session.getAttributes().get("httpSession");
        if(http==null)throw new IllegalStateException();http.getCreationTime();
        if(!c.session.isOpen()||app.auth.sessionIdentity(c.identity)==null)throw new IllegalStateException();
        String payload=json.writeValueAsString(snapshot(c.identity));
        String old=previous.put(entry.getKey(),payload);
        if(!payload.equals(old))c.session.sendMessage(new TextMessage("{\"initial\":"+(old==null)+",\"orders\":"+payload+"}"));
      } catch(Exception e) {
        connections.remove(entry.getKey());previous.remove(entry.getKey());
        try{c.session.close(CloseStatus.POLICY_VIOLATION);}catch(Exception ignored){}
      }
    }
  }
  public List<Map<String,Object>> snapshot(Identity actor) {
    if(app.auth.sessionIdentity(actor)==null)throw new vn.edu.utex.bookstore.common.Problem(403,"Phiên đã hết hạn.");
    return app.store.tx(d->{var all=new TreeMap<Long,vn.edu.utex.bookstore.order.Order>();
      d.ordersFor(actor.id()).forEach(o->all.put(o.id,o));
      d.shopsFor(actor.id(),actor.isAdmin()).forEach(s->d.ordersForShop(s.id).forEach(o->all.put(o.id,o)));
      return all.descendingMap().values().stream().limit(100).map(o->{var r=d.returnForOrder(o.id,false);
        return Map.<String,Object>of("id",o.id,"status",o.status,"payment",o.paymentStatus,"returnStatus",r==null?"":r.status);}).toList();});
  }
}
