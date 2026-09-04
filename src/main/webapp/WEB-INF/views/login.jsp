<%@ page contentType="text/html; charset=UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section class="auth-layout"><div class="auth-story"><p class="eyebrow">GÓC SÁCH / TÀI KHOẢN</p><h1>Chào bạn,<br>người đọc <em>thân quen.</em></h1><p>Đăng nhập để tiếp tục câu chuyện của bạn.</p><blockquote>“Một trang sách mở ra,<br>một chân trời ở lại.”</blockquote></div>
<div class="form-card"><p class="eyebrow">RẤT VUI ĐƯỢC GẶP LẠI BẠN</p><h2>Đăng nhập</h2>
<form method="post" action="<c:url value='/auth/login'/>">
<input type="hidden" name="_csrf" value="<c:out value='${csrf}'/>">
<label for="login">Tên đăng nhập hoặc email</label><input class="form-control" id="login" name="login" value="<c:out value='${param.login}'/>" required maxlength="254" autocomplete="username">
<label for="password">Mật khẩu</label><input class="form-control" id="password" name="password" type="password" required autocomplete="current-password">
<fieldset class="mode-options"><legend>Cách duy trì đăng nhập</legend><label><input type="radio" name="mode" value="session" checked> Session <small>Phiên làm việc hiện tại</small></label><label><input type="radio" name="mode" value="cookie"> Cookie <small>Ghi nhớ 7 ngày trên trình duyệt</small></label></fieldset>
<button class="btn btn-forest w-100" type="submit">Đăng nhập ↗</button></form><div class="auth-links"><a href="<c:url value='/auth/register'/>">Tạo tài khoản</a><a href="<c:url value='/auth/forgot'/>">Quên mật khẩu?</a></div><a class="form-note" href="<c:url value='/auth/activate'/>">Chưa kích hoạt? Nhập hoặc gửi lại OTP.</a><p class="form-note">Cookie chỉ lưu token ngẫu nhiên, không lưu mật khẩu của bạn.</p></div></section>
