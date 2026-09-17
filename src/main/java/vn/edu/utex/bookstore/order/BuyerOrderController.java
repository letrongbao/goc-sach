package vn.edu.utex.bookstore.order;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.web.Web;

@Controller
public final class BuyerOrderController {
  private final OrderService orders;

  public BuyerOrderController(OrderService orders) {
    this.orders = orders;
  }

  @GetMapping("/orders/{id}")
  public ModelAndView detail(@PathVariable long id, HttpServletRequest request, HttpServletResponse response) {
    Identity identity = (Identity) request.getAttribute("identity");
    try {
      request.setAttribute("orderDetail", orders.detail(identity.id(), id));
      request.setAttribute("view", "buyer-order-detail");
    } catch (Problem problem) {
      response.setStatus(problem.status);
      request.setAttribute("error", problem.getMessage());
      request.setAttribute("view", "error");
    }
    return Web.result(request, response);
  }
}
