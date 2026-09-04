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
public final class InventoryController {
  private final App app;
  public InventoryController(App app) { this.app = app; }

  @GetMapping({"/seller/inventory", "/seller/copy/add", "/seller/copy/edit"})
  public ModelAndView page(HttpServletRequest req, HttpServletResponse res) {
    try { populate(req, false); }
    catch (Problem p) { error(req, res, p); }
    return Web.result(req, res);
  }

  @PostMapping({"/seller/copy/add", "/seller/copy/edit"})
  public ModelAndView save(HttpServletRequest req, HttpServletResponse res) throws java.io.IOException {
    try {
      Identity actor = (Identity) req.getAttribute("identity");
      long shopId = Web.formId(req.getParameter("shopId"), true);
      long id;
      if (req.getServletPath().endsWith("/add")) {
        id = app.inventory.receive(actor, shopId, Web.formId(req.getParameter("productId"), true),
            req.getParameter("condition"), req.getParameter("defects"), req.getParameter("price"), req.getParameter("reason"));
      } else {
        id = Web.formId(req.getParameter("id"), true);
        int version;
        try { version = Integer.parseInt(req.getParameter("version")); if (version < 0) throw new NumberFormatException(); }
        catch (NumberFormatException e) { throw Problem.invalid("Phiên bản dữ liệu không hợp lệ. Tải lại trang."); }
        app.inventory.update(actor, shopId, id, version, req.getParameter("condition"), req.getParameter("defects"),
            req.getParameter("price"), req.getParameter("status"), req.getParameter("reason"));
      }
      req.getSession().setAttribute("notice", "Đã lưu bản sách và ghi lịch sử kho.");
      Web.redirect(req, res, "/seller/copy/edit?shopId=" + shopId + "&id=" + id);
    } catch (Problem p) {
      error(req, res, p);
      if (p.status == 400 && Web.positiveIdOrNull(req.getParameter("shopId")) != null) {
        try { populate(req, true); }
        catch (Problem inaccessible) { error(req, res, inaccessible); }
      }
    }
    return Web.result(req, res);
  }

  private void populate(HttpServletRequest req, boolean submitted) {
    Identity actor = (Identity) req.getAttribute("identity");
    long shopId = Web.id(req.getParameter("shopId"));
    req.setAttribute("shop", app.shops.get(actor, shopId));
    if (req.getServletPath().equals("/seller/inventory")) {
      req.setAttribute("inventoryPage", app.inventory.page(actor, shopId, req.getParameter("page")));
      req.setAttribute("view", "inventory");
      return;
    }
    boolean editing = req.getServletPath().endsWith("/edit");
    Map<String, Object> form = new HashMap<>();
    form.put("condition", "UNCLASSIFIED"); form.put("status", "UNAVAILABLE");
    form.put("price", ""); form.put("defects", ""); form.put("productId", ""); form.put("reason", "");
    if (editing) {
      long id = Web.id(req.getParameter("id"));
      var copy = app.inventory.get(actor, shopId, id);
      req.setAttribute("copy", copy);
      req.setAttribute("movements", app.inventory.history(actor, shopId, id));
      form.put("id", copy.id()); form.put("version", copy.version()); form.put("condition", copy.condition());
      form.put("status", copy.status()); form.put("price", copy.price()); form.put("defects", copy.defects());
      form.put("productId", copy.productId());
    }
    if (submitted) for (String key : List.of("id", "version", "productId", "condition", "status", "price", "defects", "reason"))
      form.put(key, Objects.toString(req.getParameter(key), ""));
    req.setAttribute("copyForm", form); req.setAttribute("editing", editing);
    req.setAttribute("view", "copy-form");
  }

  private void error(HttpServletRequest req, HttpServletResponse res, Problem p) {
    res.setStatus(p.status); req.setAttribute("error", p.getMessage()); req.setAttribute("view", "error");
  }
}
