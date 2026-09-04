package vn.edu.utex.bookstore.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
  @Bean
  org.springframework.web.multipart.support.StandardServletMultipartResolver multipartResolver() {
    return new org.springframework.web.multipart.support.StandardServletMultipartResolver() {
      @Override
      public boolean isMultipart(jakarta.servlet.http.HttpServletRequest request) {
        return request.getDispatcherType() != DispatcherType.ERROR && super.isMultipart(request);
      }
    };
  }

  @Bean
  ServletContextInitializer sessionCookies(App app) {
    return context -> {
      context.getSessionCookieConfig().setHttpOnly(true);
      context.getSessionCookieConfig().setSecure(app.settings.secureCookies());
      context.getSessionCookieConfig().setAttribute("SameSite", "Lax");
    };
  }
}
