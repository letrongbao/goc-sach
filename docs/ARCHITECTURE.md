# Cấu trúc chương trình

> Cập nhật cuối kỳ: [FINAL_ACCEPTANCE.md](FINAL_ACCEPTANCE.md). Các đoạn bên dưới giữ mô tả mốc trước; top 20, media/moderation, carrier/voucher, trả hàng và native WebSocket đã bổ sung. STOMP cũ không còn là cơ chế đang dùng.

- `web/`: Spring MVC controller, kiểm tra phiên/quyền và xử lý form.
- `auth/`, `category/`, `product/`: nghiệp vụ tài khoản, danh mục, sách.
- `shop/`: yêu cầu mở cửa hàng, xét duyệt, thành viên và nhật ký thay đổi.
- `inventory/`: từng bản sách vật lý, trạng thái/giá và lịch sử kho.
- `buyer/`: hồ sơ, địa chỉ, yêu thích, đã xem và giỏ hàng theo tài khoản.
- `order/`: checkout đa cửa hàng, đơn con, snapshot địa chỉ và reservation.
- `payment/`: yêu cầu PayOS, xác minh webhook, đối chiếu và hủy thanh toán.
- `review/`: đánh giá sau khi đơn hoàn tất.
- `persistence/`: truy cập PostgreSQL bằng JPA.
- `mail/`, `image/`: gửi email và lưu ảnh.
- `resources/templates/`: giao diện Thymeleaf.
- `resources/static/assets/`: CSS và tài nguyên giao diện.

Spring quản lý controller, service, một EntityManagerFactory và JpaTransactionManager. JPA giữ mapping XML; `Store.tx()` dùng TransactionTemplate với propagation REQUIRED và EntityManager gắn theo transaction. Lời gọi lồng nhau cùng commit/rollback, không mở transaction độc lập. Pool Hikari vẫn do Hibernate quản lý theo vòng đời factory. Chỉ validate schema, không tạo bảng; test/preview dùng App riêng và không mở database.

Spring Security có ba chain: web dùng session/remember và CSRF, API chỉ nhận Bearer, webhook PayOS chỉ cho POST và xác minh chữ ký thay CSRF. SecurityFilter nằm trong chain web, chỉ đọc danh tính hiện hành và chuẩn bị form; không tự kiểm tra quyền hoặc CSRF nữa. Không lưu SecurityContext lâu dài: mỗi request đối chiếu user đang hoạt động, không bị khóa và auth_version.

JWT RS256 có issuer/audience, thời hạn, mã user và grant ngẫu nhiên; không chứa ID phiên web. Grant được thu hồi khi đổi đăng nhập, logout, hết phiên hoặc cấp token mới; đổi mật khẩu/khóa user được kiểm tra tại mỗi lần dùng. Cấu hình thiếu một phần khiến khởi động lỗi; chưa cấu hình JWT thì API vẫn từ chối mọi request. API JWT cho giỏ, yêu thích, đã xem và checkout đã có; giao diện vẫn dùng session.

Giới hạn hiện tại: grant ở bộ nhớ một instance, tối đa 10.000, tự loại grant hết hạn khi cấp mới. Restart vô hiệu hóa tất cả JWT cũ; không hỗ trợ nhiều instance hoặc giữ grant qua restart. Đây là bước triển khai có chủ đích khác bản thiết kế lưu grant DB, phải đổi sang kho dùng chung trước khi scale. Khóa tài khoản (`blocked`) tách biệt với chưa kích hoạt (`active=false`), OTP không được gỡ khóa quản trị.

Thymeleaf chỉ nhận dữ liệu cần hiển thị, không truy cập database. Nội dung người dùng được escape; các tên template do controller quyết định.

Vai trò Seller/Manager nằm trong `shop_memberships`, không ghi đè `users.role`. Admin xét duyệt và quản lý toàn hệ thống; Manager chỉ quản lý Seller tại shop được giao. Service kiểm tra quyền từ dữ liệu hiện hành; duyệt shop dùng transaction và khóa yêu cầu, thay đổi thành viên khóa shop. `shop_applications` giữ lịch sử và chỉ cho một yêu cầu đang chờ trên mỗi tài khoản. Các bảng shop nằm trong schema nền sạch; JPA đã validate trên PostgreSQL thật. Danh sách cửa hàng/yêu cầu hiện chưa phân trang; cần bổ sung khi mở rộng dữ liệu.

`book_copies` giữ shop/đầu sách/mã cố định; nhập mới ở UNAVAILABLE, phân loại rồi mới AVAILABLE. Form kho không được đặt RESERVED/SOLD hoặc sửa bản đang ở hai trạng thái này. Thay đổi khóa shop trước, khóa bản sau và kiểm tra version của form; lịch sử được ghi trong cùng transaction. Danh sách 20 bản/trang; lịch sử chỉ hiển thị 100 dòng gần nhất nhưng không xóa dòng cũ. Không có API xóa bản sách; chặn xóa đầu sách nếu đã có bản trong kho. Giá và trạng thái công khai chỉ lấy từ `book_copies`.

CatalogService dùng các bản AVAILABLE, đã phân loại, thuộc shop ACTIVE và danh mục đang bật. Danh sách tìm theo tên/tác giả, 20 đầu sách/trang, sắp mới nhất/giá tăng/giá giảm/lượt yêu thích; giá sort là min của những bản khớp filter, tie-break createdAt/id. Tổng số bản và giá thấp nhất được lấy theo nhóm đầu sách. Chi tiết liệt kê 20 bản/trang theo giá rồi ID; hết bản không hiển thị giá giả. Trang chủ giới thiệu tối đa 5 sách mới và 5 sách có lượt yêu thích cùng danh mục/shop thật; chưa phải bốn danh sách top 20 cuối kỳ.

ReviewService phục vụ form session/CSRF và API: kiểm tra đơn COMPLETED của người gửi, khóa user trước tạo/sửa, unique user/product, nội dung 50–5.000 ký tự. Review công khai chỉ PUBLISHED, phân trang 10, count/average query riêng. DTO không lộ user/order ID; tên lấy từ profile hoặc nhãn chung. Danh mục tắt chặn review công khai; người mua vẫn mở/sửa review từ lịch sử. Phần media/moderation UI còn chờ. `experience.css` và `experience.js` bổ sung UI trên Bootstrap, form server vẫn hoạt động khi tắt JS; chi tiết ở UI_UX.md.

Đầu sách không còn cột giá/tồn legacy; giá và tồn chỉ thuộc từng bản sách. Metadata ISBN, nhà xuất bản, năm và ngôn ngữ đã có; hồ sơ/địa chỉ, giỏ và checkout tạo đơn con theo shop trong lớp nghiệp vụ. Nghiệm thu PostgreSQL đã kiểm tra rollback, checkout hai shop, hủy/hoàn tất và hai người tranh một cuốn; dữ liệu commit được đọc lại từ JVM mới. Không dùng kết quả này thay cho nghiệm thu email, ảnh hoặc thanh toán ngân hàng thật.

Checkout khóa bản sách bằng pessimistic lock trong một transaction, tính lại tiền từ kho và nhận idempotency key theo tài khoản. `checkout_groups` giữ lần checkout; `orders` là các đơn con theo shop. COD giữ sách tới khi hủy/hoàn tất. PayOS giữ sách trong thời gian thanh toán; chỉ trả về kho sau khi đối chiếu cổng xác nhận chưa nhận tiền. Trạng thái xử lý đơn và trạng thái thanh toán được lưu riêng; đơn PayOS chưa trả đủ tiền không được giao.

`PaymentGateway` dùng SDK PayOS chính thức; gọi mạng ngoài transaction database, timeout hữu hạn và không tự đổi mã đơn để thử lại. Mỗi đơn có một mã cổng được lưu trước khi tạo link. `payment_attempts` lưu trạng thái/link/hạn; `payment_events` lưu mã giao dịch, số tiền, kết quả và nguồn WEBHOOK/QUERY, không lưu nguyên webhook hoặc tài khoản ngân hàng. Webhook được kiểm tra chữ ký và thông tin đơn/số tiền; mã giao dịch unique chống xử lý lặp. Return/cancel URL phục vụ giao diện, không dùng tham số browser làm bằng chứng đã thanh toán. Các đơn quá hạn và chưa rõ kết quả được đối chiếu lại; lỗi kết nối giữ reservation để tránh bán trùng. Khoản sai tiền/đến muộn chuyển REVIEW_REQUIRED để quản trị viên đối chiếu, chưa có hoàn tiền hoặc payout tự động. Carrier, voucher và WebSocket thông báo nghiệp vụ vẫn là các mốc sau.

Ảnh production đi qua `CloudinaryImageStorage`; adapter có thể trả `public_id`, loại tài nguyên, URL và kích thước để ghi vào `media_assets`. URL nhập từ ngoài chỉ được chấp nhận khi là HTTPS Cloudinary; local filesystem không còn là runtime storage.
