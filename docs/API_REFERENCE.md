# API và phân công Góc Sách

Cập nhật 28/10/2026. Phân công đã thống nhất cho ba người; đây là trách nhiệm tiếp nhận, kiểm thử và trình bày, không phải xác nhận tác giả lịch sử Git. Khối lượng cân bằng theo độ khó nghiệp vụ: thanh toán ít URL hơn nhưng có đối soát, khóa và chống lặp.

| Người phụ trách | Phạm vi |
|---|---|
| TV1 — Lê Trọng Bảo — 22110106 | Checkout, đơn buyer/seller, payOS, webhook, trả hàng, ghi nhận hoàn tiền, thông báo đơn |
| TV2 — Nguyễn Kiều Thành — 22110231 | Xác thực, hồ sơ, địa chỉ, catalog/tìm kiếm/top20, giỏ, yêu thích, đã xem, review và media review |
| TV3 — Nguyễn An Khang — 22110160 | Shop, thành viên, duyệt shop, kho, quản trị catalog, voucher, hãng giao, chiết khấu, doanh thu, khóa user, kiểm duyệt |

TV2 bàn giao danh tính/token và giỏ cho TV1; TV3 bàn giao bản sách, voucher/phí cho TV1. TV1 xử lý thay đổi kho do đặt/hủy/trả; TV3 không sửa trực tiếp bản RESERVED/SOLD. Mỗi người chịu trách nhiệm controller, service, dữ liệu, kiểm thử và demo phần mình. Cùng review chỗ giao nhau; không chia tác giả commit giả.

## Địa chỉ và xác thực

Base mặc định: **http://localhost:8080**. Profile demo dùng **http://127.0.0.1:18082**. Nếu cấu hình context-path `/bookstore`, thêm đoạn này trước `/api`. Các URL dưới đây dùng cấu hình mặc định, không khẳng định server đang mở ở port đó.

1. GET `http://localhost:8080/auth/login`, giữ cookie JSESSIONID, lấy hidden `_csrf` trong HTML.
2. POST cùng URL dạng `application/x-www-form-urlencoded`: `_csrf=<token>&login=buyer&password=<mật khẩu>&mode=session`. Thành công 302. Lấy lại CSRF qua GET sau đăng nhập vì token đã xoay.
3. POST `http://localhost:8080/auth/api-token` với cookie phiên và form `_csrf=<token mới>`. Trả 200 `{access_token, token_type:"Bearer", expires_in}`. Cần cấu hình khóa RSA theo SETUP; thiếu khóa trả 503. Không commit khóa.
4. Các API riêng gửi `Authorization: Bearer <access_token>`. Cookie phiên đơn lẻ không đủ cho `/api`. Lấy token mới sẽ thu hồi token trước của phiên; logout/khóa user cũng làm token mất hiệu lực.
5. POST `http://localhost:8080/auth/logout` với cookie + CSRF để kết thúc phiên (302).

Tài khoản/OTP tiếp tục dùng form web có CSRF, không có `/api/login` giả: POST `/auth/register` (username,email,password,confirm), `/auth/activate` (email,code), `/auth/resend` (email), `/auth/forgot` (email), `/auth/reset` (email,code,password,confirm). Các URL đầy đủ là base ở trên cộng path này. Thành công chuyển hướng hoặc render HTML, không hứa JSON; lỗi hiển thị trên form. Gửi OTP có giới hạn tốc độ và thông báo chung tránh dò tài khoản. Demo không gửi email thật.

GET JSON thường trả 200; POST tạo địa chỉ/bản sách/đăng ký shop/voucher/catalog trả 201; thao tác void sửa/xóa trả 204, riêng tạo catalog 201 không body. Checkout trả danh sách đơn 200. POST yêu cầu trả hàng trả đối tượng 200 và dùng lại yêu cầu đã có. Lỗi nghiệp vụ JSON `{error: "..."}`; 400 dữ liệu sai, 401 thiếu/hết hạn token, 403 sai quyền, 404 không có/không thuộc chủ, 409 xung đột trạng thái/version, 429 giới hạn tốc độ. Multipart vượt giới hạn có thể 413; adapter từ chối tệp không hợp lệ 400. Không dựa riêng status HTTP để suy đã nhận tiền.

## Danh sách URL theo thành viên

### Lê Trọng Bảo — 22110106

| HTTP | URL đầy đủ | Quyền | Body |
|---|---|---|---|
| POST | `http://localhost:8080/api/checkout` | Bearer, dữ liệu của mình | OrderApiController.CheckoutRequest |
| GET | `http://localhost:8080/api/checkout/quote` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/orders` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/orders/{id}` | Bearer, dữ liệu của mình | — |
| POST | `http://localhost:8080/api/orders/{id}/cancel` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/orders/{id}/payment` | Bearer, dữ liệu của mình | — |
| POST | `http://localhost:8080/api/orders/{id}/payment/cancel` | Bearer, dữ liệu của mình | — |
| POST | `http://localhost:8080/api/orders/{id}/payment/refresh` | Bearer, dữ liệu của mình | — |
| POST | `http://localhost:8080/api/orders/{id}/payment/start` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/orders/{id}/return` | Bearer, dữ liệu của mình | — |
| POST | `http://localhost:8080/api/orders/{id}/return` | Bearer, dữ liệu của mình | OrderOperationsApiController.ReasonRequest |
| GET | `http://localhost:8080/api/seller/orders` | Quyền theo shop | — |
| GET | `http://localhost:8080/api/seller/orders/{id}` | Quyền theo shop | — |
| POST | `http://localhost:8080/api/seller/orders/{id}/cancel` | Quyền theo shop | OrderOperationsApiController.ReasonRequest |
| POST | `http://localhost:8080/api/seller/orders/{id}/return/resolve` | Quyền theo shop | OrderOperationsApiController.ResolutionRequest |
| POST | `http://localhost:8080/api/seller/orders/{id}/status` | Quyền theo shop | OrderOperationsApiController.StatusRequest |
| GET | `http://localhost:8080/api/seller/shops/{id}/returns` | Quyền theo shop | — |
| POST | `http://localhost:8080/payments/payos/webhook` | Chữ ký payOS | application/json |

### Nguyễn Kiều Thành — 22110231

| HTTP | URL đầy đủ | Quyền | Body |
|---|---|---|---|
| GET | `http://localhost:8080/api/account/addresses` | Bearer, dữ liệu của mình | — |
| POST | `http://localhost:8080/api/account/addresses` | Bearer, dữ liệu của mình | ProfileApiController.AddressRequest |
| DELETE | `http://localhost:8080/api/account/addresses/{id}` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/account/addresses/{id}` | Bearer, dữ liệu của mình | — |
| PUT | `http://localhost:8080/api/account/addresses/{id}` | Bearer, dữ liệu của mình | ProfileApiController.AddressRequest |
| GET | `http://localhost:8080/api/account/profile` | Bearer, dữ liệu của mình | — |
| PUT | `http://localhost:8080/api/account/profile` | Bearer, dữ liệu của mình | ProfileApiController.ProfileRequest |
| GET | `http://localhost:8080/api/cart` | Bearer, dữ liệu của mình | — |
| POST | `http://localhost:8080/api/cart/items` | Bearer, dữ liệu của mình | BuyerApiController.CartRequest |
| DELETE | `http://localhost:8080/api/cart/items/{copyId}` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/categories` | Công khai | — |
| GET | `http://localhost:8080/api/favorites` | Bearer, dữ liệu của mình | — |
| DELETE | `http://localhost:8080/api/favorites/{productId}` | Bearer, dữ liệu của mình | — |
| PUT | `http://localhost:8080/api/favorites/{productId}` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/me` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/products` | Công khai | — |
| GET | `http://localhost:8080/api/products/{id}` | Công khai | — |
| GET | `http://localhost:8080/api/products/{productId}/reviews` | Công khai | — |
| POST | `http://localhost:8080/api/products/{productId}/reviews` | Bearer, dữ liệu của mình | ReviewApiController.ReviewRequest |
| POST | `http://localhost:8080/api/products/{productId}/reviews` | Bearer, dữ liệu của mình | multipart/form-data |
| GET | `http://localhost:8080/api/products/{productId}/reviews/mine` | Bearer, dữ liệu của mình | — |
| PUT | `http://localhost:8080/api/products/{productId}/reviews/mine` | Bearer, dữ liệu của mình | ReviewApiController.ReviewRequest |
| PUT | `http://localhost:8080/api/products/{productId}/reviews/mine` | Bearer, dữ liệu của mình | multipart/form-data |
| GET | `http://localhost:8080/api/rankings/{sort}` | Công khai | — |
| GET | `http://localhost:8080/api/recent-views` | Bearer, dữ liệu của mình | — |
| POST | `http://localhost:8080/api/recent-views/{productId}` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/shops` | Công khai | — |
| GET | `http://localhost:8080/api/shops/{slug}` | Công khai | — |
| POST | `http://localhost:8080/auth/api-token` | Phiên + CSRF | — |

### Nguyễn An Khang — 22110160

| HTTP | URL đầy đủ | Quyền | Body |
|---|---|---|---|
| GET | `http://localhost:8080/api/account/shop-applications` | Bearer, dữ liệu của mình | — |
| POST | `http://localhost:8080/api/account/shop-applications` | Bearer, dữ liệu của mình | ShopApiController.ApplicationRequest |
| GET | `http://localhost:8080/api/admin/carriers` | Admin | — |
| POST | `http://localhost:8080/api/admin/carriers` | Admin | OperationsApiController.CarrierRequest |
| PUT | `http://localhost:8080/api/admin/carriers/{id}` | Admin | OperationsApiController.CarrierRequest |
| GET | `http://localhost:8080/api/admin/categories` | Admin | — |
| POST | `http://localhost:8080/api/admin/categories` | Admin | AdminCatalogApiController.CategoryRequest |
| DELETE | `http://localhost:8080/api/admin/categories/{id}` | Admin | — |
| GET | `http://localhost:8080/api/admin/categories/{id}` | Admin | — |
| PUT | `http://localhost:8080/api/admin/categories/{id}` | Admin | AdminCatalogApiController.CategoryRequest |
| POST | `http://localhost:8080/api/admin/media/images` | Admin | multipart/form-data |
| GET | `http://localhost:8080/api/admin/products` | Admin | — |
| POST | `http://localhost:8080/api/admin/products` | Admin | AdminCatalogApiController.ProductRequest |
| DELETE | `http://localhost:8080/api/admin/products/{id}` | Admin | — |
| GET | `http://localhost:8080/api/admin/products/{id}` | Admin | — |
| PUT | `http://localhost:8080/api/admin/products/{id}` | Admin | AdminCatalogApiController.ProductRequest |
| GET | `http://localhost:8080/api/admin/reviews` | Admin | — |
| PUT | `http://localhost:8080/api/admin/reviews/{id}/visibility` | Admin | OperationsApiController.VisibilityRequest |
| GET | `http://localhost:8080/api/admin/shop-applications` | Admin | — |
| POST | `http://localhost:8080/api/admin/shop-applications/{id}/approve` | Admin | — |
| POST | `http://localhost:8080/api/admin/shop-applications/{id}/reject` | Admin | ShopApiController.ReasonRequest |
| PUT | `http://localhost:8080/api/admin/shops/{id}/commission` | Admin | OperationsApiController.CommissionRequest |
| PUT | `http://localhost:8080/api/admin/shops/{id}/suspension` | Admin | ShopApiController.SuspensionRequest |
| GET | `http://localhost:8080/api/admin/users` | Admin | — |
| PUT | `http://localhost:8080/api/admin/users/{id}/block` | Admin | OperationsApiController.BlockRequest |
| GET | `http://localhost:8080/api/carriers` | Bearer, dữ liệu của mình | — |
| GET | `http://localhost:8080/api/seller/shops` | Quyền theo shop | — |
| GET | `http://localhost:8080/api/seller/shops/{id}` | Quyền theo shop | — |
| GET | `http://localhost:8080/api/seller/shops/{id}/members` | Quyền theo shop | — |
| PUT | `http://localhost:8080/api/seller/shops/{id}/members` | Quyền theo shop | ShopApiController.MemberRequest |
| GET | `http://localhost:8080/api/seller/shops/{id}/revenue` | Quyền theo shop | — |
| GET | `http://localhost:8080/api/seller/shops/{id}/vouchers` | Quyền theo shop | — |
| POST | `http://localhost:8080/api/seller/shops/{id}/vouchers` | Quyền theo shop | OperationsApiController.VoucherRequest |
| GET | `http://localhost:8080/api/seller/shops/{shopId}/copies` | Quyền theo shop | — |
| POST | `http://localhost:8080/api/seller/shops/{shopId}/copies` | Quyền theo shop | InventoryApiController.CopyRequest |
| GET | `http://localhost:8080/api/seller/shops/{shopId}/copies/{id}` | Quyền theo shop | — |
| PUT | `http://localhost:8080/api/seller/shops/{shopId}/copies/{id}` | Quyền theo shop | InventoryApiController.CopyRequest |
| GET | `http://localhost:8080/api/seller/shops/{shopId}/copies/{id}/history` | Quyền theo shop | — |
| POST | `http://localhost:8080/api/seller/vouchers/{code}/disable` | Quyền theo shop | — |

WebSocket TV1: `ws://localhost:8080/ws`, dùng cookie phiên; trên HTTPS dùng `wss`. Server kiểm tra danh tính và trạng thái đơn khoảng 5 giây/lần. Đây không phải REST hoặc STOMP; không có subscribe tùy ý user/shop khác.

## Query và mẫu dữ liệu

- `GET /api/products`: page,q,category,minPrice,maxPrice,condition,shop,sort. Page từ 1. `GET /api/products/{id}` và `/api/shops/{slug}` nhận page.
- `GET /api/rankings/{sort}`: newest,bestselling,favorites,rating; page=1 hoặc 2, tối đa 20 đầu sách.
- `GET /api/products/{productId}/reviews`: page, trả danh sách và header X-Total-Count/X-Total-Pages. `/mine` trả editor của người đã mua.
- `GET /api/checkout/quote`: carrierId (0 mặc định),voucherCode. Trả shops,subtotal,shippingFee,discount,total,fingerprint. POST checkout gửi lại đúng lựa chọn và fingerprint mới.
- `GET /api/orders`: status trống hoặc PENDING,CONFIRMED,SHIPPING,COMPLETED,CANCELLED. `GET /api/seller/orders`: shopId tùy chọn trong phạm vi được phép.
- Kho/admin products: page; admin categories: q. Recent views: limit mặc định20.
- Review JSON: rating1–5, body50–5000 ký tự. Multipart: rating,body,mediaType=image/video,attachment; PUT /mine thêm removeMedia=true để xóa media. Giữ media cũ khi PUT JSON. Ảnh JPEG/PNG/WebP hoặc MP4 tối đa5MB; quyền mua được kiểm tra trước upload. Cloudinary thật chưa được nghiệm thu trong đợt này.
- Upload ảnh catalog: multipart field `file`. URL nhận về dùng cho field image, chỉ chấp nhận URL Cloudinary hợp lệ. Tạo/sửa catalog không nhận giá bán; giá nằm ở copies.
- Kho: condition=LIKE_NEW/GOOD/ACCEPTABLE theo service; status=AVAILABLE/UNAVAILABLE; PUT bắt buộc version đọc từ GET, version cũ trả409.
- Shop membership: role=SELLER/MANAGER, active=true/false; quyền theo shop và cấp vai trò được kiểm tra ở service.
- Giải quyết trả: status=APPROVED/REJECTED/REFUNDED; note và reference khi hoàn tiền. REFUNDED cần settled=true xác nhận đã nhận sách và hoàn tiền bên ngoài; endpoint không chuyển tiền ngân hàng.

### Schema JSON đầu vào

Các tên và kiểu dưới đây lấy trực tiếp từ record request trong controller. Các ID phải tồn tại và thuộc đúng phạm vi; server tự suy người dùng từ token. Số tiền dùng chuỗi thập phân; ngày hết hạn dùng ISO8601 UTC.

- **AdminCatalogApiController.CategoryRequest**: `String name,String image,boolean active`
- **AdminCatalogApiController.ProductRequest**: `long categoryId,String title,String author,String description,String image,String isbn,String publisher,Integer publishedYear,String language`
- **BuyerApiController.CartRequest**: `long copyId`
- **InventoryApiController.CopyRequest**: `long productId,Integer version,String condition,String defects,String price,String status,String reason`
- **OperationsApiController.BlockRequest**: `boolean blocked`
- **OperationsApiController.CarrierRequest**: `String name,String fee,boolean active`
- **OperationsApiController.CommissionRequest**: `String rate`
- **OperationsApiController.VisibilityRequest**: `boolean visible,String note`
- **OperationsApiController.VoucherRequest**: `String code,String amount,String minimumSubtotal,int maximumUses,java.time.Instant expiresAt`
- **OrderApiController.CheckoutRequest**: `long addressId, String idempotencyKey, String quoteFingerprint, String paymentMethod,long carrierId,String voucherCode`
- **OrderOperationsApiController.ReasonRequest**: `String reason`
- **OrderOperationsApiController.ResolutionRequest**: `String status,String note,String reference,boolean settled`
- **OrderOperationsApiController.StatusRequest**: `String status`
- **ProfileApiController.AddressRequest**: `String label,String recipientName,String phone,String line1,String ward,String district,String city,boolean defaultAddress`
- **ProfileApiController.ProfileRequest**: `String displayName,String phone`
- **ReviewApiController.ReviewRequest**: `int rating,String body`
- **ShopApiController.ApplicationRequest**: `String name,String address`
- **ShopApiController.MemberRequest**: `String login,String role,boolean active`
- **ShopApiController.ReasonRequest**: `String reason`
- **ShopApiController.SuspensionRequest**: `boolean suspended,String reason`

### Ví dụ checkout COD

GET `http://localhost:8080/api/checkout/quote?carrierId=1&voucherCode=GOCSACH10`, sau đó:

```http
POST http://localhost:8080/api/checkout
Authorization: Bearer <token>
Content-Type: application/json

{"addressId":1,"idempotencyKey":"demo-api-checkout-0001","quoteFingerprint":"<fingerprint vừa lấy>","paymentMethod":"COD","carrierId":1,"voucherCode":"GOCSACH10"}
```

ID địa chỉ lấy từ `/api/account/addresses`, ID hãng từ `/api/carriers`; không mặc định ID1 thuộc người đang gọi. Đổi sang PAYOS chỉ khi đã cấu hình cổng; `/payment/start` trả checkoutUrl, `/payment/refresh` đối chiếu trạng thái. Webhook POST `/payments/payos/webhook` nhận payload payOS có chữ ký; không dùng JSON `{status:PAID}` để đánh dấu đã trả tiền. Fake gateway chỉ có trong test, không có endpoint thanh toán giả trong JAR.

### Kiểm thử và bằng chứng

`ExpandedApiWebTest` kiểm tra HTTP server thật với RSA tạm và dữ liệu bộ nhớ riêng: catalog công khai, ranh giới Bearer, sai dữ liệu, địa chỉ khác chủ, shop khác quyền, version kho cũ, admin, thu hồi token khi khóa, checkout voucher/phí và gửi lặp, trả hàng/hoàn tiền có xác nhận, review có điều kiện mua. Bộ payment/JWT/WebSocket hiện có tiếp tục kiểm tra chữ ký, callback lặp/đến muộn, hủy/lỗi cổng, CSRF và phiên. Không suy kiểm thử bộ nhớ thành nghiệm thu PostgreSQL hoặc dịch vụ thật mới. Kết quả lần chạy đầy đủ xem FINAL_ACCEPTANCE.md.
