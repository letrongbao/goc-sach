package vn.edu.utex.bookstore.web;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.Duration;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.common.Problem;
import vn.edu.utex.bookstore.config.App;

@org.springframework.stereotype.Controller
public final class OtpController {
  private final App app;
  public OtpController(App app) { this.app = app; }

  @org.springframework.web.bind.annotation.RequestMapping(value = {"/auth/register", "/auth/activate", "/auth/forgot", "/auth/reset", "/auth/resend"}, method = {org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST})
  public org.springframework.web.servlet.ModelAndView handle(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    if ("POST".equals(req.getMethod())) doPost(req, res); else doGet(req, res);
    return Web.result(req, res);
  }

  protected void doGet(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    String view =
        switch (req.getServletPath()) {
          case "/auth/register" -> "register";
          case "/auth/activate" -> "activate";
          case "/auth/forgot" -> "forgot";
          case "/auth/reset" -> "reset";
          default -> null;
        };
    if (view == null) {
      res.sendError(405);
      return;
    }
    Web.view(req, res, view);
  }

  protected void doPost(HttpServletRequest req, HttpServletResponse res)
      throws ServletException, IOException {
    String path = req.getServletPath();
    String email = req.getParameter("email");
    String view =
        switch (path) {
          case "/auth/register" -> "register";
          case "/auth/activate", "/auth/resend" -> "activate";
          case "/auth/forgot" -> "forgot";
          default -> "reset";
        };
    try {
      boolean sending =
          path.equals("/auth/register")
              || path.equals("/auth/resend")
              || path.equals("/auth/forgot");
      String group = sending ? "otp-send:" : "otp-verify:";
      app.limits.check(
          group + "ip:" + req.getRemoteAddr(), sending ? 10 : 30, Duration.ofMinutes(15));
      app.limits.check(
          group + "account:" + Tokens.digest(AuthService.normalize(email)),
          sending ? 3 : 10,
          Duration.ofMinutes(15));
      switch (path) {
        case "/auth/register" -> {
          app.otp.register(
              req.getParameter("username"),
              email,
              req.getParameter("password"),
              req.getParameter("confirm"));
          req.getSession().setAttribute("pendingEmail", email);
          req.getSession().setAttribute("notice", OtpService.SENT_MESSAGE);
          Web.redirect(req, res, "/auth/activate");
        }
        case "/auth/resend" -> {
          issuePublic(app, email, OtpService.ACTIVATE);
          req.setAttribute("notice", OtpService.SENT_MESSAGE);
          Web.view(req, res, "activate");
        }
        case "/auth/activate" -> {
          app.otp.activate(email, req.getParameter("code"));
          req.getSession().setAttribute("notice", "Tài khoản đã kích hoạt. Bạn có thể đăng nhập.");
          Web.redirect(req, res, "/auth/login");
        }
        case "/auth/forgot" -> {
          issuePublic(app, email, OtpService.RESET);
          req.getSession().setAttribute("pendingEmail", email);
          req.getSession().setAttribute("notice", OtpService.SENT_MESSAGE);
          Web.redirect(req, res, "/auth/reset");
        }
        case "/auth/reset" -> {
          app.otp.reset(
              email,
              req.getParameter("code"),
              req.getParameter("password"),
              req.getParameter("confirm"));
          req.getSession().invalidate();
          SecurityFilter.cookie(req, res, null, app.settings.secureCookies());
          req.getSession()
              .setAttribute(
                  "notice", "Đã đặt lại mật khẩu và vô hiệu hóa các phiên cũ. Hãy đăng nhập lại.");
          Web.redirect(req, res, "/auth/login");
        }
        default -> res.sendError(405);
      }
    } catch (Problem e) {
      res.setStatus(e.status);
      req.setAttribute("error", e.getMessage());
      Web.view(req, res, view);
    }
  }

  private void issuePublic(App app, String email, String purpose) {
    try {
      app.otp.issue(email, purpose);
    } catch (OtpDeliveryFailure e) {
      // Do not log the recipient, OTP or provider exception.
      org.slf4j.LoggerFactory.getLogger(OtpController.class).warn("OTP delivery failed; inspect SMTP configuration.");
    }
  }
}
