package vn.edu.utex.bookstore.order;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.web.Web;

@Controller
public final class SellerOrderController {
  private final OrderService orders;
  private final vn.edu.utex.bookstore.payment.PaymentService payments;

  public SellerOrderController(OrderService orders, vn.edu.utex.bookstore.payment.PaymentService payments) {
    this.orders = orders;
    this.payments = payments;
  }

  @GetMapping("/seller/orders")
  public ModelAndView list(@RequestParam(required = false) Long shopId,
      HttpServletRequest request, HttpServletResponse response) {
    Identity actor = (Identity) request.getAttribute("identity");
    try {
      request.setAttribute("sellerShops", orders.sellerShops(actor));
      request.setAttribute("selectedShopId", shopId);
      request.setAttribute("sellerOrders", orders.sellerOrders(actor, shopId));
      request.setAttribute("view", "seller-orders");
    } catch (Problem problem) {
      error(request, response, problem);
    }
    return Web.result(request, response);
  }

  @GetMapping("/seller/orders/{id}")
  public ModelAndView detail(@PathVariable long id,
      HttpServletRequest request, HttpServletResponse response) {
    Identity actor = (Identity) request.getAttribute("identity");
    try {
      request.setAttribute("orderDetail", orders.sellerDetail(actor, id));
      request.setAttribute("view", "seller-order-detail");
    } catch (Problem problem) {
      error(request, response, problem);
    }
    return Web.result(request, response);
  }

  @PostMapping("/seller/orders/{id}/status")
  public ModelAndView advance(@PathVariable long id, @RequestParam String status,
      HttpServletRequest request, HttpServletResponse response) {
    return change(id, request, response, () -> orders.advance(identity(request), id, status),
        "Đã cập nhật đơn hàng.");
  }

  @PostMapping("/seller/orders/{id}/cancel")
  public ModelAndView cancel(@PathVariable long id, @RequestParam String reason,
      HttpServletRequest request, HttpServletResponse response) {
    return change(id, request, response, () -> payments.sellerCancel(identity(request), id, reason),
        "Đã hủy đơn và trả sách về kho.");
  }

  private ModelAndView change(long id, HttpServletRequest request, HttpServletResponse response,
      Runnable action, String notice) {
    try {
      action.run();
      request.getSession().setAttribute("notice", notice);
      Web.redirect(request, response, "/seller/orders/" + id);
      return null;
    } catch (Problem problem) {
      response.setStatus(problem.status);
      request.setAttribute("error", problem.getMessage());
      try {
        request.setAttribute("orderDetail", orders.sellerDetail(identity(request), id));
        request.setAttribute("view", "seller-order-detail");
      } catch (Problem ignored) {
        request.setAttribute("view", "error");
      }
      return Web.result(request, response);
    } catch (java.io.IOException redirectFailure) {
      throw new IllegalStateException("Không thể chuyển trang sau khi cập nhật đơn.", redirectFailure);
    }
  }

  private static Identity identity(HttpServletRequest request) {
    return (Identity) request.getAttribute("identity");
  }

  private static void error(HttpServletRequest request, HttpServletResponse response, Problem problem) {
    response.setStatus(problem.status);
    request.setAttribute("error", problem.getMessage());
    request.setAttribute("view", "error");
  }
}
