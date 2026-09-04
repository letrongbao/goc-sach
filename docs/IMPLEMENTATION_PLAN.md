# Kế hoạch triển khai chi tiết

Ngày 04/09/2026. Phục vụ phát triển, không phải tài liệu nộp báo cáo. Bản dễ đọc và thứ tự mốc ở [ROADMAP.md](ROADMAP.md); yêu cầu chính thức ở [REQUIREMENTS.md](REQUIREMENTS.md).

Các mục dưới đây là thiết kế ban đầu; hiện trạng triển khai/review/test cập nhật riêng tại [MILESTONES.md](MILESTONES.md). Bảng điểm xuất phát bên dưới giữ nguyên để đối chiếu, không phải trạng thái code mới nhất. Không chạy SQL, tạo tài khoản dịch vụ, gửi email, publish hay push chỉ vì tài liệu có liệt kê.

## 1. Điểm xuất phát đã kiểm tra

| Vị trí trong source | Điều đã thấy | Việc cần thay đổi |
|---|---|---|
| `pom.xml` | Boot 3.5.16, Java 21, Thymeleaf, Hibernate, PostgreSQL; chưa có Security/WebSocket/Cloudinary | Bổ sung dependency theo từng mốc, giữ phiên bản do Boot quản lý khi có |
| `config/App.java` | Tự tạo service bằng `new`; trường ảnh mang kiểu `LocalImageStorage` | Chuyển sang bean/constructor injection; phụ thuộc interface ảnh |
| `persistence/JpaStore.java` | Tự tạo EMF; mỗi `tx()` mở EntityManager và transaction mới | Một EMF do Spring quản lý; transaction của cùng nghiệp vụ phải dùng chung |
| `web/SecurityFilter.java` | Tự xử lý session, remember token, CSRF và ADMIN | Thay bằng Spring Security, giữ các bảo vệ hiện có và test hồi quy |
| `auth/User.java`, `sql/schema.sql` | Một role ADMIN/USER, active, auth_version | Thêm phân quyền theo shop; tách chờ kích hoạt với bị khóa |
| `product/ProductService.java` | 10 mới nhất, 6/trang, xóa vật lý; giá/tồn đặt tại product | Thêm truy vấn theo yêu cầu cuối kỳ, tồn từng bản/shop, ngừng bán thay xóa lịch sử |
| `image/ImageStorage.java` | Chỉ trả chuỗi tham chiếu, chưa có quản lý/xóa tài sản | Kiểu media có provider/id/chủ sở hữu; bổ sung adapter Cloudinary |
| `META-INF/orm.xml` và 5 bảng hiện tại | Mapping JPA XML, schema validate | Giữ ID/dữ liệu; mở rộng từng bước, không create/update schema tự động |

Không dùng kết quả test mock để khẳng định dữ liệu thật đã đúng. Nghiệm thu nền tảng PostgreSQL/Gmail là đầu việc đầu tiên của M1.

## 2. Kiến trúc và cách nâng nền tảng

Giữ một ứng dụng, một PostgreSQL, đóng gói JAR. Chia module theo chức năng: auth, catalog, shop, inventory, customer, cart, order, payment, promotion, review, notification, reporting. Trong mỗi module giữ controller → service → repository. Không tách microservices.

- Controller nhận form/JSON và trả DTO; Thymeleaf không truy cập entity lazy hoặc repository.
- Các service nhận user ID/quyền đã xác thực, không nhận HttpSession/HttpServletRequest.
- Giữ interface gửi email, media và thời gian để test. Cấu hình một chỗ; secret ngoài Git.
- Đưa JPA sang EMF/DataSource/JpaTransactionManager do Spring quản lý. Chỉ bỏ cấu hình loại trừ auto-config và EMF cũ khi bean thay thế đã được kiểm thử.
- Giữ API `Store.tx()` ở bước đầu nhưng triển khai bằng TransactionTemplate, propagation REQUIRED, dùng EntityManager gắn với transaction. Lời gọi lồng nhau không tự commit một phần.
- EntityManager/JPQL vẫn đáp ứng JPA; chưa cần chuyển toàn bộ repository sang Spring Data. Có thể giữ mapping XML và thêm mapping module mới có kiểm soát.
- Không chỉ dán `@Transactional` vào các object tự `new` hoặc phương thức tự gọi trong cùng object. Khi dùng annotation, phải là bean được gọi qua proxy. [Tài liệu Spring về transaction](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html).
- Giữ `ddl-auto=validate`, `spring.sql.init.mode=never`, tắt open-in-view. Không dùng migration runner hay init SQL trong Compose/test.
- Sau khi đổi transaction, test một nghiệp vụ ghi nhiều bảng rồi cố ý lỗi ở cuối: tất cả phải rollback. OTP nhập sai vẫn phải lưu số lần sai; không vô tình rollback bộ đếm khi chuyển cấu trúc xử lý lỗi.

Thư viện dự kiến: starter-security, starter-oauth2-resource-server, starter-websocket, starter-validation, spring-security-messaging, spring-security-test, Thymeleaf extras tương thích Security 6 và Cloudinary Java SDK `cloudinary-http5`. Dùng Boot BOM cho phần Spring; kiểm tra tương thích và khóa version SDK bên ngoài khi thực sự thêm. Không thêm thư viện JSP hoặc Cloudinary taglib.

## 3. Vai trò và cửa hàng

| Vai trò | Phạm vi được phép |
|---|---|
| Guest | Xem/tìm/lọc sách, trang shop, đăng ký/đăng nhập |
| User | Hồ sơ, địa chỉ, giỏ, đơn của mình, yêu thích, đã xem, đánh giá sau mua |
| Seller | Quyền User; gửi yêu cầu mở shop; quản lý sách, đơn, khuyến mãi và doanh thu shop đã được duyệt |
| Manager | Quản lý hoạt động, thành viên và dữ liệu các shop được giao; không nâng quyền toàn hệ thống |
| Admin | Duyệt/khóa shop, quản lý user/role toàn hệ thống, danh mục chung, vận chuyển, chiết khấu và báo cáo |
| Shipper — thêm | Chỉ đơn được phân công, không xem toàn bộ thông tin khách/shop |

Thiết kế `shop_applications` có PENDING/APPROVED/REJECTED; duyệt một lần trong transaction, không tạo hai shop khi bấm lặp. `shop_memberships` ràng buộc user/shop/role và trạng thái; một người có thể tham gia nhiều shop. Giữ ADMIN/USER toàn hệ thống, vai trò cửa hàng xác định qua membership.

Mọi truy vấn sản phẩm, đơn, media và doanh thu của Seller/Manager đều kiểm tra shop hiện hành ở server. Không tin shopId/role trong form hoặc chỉ ẩn nút ở Thymeleaf. Thay đổi membership/khóa shop có hiệu lực ngay với request mới và kết nối realtime liên quan.

Manager được quản lý khách hàng có quan hệ với shop ở mức cần thiết, không đọc hồ sơ toàn hệ thống hay tự cấp ADMIN. Việc cho Manager sửa chiết khấu và nhà vận chuyển áp dụng theo phạm vi shop; cần thầy xác nhận vì mô tả quyền khá rộng.

## 4. Spring Security và JWT có chức năng thực tế

Đề xuất giữ session cho các trang Thymeleaf và dùng JWT cho API JSON mà giao diện thực sự gọi, không đổi website thành SPA.

- Web: form login, BCrypt tương thích hash cũ, session fixation protection, remember-me có token lưu băm, CSRF cho mọi thao tác dùng cookie. Thay filter cũ trong một mốc, không để hai nơi xác thực khác nhau.
- API `/api/**`: chỉ nhận `Authorization: Bearer`, không fallback về cookie/session; trả 401/403 JSON. JWT dùng cho thêm/sửa giỏ, yêu thích và đọc thông báo từ UI.
- `POST /auth/api-token`: phiên web còn hợp lệ + CSRF mới cấp token. JavaScript giữ access token trong bộ nhớ, không localStorage, URL hoặc log. Hết hạn thì xin token mới qua phiên; phiên hết hạn thì đăng nhập lại.
- Bản đầu không cần refresh token độc lập: phiên/remember-me đã là cơ chế duy trì đăng nhập. Khác với kế hoạch sơ bộ, tránh duy trì thêm một vòng đời refresh song song chưa có client cần dùng. Nếu sau này có mobile/client độc lập mới thiết kế refresh rotation và phát hiện dùng lại.
- JWT đề xuất RS256, hạn 10 phút; kiểm tra chữ ký, thuật toán cho phép, issuer, audience, exp/nbf, user active và auth_version. Có `sid` liên kết bản ghi cấp quyền API theo phiên để logout thu hồi token ngay, không phải chờ 10 phút.
- Khóa ký lưu ngoài source, có key ID và cách xoay khóa; không dùng lại otp.secret để ký JWT.
- Dùng JwtEncoder/JwtDecoder và hỗ trợ Resource Server của Spring thay vì tự viết parser xác minh JWT. [Spring Security JWT](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).
- Quyền shop lấy theo dữ liệu hiện hành, không coi danh sách shop trong token là quyền vĩnh viễn. Đổi mật khẩu/khóa tài khoản tăng auth_version, thu hồi remember token, API grants và chặn phiên cũ.
- Chỉ bỏ CSRF trên API thật sự chỉ dùng Bearer; `/auth/api-token`, form web và thao tác cookie vẫn bảo vệ. Không bật CORS mọi domain.
- Tách tài khoản chưa kích hoạt và bị quản trị khóa: gửi OTP kích hoạt không được mở lại tài khoản đã bị khóa.

Ca chốt: JWT giả/sai audience/hết hạn bị 401; thiếu CSRF bị 403; user gọi admin bị 403; shop A sửa shop B thất bại; sau reset/logout/khóa, token cũ không dùng được. Giao diện phải gọi API thành công, không chỉ demo bằng công cụ ngoài.

## 5. Dữ liệu sách cũ và lộ trình giữ dữ liệu cũ

Đề xuất mỗi bản sách vật lý có mã riêng. `products` tiếp tục là đầu sách, giữ ID/đường dẫn chi tiết. Thêm `book_copies` chứa product_id, shop_id, mã bản sách, tình trạng, mô tả khuyết điểm, giá, trạng thái và version.

Trạng thái bản sách: AVAILABLE, RESERVED, SOLD, UNAVAILABLE. Đầu sách có nhà xuất bản, năm xuất bản, ISBN nếu có, ngôn ngữ; ISBN không bắt buộc với sách cũ. Giá/tồn hiển thị được tính từ những bản có thể bán; không cập nhật đồng thời hai nguồn tồn độc lập.

Các nhóm bảng dự kiến, chỉ tạo khi tới mốc:

| Mốc | Nhóm dữ liệu |
|---|---|
| M1 | Trạng thái tài khoản, API grants; mở rộng quyền nếu cần |
| M2 | Shops, applications, memberships, book_copies, inventory_movements; mở rộng products |
| M3 | Media assets và liên kết ảnh sản phẩm/shop |
| M4 | Profiles/addresses, carts/items, favorites, recently_viewed |
| M5 | Checkout groups, orders/items, status history, inventory reservations, carriers/rates |
| M6 | Payment attempts/events, return requests/items, refunds, promotions, coupon redemptions, commission snapshots |
| M7 | Reviews/media, notifications, outbox events |
| Xuyên suốt | Nhật ký quản trị và thay đổi trạng thái quan trọng |

Quy trình chuyển dữ liệu: backup DB riêng → SQL bổ sung để người dùng duyệt → chạy trên DB kiểm thử được chuẩn bị thủ công → đối chiếu ID/số lượng/tổng tồn → chuyển code đọc dữ liệu mới → chỉ dọn cột cũ sau nghiệm thu và cho phép riêng.

Đối với product cũ có stock N, chuẩn bị ánh xạ N bản sách vào shop khởi tạo được chọn, giữ product ID và giá cũ. Tình trạng chưa biết phải là “chưa phân loại”, không tự khẳng định như mới; chỉ mở bán sau phân loại. Không tự gán dữ liệu thật vào một shop tùy tiện.

Khóa ngoại bảo vệ dữ liệu; tiền dùng BigDecimal/NUMERIC, lưu thời gian UTC, hiển thị giờ Việt Nam. Ràng buộc unique cho favorites, membership và mã giao dịch; index theo shop/trạng thái/thời gian. Xóa đầu sách/shop có lịch sử bằng ngừng hoạt động, không cascade xóa đơn.

## 6. Cloudinary và giao diện

M3 dùng Cloudinary thật cho ảnh bìa/ảnh tình trạng, avatar và logo shop; M7 mở rộng ảnh/video đánh giá.

- Refactor App để nhận interface media. Metadata lưu provider, asset ID/public ID, resource type, URL HTTPS, owner, shop, trạng thái; không chỉ một chuỗi URL.
- Upload qua backend có xác thực/kiểm tra quyền, SDK ký request; secret không ra browser. Cấu hình cloud_name, api_key, api_secret ngoài Git, secure=true. [Cloudinary Java SDK](https://cloudinary.com/documentation/java_integration).
- Đề xuất ảnh JPEG/PNG/WebP tối đa 5 MB; bình luận tối đa 3 ảnh và 1 video MP4 20 MB/30 giây. Đây là giới hạn sản phẩm đề xuất, cần kiểm tra quota và khả năng kiểm định thực tế khi làm; không chỉ tin đuôi file/MIME do client gửi.
- Asset lỗi/chưa kiểm định không được publish. Giới hạn số pixel, loại nội dung, dung lượng và thời gian xử lý; rate limit theo tài khoản.
- Upload thành công nhưng DB thất bại: đánh dấu tài sản mồ côi và dọn có kiểm soát. Thay ảnh phải gắn ảnh mới trước, ảnh cũ chỉ xóa khi không còn tham chiếu và đúng chủ sở hữu.
- Xóa Cloudinary dùng ID/resource type đã lưu, không nhận tùy ý public ID từ client. Phải tính cả cache CDN và khả năng khôi phục trước khi xóa thật. [Upload API](https://cloudinary.com/documentation/image_upload_api_reference).
- Không tự tải URL tùy ý từ internet để nhập ảnh; giữ tham chiếu legacy trong giai đoạn chuyển và nhập lại ảnh có quyền sử dụng.
- Thumbnail đúng kích thước, ảnh dự phòng, lazy loading; không dùng AI tạo ảnh minh chứng sản phẩm thật.

Giữ thiết kế Góc Sách hiện tại, Thymeleaf fragments/layout, Bootstrap responsive. Bổ sung sidebar riêng cho shop/admin, breadcrumbs, lỗi tại form, trạng thái rỗng/loading, xác nhận thao tác quan trọng. Bàn phím và nhãn form dùng được ở 320/390/768/1366px. Ghi nguồn và giấy phép template/ảnh.

Catalog: top 20 từng nhóm mới/bán chạy/đánh giá/yêu thích; tạm phân trang 10 cho từng nhóm. Danh sách tất cả dùng page size 20 cấu hình riêng. Quy tắc top 20 là giả định cần xác nhận, không tự đổi yêu cầu thành 20/trang.

Bán chạy dựa số lượng giao thành công trừ số trả đã chấp nhận; đánh giá dựa đánh giá hợp lệ, yêu thích dựa user duy nhất, mới dựa created_at; có ID làm khóa phụ ổn định. Sách chưa có đánh giá không giả điểm cao. Shop khóa/sách ngừng bán không vào danh sách mua mới.

## 7. Giỏ, đơn hàng, khuyến mãi và tiền

Giỏ DB theo user, dòng giỏ tham chiếu bản sách đã chọn. Không giữ tồn chỉ vì thêm vào giỏ. Kiểm tra lại trạng thái, giá, vận chuyển và voucher khi checkout. Địa chỉ trong đơn là snapshot, không thay theo việc sửa profile.

Checkout nhiều shop: tạo checkout group và đơn con từng shop trong một transaction, idempotency key theo user + lần checkout. Đề xuất thanh toán riêng mỗi đơn con để không cần chia một giao dịch cổng thanh toán cho nhiều shop. UI nêu rõ các đơn/phí cần thanh toán, cho thử lại đơn chưa thanh toán.

- Khóa các bản sách theo thứ tự ID cố định, kiểm tra còn bán, giữ tồn và tạo đơn/chi tiết/lịch sử trong cùng transaction. Dùng pessimistic lock hoặc cập nhật có điều kiện và kiểm tra số dòng; test trên PostgreSQL thật. [PostgreSQL row locks](https://www.postgresql.org/docs/17/explicit-locking.html).
- COD tạo đơn và giữ sách; hết hạn xác nhận/hủy mới trả tồn. Đơn online giữ tạm, đề xuất 15 phút; tác vụ giải phóng chạy lặp an toàn, có thể chạy lại sau restart.
- Callback đến sau khi hết giữ tồn phải xử lý bù: không đánh dấu đã bán sách nay thuộc đơn khác; đưa khoản tiền vào trạng thái cần đối soát/hoàn tiền.
- Mỗi thay đổi tồn có inventory movement và lý do; không sửa số tồn trực tiếp mà mất lịch sử.
- Phân biệt trạng thái đơn với trạng thái thanh toán. Đơn: NEW → CONFIRMED → PICKED_UP → SHIPPING → DELIVERED; nhánh CANCELLED và RETURN_REQUESTED → RETURNED. Thanh toán: UNPAID/PENDING/PAID/FAILED/REFUND_PENDING/REFUNDED.
- Khách hủy trước xác nhận; sau đó gửi yêu cầu. Seller/Manager chỉ chuyển trạng thái theo quyền. Hoàn hàng phải nhận và kiểm tra tình trạng trước khi cho bán lại; trả tiền và nhập lại kho là hai bước riêng.
- Payment attempts có mã riêng, không dùng lại mã cũ cho nhiều lần thanh toán; callback có unique event/transaction và khóa chống xử lý lặp.
- VNPAY: kiểm chữ ký, merchant, số tiền, mã đơn và kết quả giao dịch; IPN server-to-server là nơi cập nhật thanh toán, ReturnURL chỉ hiển thị. Callback cần endpoint HTTPS truy cập được từ cổng; cấu hình môi trường demo riêng. [Hướng dẫn VNPAY](https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html).
- Test sandbox: thành công, thất bại, người dùng đóng trình duyệt, sai chữ ký/số tiền, IPN trùng/đến muộn/timeout. Hoàn tiền chỉ ghi REFUNDED sau xác nhận hợp lệ từ provider hoặc quy trình hoàn COD có bằng chứng; không làm nút đổi trạng thái giả thành tích hợp hoàn tiền.

Voucher theo shop/toàn hệ thống: % giảm, trần tiền, đơn tối thiểu, khoảng thời gian, tổng lượt và lượt/user; có loại giảm phí vận chuyển. Bản đầu không cộng dồn nhiều voucher sản phẩm; cho một voucher hàng và một voucher vận chuyển nếu chính sách cho phép. Giữ/tiêu thụ/hoàn lượt trong transaction, tránh dùng trùng lượt cuối.

Nhà vận chuyển có tên, trạng thái, biểu phí; tính phí phía server theo chính sách đã chốt, lưu phí vào đơn. Chiết khấu hệ thống theo shop có thời gian hiệu lực; chụp tỷ lệ/số tiền khi chốt đơn, không sửa doanh thu lịch sử khi đổi cấu hình. Báo cáo tách doanh số, tiền đã thu, giảm giá, hoàn tiền, chiết khấu, doanh thu ròng; không gọi tất cả là lợi nhuận.

## 8. Đánh giá và WebSocket

Mỗi dòng hàng đã giao cho phép chủ đơn tạo một đánh giá (unique order_item_id); điểm 1–5, text sau trim từ 50 ký tự, ảnh/video đã kiểm định. Không cho tự đánh giá đơn hủy/chưa nhận; cập nhật đánh giá theo chính sách và lưu mốc sửa. Seller được phản hồi, không tự tăng sao/xóa đánh giá xấu. Admin ẩn nội dung vi phạm có lý do, không xóa dấu vết.

WebSocket dùng STOMP cùng origin, xác thực theo session Spring Security đã có. Đây là lựa chọn cho giao diện Thymeleaf; JWT vẫn thực sự bảo vệ API giỏ/yêu thích, không bắt buộc mọi giao thức phải dùng JWT.

- Endpoint dự kiến `/ws`; thông báo cá nhân `/user/queue/notifications`. Chỉ server phát thông báo đơn mới, thanh toán, trạng thái và yêu cầu trả hàng cho đúng người.
- Kiểm tra CONNECT, SUBSCRIBE, SEND, origin, CSRF; mặc định từ chối destination không khai báo. Không phát địa chỉ/điện thoại/chi tiết đơn qua topic công khai. [Spring WebSocket Security](https://docs.spring.io/spring-security/reference/6.5/servlet/integrations/websocket.html).
- Lưu notification và outbox cùng transaction nghiệp vụ, chỉ phát sau commit. Tác vụ gửi có retry; browser khử trùng theo event ID, đọc lại thông báo chưa đọc khi reconnect. Không dùng WebSocket làm nguồn dữ liệu duy nhất.
- Dùng user destinations để gửi theo principal đã xác thực, không theo userId do client tự khai. [Spring user destinations](https://docs.spring.io/spring-framework/reference/web/websocket/stomp/user-destination.html).
- Đăng xuất/khóa tài khoản/thu hồi membership phải ngắt hoặc loại quyền các kết nối đang mở, không chỉ chặn đăng ký mới.
- Đồ án một instance dùng simple broker; nhiều instance cần thiết kế broker/session chung, chưa bổ sung Redis/RabbitMQ khi không có nhu cầu.
- Cập nhật CSP connect-src cho endpoint WSS cùng hệ thống và media-src đúng Cloudinary; không mở wildcard để chữa lỗi kết nối.

Ca demo: khách A đặt hàng → Seller shop A nhận ngay; Seller shop B không nhận; cập nhật đơn → chỉ khách A nhận; ngắt mạng rồi mở lại không mất thông báo; rollback đơn không phát thông báo thành công.

## 9. Danh sách kiểm tra theo mốc

| Nhóm | Điều kiện phải thử |
|---|---|
| Hồi quy | Đăng ký/kích hoạt/reset, đăng nhập/remember/logout, CRUD catalog và upload cũ |
| Database thật | JPA validate, CRUD còn sau restart, rollback nhiều bảng, ràng buộc FK/unique |
| Quyền | Từng role và shop A/B; ID đoán, khóa shop/user, tự nâng quyền, đổi owner trong form |
| JWT/session | Giả/hết hạn/sai issuer/audience, thiếu Bearer, thiếu CSRF, logout/reset/thu hồi quyền |
| Media | MIME giả, quá dung lượng/pixel, lỗi provider, DB lỗi sau upload, xóa ảnh người khác |
| Đơn/tồn | Hai checkout tranh một cuốn; double click; hủy/lặp callback; timeout giữ tồn và restart |
| Thanh toán | IPN ký sai/trùng/đến muộn/sai tiền, đóng ReturnURL; hoàn tiền không lặp |
| Voucher/báo cáo | Hai người tranh lượt cuối; hết hạn/sai shop; số liệu trước/sau trả hàng |
| Review/realtime | Chưa mua, text 49/50 ký tự, media sai; nhận chéo user/shop, reconnect, thu hồi kết nối |
| UI | 320/390/768/1366px, bàn phím, dữ liệu rỗng, lỗi nhập, ảnh lỗi, tải chậm |
| Đóng gói | Máy sạch chạy JAR; không secret/test fixture trong JAR; chỉ cấu hình ngoài source |

Unit test dùng Clock/fake provider; HTTP integration dùng Spring test. Persistence/concurrency test chỉ chạy khi có DB kiểm thử được chuẩn bị và cho phép; không @Sql, Testcontainers init script, Hibernate create/update hay script tự tạo schema. Được tạo/xóa dữ liệu fixture trong transaction theo phạm vi DB test đã duyệt, tuyệt đối không dùng DB bài tập.

Cloudinary/Gmail/VNPAY thật hoặc sandbox kiểm tra thủ công có giới hạn, không gửi/upload hàng loạt từ test thường. Demo dùng khách hàng giả, ít nhất 2 shop và các tài khoản đủ vai trò. Không tải dữ liệu cá nhân thật lên GitHub.

## 10. Điều kiện bắt đầu và hồ sơ hoàn thành

Trước M1: cấu hình DB/email riêng, backup, phiên bản nền được chốt sau khi chủ project yêu cầu commit; xác định người phụ trách từng mốc. Trước M3 cần Cloudinary riêng; trước M6 cần VNPAY sandbox và URL HTTPS cho IPN. Người dùng tự nhập secret, không gửi vào chat.

Trước viết SQL M2 phải chốt mô hình từng bản sách và áp dụng Seller cho chuỗi cửa hàng. Không chạy schema chỉ dựa vào kế hoạch này.

Mỗi mốc bàn giao: source + test + ảnh/demo có ghi môi trường + checklist yêu cầu đã đạt + giới hạn còn lại. Báo cáo nộp ngắn, tập trung khảo sát, phân tích/use case, dữ liệu, màn hình, kết quả và đóng góp. Thiết kế kỹ thuật giữ trong file này, không nhồi vào README.

Chỉ hoàn thành khi mọi yêu cầu bắt buộc được đối chiếu lại với thầy, test môi trường thật/sandbox qua, nhóm chạy lại từ hướng dẫn và từng thành viên giải thích được phần mình làm. Các nội dung còn mơ hồ hoặc tài liệu Word chưa đọc phải được giải quyết trước khi đóng M0/M8.
