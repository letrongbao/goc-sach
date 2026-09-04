package vn.edu.utex.bookstore.web;

import jakarta.servlet.http.*;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.config.App;

@Controller
public final class ShopController {
  private final App app;
  public ShopController(App app) { this.app = app; }

  @GetMapping({"/account/shops", "/seller/shops", "/seller/shop", "/admin/shops"})
  public ModelAndView page(HttpServletRequest request, HttpServletResponse response) throws Exception {
    try { populate(request); }
    catch (Problem problem) { error(request, response, problem); }
    return Web.result(request, response);
  }

  @PostMapping({"/account/shop/apply", "/admin/shop/approve", "/admin/shop/reject", "/admin/shop/status", "/seller/shop/member"})
  public ModelAndView action(HttpServletRequest request, HttpServletResponse response) throws Exception {
    Identity actor = (Identity) request.getAttribute("identity");
    String path = request.getServletPath();
    try {
      String redirect = "/admin/shops";
      switch (path) {
        case "/account/shop/apply" -> {
          app.shops.apply(actor, request.getParameter("name"), request.getParameter("address"));
          redirect = "/account/shops";
        }
        case "/admin/shop/approve" -> app.shops.approve(actor, Web.formId(request.getParameter("id"), true));
        case "/admin/shop/reject" -> app.shops.reject(actor, Web.formId(request.getParameter("id"), true), request.getParameter("reason"));
        case "/admin/shop/status" -> {
          String status = Objects.toString(request.getParameter("status"), "");
          if (!Set.of("ACTIVE", "SUSPENDED").contains(status)) throw Problem.invalid("Trạng thái không hợp lệ.");
          app.shops.setSuspended(actor, Web.formId(request.getParameter("shopId"), true),
              "SUSPENDED".equals(status), request.getParameter("reason"));
        }
        case "/seller/shop/member" -> {
          long shopId = Web.formId(request.getParameter("shopId"), true);
          app.shops.setMember(actor, shopId, request.getParameter("login"), request.getParameter("role"), "on".equals(request.getParameter("active")));
          redirect = "/seller/shop?id=" + shopId;
        }
        default -> throw Problem.missing();
      }
      request.getSession().setAttribute("notice", "Đã lưu thay đổi cửa hàng.");
      Web.redirect(request, response, redirect);
    } catch (Problem problem) {
      if (path.equals("/account/shop/apply")) {
        response.setStatus(problem.status);
        request.setAttribute("error", problem.getMessage());
        applicationPage(request, actor);
      } else error(request, response, problem);
    }
    return Web.result(request, response);
  }

  private void populate(HttpServletRequest request) {
    Identity actor = (Identity) request.getAttribute("identity");
    switch (request.getServletPath()) {
      case "/account/shops" -> applicationPage(request, actor);
      case "/seller/shops" -> {
        request.setAttribute("shops", app.shops.shops(actor));
        request.setAttribute("view", "shops");
      }
      case "/admin/shops" -> {
        request.setAttribute("applications", app.shops.applications(actor, true));
        request.setAttribute("shops", app.shops.shops(actor));
        request.setAttribute("view", "admin-shops");
      }
      case "/seller/shop" -> {
        var shop = app.shops.get(actor, Web.id(request.getParameter("id")));
        request.setAttribute("shop", shop);
        request.setAttribute("members", shop.canManageMembers() ? app.shops.members(actor, shop.id()) : List.of());
        request.setAttribute("view", "shop-manage");
      }
      default -> throw Problem.missing();
    }
  }

  private void applicationPage(HttpServletRequest request, Identity actor) {
    request.setAttribute("applications", app.shops.applications(actor, false));
    request.setAttribute("shopForm", Map.of("name", Objects.toString(request.getParameter("name"), ""),
        "address", Objects.toString(request.getParameter("address"), "")));
    request.setAttribute("view", "shop-application");
  }

  private void error(HttpServletRequest request, HttpServletResponse response, Problem problem) {
    response.setStatus(problem.status);
    request.setAttribute("error", problem.getMessage());
    request.setAttribute("view", "error");
  }
}
