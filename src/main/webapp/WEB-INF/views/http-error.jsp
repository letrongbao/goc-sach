<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Thông báo · Góc Sách</title>
<link rel="stylesheet" href="<c:url value='/assets/bootstrap.min.css'/>"><link rel="stylesheet" href="<c:url value='/assets/app.css'/>"><link rel="stylesheet" href="<c:url value='/assets/typography.css'/>">
<link rel="stylesheet" href="<c:url value='/assets/responsive.css'/>">
</head><body>
<c:if test="${applicationScope.preview}"><div class="alert alert-warning mb-0 text-center">BẢN XEM GIAO DIỆN · Dữ liệu giả trong bộ nhớ · Chưa kết nối PostgreSQL/Gmail</div></c:if>
<header class="site-header"><a class="brand" href="<c:url value='/'/>">góc<span>sách.</span></a></header>
<main class="container section auth-narrow"><section class="form-card" aria-labelledby="error-title">
<p class="eyebrow">GÓC SÁCH / THÔNG BÁO</p><h1 id="error-title"><c:out value="${statusCode}"/> — Chưa thể tiếp tục</h1>
<p class="muted"><c:out value="${safeMessage}"/></p>
<div class="d-flex flex-wrap gap-2 mt-4"><a class="btn btn-forest" href="<c:url value='/'/>">Về trang chủ</a><a class="btn btn-outline-dark" href="<c:url value='/auth/login'/>">Đăng nhập</a></div>
</section></main></body></html>
