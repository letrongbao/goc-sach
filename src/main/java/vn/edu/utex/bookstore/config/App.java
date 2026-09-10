package vn.edu.utex.bookstore.config;

import java.time.Clock;
import vn.edu.utex.bookstore.auth.*;
import vn.edu.utex.bookstore.category.*;
import vn.edu.utex.bookstore.image.*;
import vn.edu.utex.bookstore.persistence.*;

public final class App implements AutoCloseable {
  public final Settings settings;
  public final Store store;
  public final AuthService auth;
  public final CategoryService categories;
  public final ImageStorage images;
  public final RateLimiter limits;
  public final OtpService otp;
  public final vn.edu.utex.bookstore.product.ProductService products;
  public final vn.edu.utex.bookstore.shop.ShopService shops;
  public final vn.edu.utex.bookstore.inventory.InventoryService inventory;
  public final vn.edu.utex.bookstore.product.CatalogService catalog;

  public App(Settings settings, Store store, Clock clock) {
    this(settings, store, clock, new vn.edu.utex.bookstore.mail.SmtpEmailSender(settings));
  }

  public App(
      Settings settings, Store store, Clock clock, vn.edu.utex.bookstore.mail.EmailSender sender) {
    this(settings, store, new AuthService(store, new Passwords(), clock),
        new CategoryService(store), new InMemoryImageStorage(),
        new RateLimiter(clock), new OtpService(store, new Passwords(), sender, clock, settings.require("otp.secret")),
        new vn.edu.utex.bookstore.product.ProductService(store, clock),
        new vn.edu.utex.bookstore.shop.ShopService(store, clock),
        new vn.edu.utex.bookstore.inventory.InventoryService(store, clock),
        new vn.edu.utex.bookstore.product.CatalogService(store));
  }

  public App(Settings settings, Store store, AuthService auth, CategoryService categories,
      ImageStorage images, RateLimiter limits, OtpService otp, vn.edu.utex.bookstore.product.ProductService products,
      vn.edu.utex.bookstore.shop.ShopService shops, vn.edu.utex.bookstore.inventory.InventoryService inventory,
      vn.edu.utex.bookstore.product.CatalogService catalog) {
    this.settings = settings;
    this.store = store;
    this.auth = auth;
    this.categories = categories;
    this.images = images;
    this.limits = limits;
    this.otp = otp;
    this.products = products;
    this.shops = shops;
    this.inventory = inventory;
    this.catalog = catalog;
  }

  public void close() {
    store.close();
  }
}
