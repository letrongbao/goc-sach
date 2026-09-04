<%@ page contentType="text/html; charset=UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<section class="container section auth-narrow"><div class="form-card"><p class="eyebrow">GÓC SÁCH / TÀI KHOẢN</p><h1>Quên mật khẩu?</h1><p class="muted">Mã xác minh có hiệu lực 10 phút và chỉ dùng một lần.</p>
<form method="post" action="<c:url value='/auth/forgot'/>"><input type="hidden" name="_csrf" value="<c:out value='${csrf}'/>">

<label for="email">Email</label><input id="email" name="email" class="form-control" type="email" required maxlength="254" autocomplete="email" value="<c:out value='${not empty param.email ? param.email : sessionScope.pendingEmail}'/>">
<button class="btn btn-forest w-100 mt-4">Gửi mã đặt lại mật khẩu</button>
</form>
<div class="auth-links"><a href="<c:url value='/auth/login'/>">Về đăng nhập</a></div></div></section>
