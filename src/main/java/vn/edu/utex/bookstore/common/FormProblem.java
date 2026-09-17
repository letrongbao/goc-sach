package vn.edu.utex.bookstore.common;

/** Validation failure tied to one form field so the browser can explain it in place. */
public final class FormProblem extends Problem {
  public final String field;

  public FormProblem(String field, String message) {
    super(400, message);
    this.field = field;
  }
}
