<%@ page contentType="text/html; charset=UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section class="container section auth-narrow"><div class="form-card"><p class="eyebrow">GÓC SÁCH / TÀI KHOẢN</p><h1>Đặt mật khẩu mới</h1><p class="muted">Mã xác minh có hiệu lực 10 phút và chỉ dùng một lần.</p>
<form method="post" action="<c:url value='/auth/reset'/>"><input type="hidden" name="_csrf" value="<c:out value='${csrf}'/>">

<label for="email">Email</label><input id="email" name="email" class="form-control" type="email" required maxlength="254" autocomplete="email" value="<c:out value='${not empty param.email ? param.email : sessionScope.pendingEmail}'/>">
<label for="code">Mã OTP từ email</label><input id="code" name="code" class="form-control otp-input" required pattern="[0-9]{6}" maxlength="6" inputmode="numeric" autocomplete="one-time-code"><label for="password">Mật khẩu ${view eq 'reset'?'mới':''}</label><input id="password" name="password" class="form-control" type="password" minlength="10" required autocomplete="new-password"><label for="confirm">Xác nhận mật khẩu</label><input id="confirm" name="confirm" class="form-control" type="password" minlength="10" required autocomplete="new-password"><small>Ít nhất 10 ký tự, tối đa 72 byte UTF-8.</small><button class="btn btn-forest w-100 mt-4">Xác minh OTP & đổi mật khẩu</button>
</form>
<div class="auth-links"><a href="<c:url value='/auth/login'/>">Về đăng nhập</a><a href="<c:url value='/auth/forgot'/>">Yêu cầu mã mới</a></div></div></section>
