package vn.edu.utex.bookstore.operations;

import jakarta.servlet.http.*;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.web.Web;

@Controller
public class OperationsController {
  private final OperationsService operations;
  private final OrderService orders;
  public OperationsController(OperationsService operations,OrderService orders){this.operations=operations;this.orders=orders;}
  @GetMapping("/admin/operations")
  public ModelAndView admin(HttpServletRequest req,HttpServletResponse res){return render(req,res,()->{
    var actor=identity(req);req.setAttribute("accounts",operations.accounts(actor));req.setAttribute("carriers",operations.allCarriers(actor));
    req.setAttribute("moderation",operations.moderation(actor));req.setAttribute("shops",orders.sellerShops(actor));req.setAttribute("view","admin-operations");});}
  @GetMapping("/seller/operations")
  public ModelAndView seller(@RequestParam(required=false) Long shopId,HttpServletRequest req,HttpServletResponse res){return render(req,res,()->{
    var actor=identity(req);var shops=orders.sellerShops(actor);req.setAttribute("shops",shops);
    Long selected=shopId!=null?shopId:shops.isEmpty()?null:shops.getFirst().id();req.setAttribute("shopId",selected);
    if(selected!=null){req.setAttribute("vouchers",operations.vouchers(actor,selected));req.setAttribute("revenue",operations.revenue(actor,selected));req.setAttribute("returns",operations.shopReturns(actor,selected));}
    req.setAttribute("view","seller-operations");});}
  @PostMapping("/admin/operations/users/{id}")
  public ModelAndView block(@PathVariable long id,@RequestParam boolean blocked,HttpServletRequest req,HttpServletResponse res){return action(req,res,"/admin/operations",()->operations.block(identity(req),id,blocked));}
  @PostMapping("/admin/operations/carriers")
  public ModelAndView carrier(@RequestParam(required=false) Long id,@RequestParam String name,@RequestParam String fee,@RequestParam(defaultValue="false") boolean active,HttpServletRequest req,HttpServletResponse res){return action(req,res,"/admin/operations",()->operations.saveCarrier(identity(req),id,name,fee,active));}
  @PostMapping("/admin/operations/commission")
  public ModelAndView commission(@RequestParam long shopId,@RequestParam String rate,HttpServletRequest req,HttpServletResponse res){return action(req,res,"/admin/operations",()->operations.commission(identity(req),shopId,rate));}
  @PostMapping("/admin/operations/reviews/{id}")
  public ModelAndView review(@PathVariable long id,@RequestParam boolean visible,@RequestParam String note,HttpServletRequest req,HttpServletResponse res){return action(req,res,"/admin/operations",()->operations.moderate(identity(req),id,visible,note));}
  @PostMapping("/seller/operations/vouchers")
  public ModelAndView voucher(@RequestParam long shopId,@RequestParam String code,@RequestParam String amount,@RequestParam String minimum,@RequestParam int uses,@RequestParam String expiry,HttpServletRequest req,HttpServletResponse res){return action(req,res,"/seller/operations?shopId="+shopId,()->{
    java.time.Instant until;try{until=LocalDate.parse(expiry).plusDays(1).atStartOfDay(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();}catch(RuntimeException e){throw Problem.invalid("Ngày hết hạn không hợp lệ.");}
    operations.createVoucher(identity(req),shopId,code,amount,minimum,uses,until);});}
  @PostMapping("/seller/operations/vouchers/disable")
  public ModelAndView disable(@RequestParam long shopId,@RequestParam String code,HttpServletRequest req,HttpServletResponse res){return action(req,res,"/seller/operations?shopId="+shopId,()->operations.disableVoucher(identity(req),code));}
  @GetMapping("/orders/{id}/return")
  public ModelAndView returns(@PathVariable long id,HttpServletRequest req,HttpServletResponse res){return render(req,res,()->{
    var actor=identity(req);req.setAttribute("orderDetail",orders.detail(actor.id(),id));req.setAttribute("returnRequest",operations.buyerReturn(actor.id(),id));req.setAttribute("view","buyer-return");});}
  @PostMapping("/orders/{id}/return")
  public ModelAndView requestReturn(@PathVariable long id,@RequestParam String reason,HttpServletRequest req,HttpServletResponse res){return action(req,res,"/orders/"+id+"/return",()->operations.requestReturn(identity(req).id(),id,reason));}
  @PostMapping("/seller/operations/returns/{id}")
  public ModelAndView resolve(@PathVariable long id,@RequestParam long shopId,@RequestParam String status,@RequestParam String note,@RequestParam(defaultValue="") String reference,@RequestParam(defaultValue="false") boolean settled,HttpServletRequest req,HttpServletResponse res){return action(req,res,"/seller/operations?shopId="+shopId,()->{
    if("REFUNDED".equals(status)&&!settled)throw Problem.invalid("Cần xác nhận đã nhận sách và đã hoàn tiền bên ngoài.");operations.resolveReturn(identity(req),id,status,note,reference);});}
  private static Identity identity(HttpServletRequest req){var a=(Identity)req.getAttribute("identity");if(a==null)throw new Problem(401,"Vui lòng đăng nhập.");return a;}
  private ModelAndView render(HttpServletRequest req,HttpServletResponse res,Runnable work){try{work.run();}catch(Problem p){res.setStatus(p.status);req.setAttribute("error",p.getMessage());req.setAttribute("view","error");}return Web.result(req,res);}
  private ModelAndView action(HttpServletRequest req,HttpServletResponse res,String path,Runnable work){try{work.run();req.getSession().setAttribute("notice","Đã lưu thay đổi.");}catch(Problem p){res.setStatus(p.status);req.setAttribute("error",p.getMessage());req.setAttribute("view","error");return Web.result(req,res);}return new ModelAndView("redirect:"+path);}
}
