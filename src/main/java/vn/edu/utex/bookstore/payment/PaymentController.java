package vn.edu.utex.bookstore.payment;

import jakarta.servlet.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.web.Web;
import java.util.Map;

@Controller
public final class PaymentController {
  private final PaymentService payments;
  private final OrderService orders;
  public PaymentController(PaymentService payments, OrderService orders) { this.payments = payments; this.orders = orders; }

  @GetMapping("/orders/{id}/payment")
  public ModelAndView page(@PathVariable long id, HttpServletRequest req, HttpServletResponse res) {
    try {
      var identity = identity(req);
      req.setAttribute("orderDetail", orders.detail(identity.id(), id));
      req.setAttribute("payment", payments.view(identity.id(), id));
      req.setAttribute("view", "buyer-payment");
    } catch (Problem problem) {
      res.setStatus(problem.status); req.setAttribute("error", problem.getMessage()); req.setAttribute("view", "error");
    }
    res.setHeader("Cache-Control", "no-store");
    return Web.result(req, res);
  }

  @PostMapping("/orders/{id}/payment/start")
  public ModelAndView start(@PathVariable long id, HttpServletRequest req, HttpServletResponse res) throws java.io.IOException {
    try { res.sendRedirect(payments.start(identity(req).id(), id)); return null; }
    catch (Problem problem) { return errorPage(id, req, res, problem); }
  }
  @PostMapping("/orders/{id}/payment/refresh")
  public ModelAndView refresh(@PathVariable long id, HttpServletRequest req, HttpServletResponse res) throws java.io.IOException {
    try {
      payments.refresh(identity(req).id(), id);
      req.getSession().setAttribute("notice", "Đã kiểm tra kết quả thanh toán.");
      Web.redirect(req, res, "/orders/" + id + "/payment"); return null;
    } catch (Problem problem) { return errorPage(id, req, res, problem); }
  }
  @PostMapping("/orders/{id}/payment/cancel")
  public ModelAndView cancel(@PathVariable long id, HttpServletRequest req, HttpServletResponse res) throws java.io.IOException {
    try {
      payments.cancel(identity(req).id(), id);
      req.getSession().setAttribute("notice", "Đã hủy đơn hàng sau khi kiểm tra thanh toán.");
      Web.redirect(req, res, "/orders/" + id); return null;
    } catch (Problem problem) { return errorPage(id, req, res, problem); }
  }

  @GetMapping({"/payments/payos/return", "/payments/payos/cancel"})
  public ModelAndView returned(@RequestParam(required = false) Long orderCode, HttpServletRequest req, HttpServletResponse res) throws java.io.IOException {
    Identity identity = (Identity) req.getAttribute("identity");
    if (identity == null) { Web.redirect(req, res, "/auth/login"); return null; }
    // Only the local, owned order is displayed. status/code/cancel/id query parameters are not evidence of money.
    if (orderCode == null) { Web.redirect(req, res, "/orders"); return null; }
    try { return page(payments.orderIdForCode(identity.id(), orderCode), req, res); }
    catch (Problem problem) {
      res.setStatus(problem.status); req.setAttribute("error", problem.getMessage()); req.setAttribute("view", "error");
      return Web.result(req, res);
    }
  }

  @PostMapping(value = "/payments/payos/webhook", consumes = "application/json")
  @ResponseBody
  public ResponseEntity<?> webhook(HttpServletRequest req) throws java.io.IOException {
    byte[] body = req.getInputStream().readNBytes(32_769);
    if (body.length > 32_768) return ResponseEntity.status(413).body(Map.of("received", false));
    try { payments.webhook(new String(body, java.nio.charset.StandardCharsets.UTF_8)); return ResponseEntity.ok(Map.of("received", true)); }
    catch (Problem problem) { return ResponseEntity.status(problem.status).body(Map.of("received", false)); }
  }

  @GetMapping("/admin/payments")
  public ModelAndView review(HttpServletRequest req, HttpServletResponse res) {
    req.setAttribute("paymentReviews", payments.reviews(identity(req)));
    req.setAttribute("view", "payment-review"); return Web.result(req, res);
  }

  private ModelAndView errorPage(long id, HttpServletRequest req, HttpServletResponse res, Problem problem) {
    res.setStatus(problem.status); req.setAttribute("error", problem.getMessage()); return page(id, req, res);
  }
  private static Identity identity(HttpServletRequest req) {
    Identity identity = (Identity) req.getAttribute("identity");
    if (identity == null) throw new Problem(401, "Vui lòng đăng nhập."); return identity;
  }
}
