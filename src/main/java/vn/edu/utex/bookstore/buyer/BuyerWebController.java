package vn.edu.utex.bookstore.buyer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.auth.Tokens;
import vn.edu.utex.bookstore.common.FormProblem;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.order.OrderService;
import vn.edu.utex.bookstore.web.Web;

@Controller
public final class BuyerWebController {
  private static final String CHECKOUT_KEY = "checkoutIdempotencyKey";
  private final BuyerService buyer;
  private final OrderService orders;
  private final vn.edu.utex.bookstore.payment.PaymentService payments;

  public BuyerWebController(BuyerService buyer, OrderService orders, vn.edu.utex.bookstore.payment.PaymentService payments) {
    this.buyer = buyer;
    this.orders = orders;
    this.payments = payments;
  }

  @GetMapping("/account/profile")
  public ModelAndView profile(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    request.setAttribute("profile", buyer.profile(identity.id()));
    request.setAttribute("view", "buyer-profile");
    return Web.result(request, response);
  }

  @PostMapping("/account/profile")
  public ModelAndView saveProfile(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      buyer.saveProfile(identity.id(), request.getParameter("displayName"), request.getParameter("phone"));
      request.getSession().setAttribute("notice", "Đã lưu thông tin cá nhân.");
      return redirect(request, response, "/account/profile");
    } catch (Problem problem) {
      return showProblem(request, response, problem, () -> profile(request, response));
    }
  }

  @GetMapping("/account/addresses")
  public ModelAndView addresses(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      String edit = request.getParameter("edit");
      Address form = edit == null || edit.isBlank() ? emptyAddress()
          : buyer.address(identity.id(), Web.formId(edit, true));
      renderAddresses(request, identity, form);
    } catch (Problem problem) {
      response.setStatus(problem.status);
      request.setAttribute("error", problem.getMessage());
      renderAddresses(request, identity, emptyAddress());
    }
    return Web.result(request, response);
  }

  @PostMapping("/account/address/save")
  public ModelAndView saveAddress(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      buyer.saveAddress(identity.id(), Web.formId(request.getParameter("id"), false), request.getParameter("label"),
          request.getParameter("recipientName"), request.getParameter("phone"), request.getParameter("line1"),
          request.getParameter("ward"), request.getParameter("district"), request.getParameter("city"),
          "on".equals(request.getParameter("defaultAddress")));
      request.getSession().setAttribute("notice", "Đã lưu địa chỉ.");
      return redirect(request, response, "/account/addresses");
    } catch (Problem problem) {
      response.setStatus(problem.status);
      request.setAttribute("error", problem.getMessage());
      if (problem instanceof FormProblem fieldProblem)
        request.setAttribute("fieldErrors", java.util.Map.of(fieldProblem.field, fieldProblem.getMessage()));
      renderAddresses(request, identity, submittedAddress(request));
      return Web.result(request, response);
    }
  }

  @PostMapping("/account/address/delete")
  public ModelAndView deleteAddress(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      buyer.deleteAddress(identity.id(), Web.formId(request.getParameter("id"), true));
      request.getSession().setAttribute("notice", "Đã xóa địa chỉ.");
      return redirect(request, response, "/account/addresses");
    } catch (Problem problem) {
      return showProblem(request, response, problem, () -> addresses(request, response));
    }
  }

  @GetMapping("/favorites")
  public ModelAndView favorites(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    request.setAttribute("favorites", buyer.favoriteBooks(identity.id()));
    request.setAttribute("view", "buyer-favorites");
    return Web.result(request, response);
  }

  @PostMapping("/favorites/add")
  public ModelAndView addFavorite(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      long productId = Web.formId(request.getParameter("productId"), true);
      buyer.addFavorite(identity.id(), productId);
      request.getSession().setAttribute("notice", "Đã thêm sách vào danh sách yêu thích.");
      return redirect(request, response, "/product/detail?id=" + productId);
    } catch (Problem problem) {
      return showProblem(request, response, problem, () -> favorites(request, response));
    }
  }

  @PostMapping("/favorites/remove")
  public ModelAndView removeFavorite(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      buyer.removeFavorite(identity.id(), Web.formId(request.getParameter("productId"), true));
      request.getSession().setAttribute("notice", "Đã bỏ sách khỏi danh sách yêu thích.");
      return redirect(request, response, "/favorites");
    } catch (Problem problem) {
      return showProblem(request, response, problem, () -> favorites(request, response));
    }
  }

  @GetMapping("/recently-viewed")
  public ModelAndView recent(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    request.setAttribute("recentlyViewed", buyer.recentlyViewedBooks(identity.id(), 20));
    request.setAttribute("view", "buyer-recent");
    return Web.result(request, response);
  }

  @GetMapping("/cart")
  public ModelAndView cart(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    var lines = buyer.cart(identity.id());
    request.setAttribute("cartLines", lines);
    request.setAttribute("cartGroups", buyer.groupCart(lines));
    request.setAttribute("cartSubtotal", lines.stream().map(BuyerService.CartLine::price)
        .reduce(BigDecimal.ZERO, BigDecimal::add));
    request.setAttribute("cartReady", !lines.isEmpty() && lines.stream().allMatch(BuyerService.CartLine::sellable));
    request.setAttribute("view", "buyer-cart");
    return Web.result(request, response);
  }

  @PostMapping("/cart/items/add")
  public ModelAndView addCartItem(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      buyer.addToCart(identity.id(), Web.formId(request.getParameter("copyId"), true));
      request.getSession().setAttribute("notice", "Đã thêm bản sách vào giỏ.");
      return redirect(request, response, "/cart");
    } catch (Problem problem) {
      return showProblem(request, response, problem, () -> cart(request, response));
    }
  }

  @PostMapping("/cart/items/remove")
  public ModelAndView removeCartItem(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      buyer.removeFromCart(identity.id(), Web.formId(request.getParameter("copyId"), true));
      request.getSession().setAttribute("notice", "Đã bỏ bản sách khỏi giỏ.");
      return redirect(request, response, "/cart");
    } catch (Problem problem) {
      return showProblem(request, response, problem, () -> cart(request, response));
    }
  }

  @GetMapping("/checkout")
  public ModelAndView checkout(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    var lines = buyer.cart(identity.id());
    String key = (String) request.getSession().getAttribute(CHECKOUT_KEY);
    if (key == null && !lines.isEmpty()) {
      key = Tokens.random();
      request.getSession().setAttribute(CHECKOUT_KEY, key);
    }
    request.setAttribute("cartLines", lines);
    request.setAttribute("cartSubtotal", lines.stream().map(BuyerService.CartLine::price)
        .reduce(BigDecimal.ZERO, BigDecimal::add));
    request.setAttribute("checkoutQuote", orders.quote(identity.id()));
    request.setAttribute("checkoutKey", key == null ? "" : key);
    request.setAttribute("addresses", buyer.addresses(identity.id()));
    request.setAttribute("payosEnabled", payments.enabled());
    request.setAttribute("paymentMethod", "PAYOS".equals(request.getParameter("paymentMethod")) ? "PAYOS" : "COD");
    request.setAttribute("view", "buyer-checkout");
    return Web.result(request, response);
  }

  @PostMapping("/checkout")
  public ModelAndView submitCheckout(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      long address = Web.formId(request.getParameter("addressId"), true);
      String submittedKey = request.getParameter("idempotencyKey");
      String sessionKey = (String) request.getSession().getAttribute(CHECKOUT_KEY);
      if (sessionKey == null || !sessionKey.equals(submittedKey))
        throw Problem.invalid("Phiên đặt hàng đã hết hạn. Vui lòng tải lại trang.");
      var result = payments.checkout(identity.id(), address, submittedKey, request.getParameter("quoteFingerprint"), request.getParameter("paymentMethod"));
      request.getSession().removeAttribute(CHECKOUT_KEY);
      request.getSession().setAttribute(
          "notice", "PAYOS".equals(result.orders().getFirst().paymentMethod)
              ? "Đã tạo đơn. Vui lòng thanh toán từng đơn của cửa hàng trong thời gian giữ sách."
              : "Đã đặt hàng. Cửa hàng sẽ sớm xác nhận đơn của bạn.");
      if (result.orders().size() == 1 && "PAYOS".equals(result.orders().getFirst().paymentMethod))
        return redirect(request, response, "/orders/" + result.orders().getFirst().id + "/payment");
      return redirect(request, response, "/orders");
    } catch (Problem problem) {
      return showProblem(request, response, problem, () -> checkout(request, response));
    }
  }

  @PostMapping("/orders/cancel")
  public ModelAndView cancelOrder(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    try {
      payments.cancel(identity.id(), Web.formId(request.getParameter("id"), true));
      request.getSession().setAttribute("notice", "Đã hủy đơn hàng.");
      return redirect(request, response, "/orders");
    } catch (Problem problem) {
      return showProblem(request, response, problem, () -> orders(request, response));
    }
  }

  @GetMapping("/orders")
  public ModelAndView orders(HttpServletRequest request, HttpServletResponse response) {
    Identity identity = identity(request);
    if (identity == null) return redirect(request, response, "/auth/login");
    request.setAttribute("orders", orders.orders(identity.id()));
    request.setAttribute("view", "buyer-orders");
    return Web.result(request, response);
  }

  private static ModelAndView showProblem(HttpServletRequest request, HttpServletResponse response, Problem problem,
      java.util.function.Supplier<ModelAndView> renderer) {
    response.setStatus(problem.status);
    request.setAttribute("error", problem.getMessage());
    return renderer.get();
  }

  private static Identity identity(HttpServletRequest request) {
    return (Identity) request.getAttribute("identity");
  }

  private void renderAddresses(HttpServletRequest request, Identity identity, Address form) {
    request.setAttribute("addresses", buyer.addresses(identity.id()));
    request.setAttribute("addressForm", form);
    request.setAttribute("editingAddress", form.id != null);
    request.setAttribute("view", "buyer-addresses");
  }

  private static Address emptyAddress() {
    Address address = new Address();
    address.label = "";
    address.recipientName = "";
    address.phone = "";
    address.line1 = "";
    address.ward = "";
    address.district = "";
    address.city = "";
    return address;
  }

  private static Address submittedAddress(HttpServletRequest request) {
    Address address = emptyAddress();
    address.id = Web.positiveIdOrNull(request.getParameter("id"));
    address.label = value(request, "label");
    address.recipientName = value(request, "recipientName");
    address.phone = value(request, "phone");
    address.line1 = value(request, "line1");
    address.ward = value(request, "ward");
    address.district = value(request, "district");
    address.city = value(request, "city");
    address.defaultAddress = "on".equals(request.getParameter("defaultAddress"));
    return address;
  }

  private static String value(HttpServletRequest request, String name) {
    String value = request.getParameter(name);
    return value == null ? "" : value;
  }

  private static ModelAndView redirect(HttpServletRequest request, HttpServletResponse response, String path) {
    try {
      Web.redirect(request, response, path);
    } catch (java.io.IOException ignored) {
      // The container owns the response after a failed redirect.
    }
    return null;
  }
}
