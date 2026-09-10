package vn.edu.utex.bookstore.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import vn.edu.utex.bookstore.common.Problem;

public final class Web {
  private Web() {}

  public static long id(String raw) {
    try {
      long id = Long.parseLong(raw);
      if (id <= 0) throw Problem.missing();
      return id;
    } catch (NumberFormatException e) {
      throw Problem.missing();
    }
  }

  public static Long optionalId(String raw) {
    return raw == null || raw.isBlank() ? null : id(raw);
  }

  public static Long positiveIdOrNull(String raw) {
    if (raw == null || !raw.matches("[0-9]{1,19}")) return null;
    try {
      long value = Long.parseLong(raw);
      return value > 0 ? value : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static Long formId(String raw, boolean required) {
    if (!required && (raw == null || raw.isBlank())) return null;
    Long value = positiveIdOrNull(raw);
    if (value == null)
      throw Problem.invalid("Mã dữ liệu phải là số nguyên dương. Hãy kiểm tra lại biểu mẫu.");
    return value;
  }

  public static void view(HttpServletRequest req, HttpServletResponse res, String view)
      throws ServletException, IOException {
    req.setAttribute("view", view);

  }

  public static java.util.Map<String, Object> emptyProduct() {
    var values = new java.util.HashMap<String, Object>();
    for (String key : java.util.List.of("id", "categoryId", "title", "author", "description", "image", "isbn", "publisher", "publishedYear", "language"))
      values.put(key, "");
    return values;
  }

  public static org.springframework.web.servlet.ModelAndView result(HttpServletRequest req, HttpServletResponse res) {
    if (req.getAttribute("view") == null || res.isCommitted()) return null;
    var model = new java.util.HashMap<String, Object>();
    var names = req.getAttributeNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (!name.startsWith("org.springframework")) model.put(name, req.getAttribute(name));
    }
    var form = new java.util.HashMap<String, String>();
    for (String key : java.util.List.of("email", "login", "username", "q"))
      form.put(key, java.util.Objects.toString(req.getParameter(key), ""));
    model.put("form", form);
    model.put("pendingEmail", req.getSession().getAttribute("pendingEmail"));
    model.put("preview", Boolean.TRUE.equals(req.getServletContext().getAttribute("preview")));
    model.put("selectedCategoryId", req.getAttribute("selectedCategoryId"));
    return new org.springframework.web.servlet.ModelAndView("layout", model);
  }

  public static void redirect(HttpServletRequest req, HttpServletResponse res, String route)
      throws IOException {
    res.sendRedirect(req.getContextPath() + route);
  }
}
