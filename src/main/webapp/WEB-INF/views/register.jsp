<%@ page contentType="text/html; charset=UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section class="container section auth-narrow"><div class="form-card"><p class="eyebrow">GÓC SÁCH / TÀI KHOẢN</p><h1>Tạo tài khoản</h1><p class="muted">Mã xác minh có hiệu lực 10 phút và chỉ dùng một lần.</p>
<form method="post" action="<c:url value='/auth/register'/>"><input type="hidden" name="_csrf" value="<c:out value='${csrf}'/>">
<label for="username">Tên đăng nhập</label><input id="username" name="username" class="form-control" required minlength="3" maxlength="50" autocomplete="username" value="<c:out value='${param.username}'/>">
<label for="email">Email</label><input id="email" name="email" class="form-control" type="email" required maxlength="254" autocomplete="email" value="<c:out value='${not empty param.email ? param.email : sessionScope.pendingEmail}'/>">
<label for="password">Mật khẩu ${view eq 'reset'?'mới':''}</label><input id="password" name="password" class="form-control" type="password" minlength="10" required autocomplete="new-password"><label for="confirm">Xác nhận mật khẩu</label><input id="confirm" name="confirm" class="form-control" type="password" minlength="10" required autocomplete="new-password"><small>Ít nhất 10 ký tự, tối đa 72 byte UTF-8.</small><button class="btn btn-forest w-100 mt-4">Đăng ký & nhận OTP</button>
</form>
<div class="auth-links"><a href="<c:url value='/auth/login'/>">Về đăng nhập</a></div></div></section>
