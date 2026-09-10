package vn.edu.utex.bookstore.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.config.App;
import vn.edu.utex.bookstore.auth.Identity;
import vn.edu.utex.bookstore.buyer.BuyerService;

@org.springframework.stereotype.Controller
public final class ProductController {
  private final App app;
  private final BuyerService buyer;
  public ProductController(App app, BuyerService buyer) { this.app = app; this.buyer = buyer; }

  @org.springframework.web.bind.annotation.RequestMapping(value = {"/product", "/product/detail", "/admin/products", "/admin/product/add", "/admin/product/edit", "/admin/product/save", "/admin/product/delete"}, method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
  public org.springframework.web.servlet.ModelAndView handle(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    if ("POST".equals(req.getMethod())) doPost(req, res); else doGet(req, res);
    return Web.result(req, res);
  }

  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    dispatch(req, res, false);
  }

  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    dispatch(req, res, true);
  }

  private void dispatch(HttpServletRequest req, HttpServletResponse res, boolean post)
      throws ServletException, IOException {
    String path = req.getServletPath();
    try {
      if (!post && (path.equals("/product") || path.equals("/admin/products"))) {
        req.setAttribute("catalog", path.startsWith("/admin/") ? app.products.page(req.getParameter("page"))
            : app.catalog.page(req.getParameter("page"), req.getParameter("q")));
        req.setAttribute("adminView", path.startsWith("/admin/"));
        Web.view(req, res, "products");
        return;
      }
      if (!post && path.equals("/product/detail")) {
        var detail = app.catalog.detail(Web.id(req.getParameter("id")), req.getParameter("offerPage"));
        req.setAttribute("product", detail.product());
        req.setAttribute("offers", detail.offers());
        req.setAttribute("offerPage", detail.number());
        req.setAttribute("offerPages", detail.pages());
        Identity identity = (Identity) req.getAttribute("identity");
        if (identity != null) buyer.recordViewed(identity.id(), detail.product().id());
        Web.view(req, res, "product-detail");
        return;
      }
      if (!post && (path.equals("/admin/product/add") || path.equals("/admin/product/edit"))) {
        if (path.endsWith("/edit")) {
          var product = app.products.get(Web.id(req.getParameter("id")));
          req.setAttribute("product", product);
          req.setAttribute("selectedCategoryId", product.categoryId());
        } else req.setAttribute("product", Web.emptyProduct());
        req.setAttribute("categories", app.categories.list(""));
        Web.view(req, res, "product-form");
        return;
      }
      if (post && path.equals("/admin/product/save")) {
        Long id = Web.formId(req.getParameter("id"), false);
        long categoryId = Web.formId(req.getParameter("categoryId"), true);
        String image;
        Part file =
            req.getContentType() != null && req.getContentType().startsWith("multipart/form-data")
                ? req.getPart("upload")
                : null;
        if (file != null && file.getSize() > 0)
          try (var input = file.getInputStream()) {
            image = app.images.store(input, file.getSize());
          }
        else image = existingImage(id);
        app.products.saveDetails(
            id,
            categoryId,
            req.getParameter("title"),
            req.getParameter("author"),
            req.getParameter("description"), image,
            blank(req.getParameter("isbn")), blank(req.getParameter("publisher")),
            year(req.getParameter("publishedYear")), blank(req.getParameter("language")));
        Web.redirect(req, res, "/admin/products");
        return;
      }
      if (post && path.equals("/admin/product/delete")) {
        app.products.delete(Web.formId(req.getParameter("id"), true));
        Web.redirect(req, res, "/admin/products");
        return;
      }
      throw Problem.missing();
    } catch (Problem e) {
      res.setStatus(e.status);
      req.setAttribute("error", e.getMessage());
      if (post && path.equals("/admin/product/save")) {
        Map<String, String> form = new HashMap<>();
        for (String key :
            List.of(
                "id", "categoryId", "title", "author", "description", "image", "isbn", "publisher", "publishedYear", "language"))
          form.put(key, Objects.toString(req.getParameter(key), ""));
        req.setAttribute("product", form);
        req.setAttribute(
            "selectedCategoryId", Web.positiveIdOrNull(req.getParameter("categoryId")));
        req.setAttribute("categories", app.categories.list(""));
        Web.view(req, res, "product-form");
      } else Web.view(req, res, "error");
    }
  }

  private static String blank(String value) { return value == null ? null : value.trim(); }
  /** Existing media is read from the database; clients cannot inject an image URL. */
  private String existingImage(Long id) {
    if (id == null) return "";
    var current = app.products.get(id);
    return current.image() == null ? "" : current.image();
  }
  private static Integer year(String value) { if (value == null || value.isBlank()) return null; try { return Integer.valueOf(value); } catch (NumberFormatException e) { throw Problem.invalid("Năm xuất bản không hợp lệ."); } }
}
