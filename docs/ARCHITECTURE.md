# Cấu trúc chương trình

- `web/`: Spring MVC controller, kiểm tra phiên/quyền và xử lý form.
- `auth/`, `category/`, `product/`: nghiệp vụ tài khoản, danh mục, sách.
- `shop/`: yêu cầu mở cửa hàng, xét duyệt, thành viên và nhật ký thay đổi.
- `inventory/`: từng bản sách vật lý, trạng thái/giá và lịch sử kho.
- `buyer/`: hồ sơ, địa chỉ, yêu thích, đã xem và giỏ hàng theo tài khoản.
- `order/`: checkout đa cửa hàng, đơn con, snapshot địa chỉ và reservation.
- `review/`: đánh giá sau khi đơn hoàn tất.
- `persistence/`: truy cập PostgreSQL bằng JPA.
- `mail/`, `image/`: gửi email và lưu ảnh.
- `resources/templates/`: giao diện Thymeleaf.
- `resources/static/assets/`: CSS và tài nguyên giao diện.

Spring quản lý controller, service, một EntityManagerFactory và JpaTransactionManager. JPA giữ mapping XML; `Store.tx()` dùng TransactionTemplate với propagation REQUIRED và EntityManager gắn theo transaction. Lời gọi lồng nhau cùng commit/rollback, không mở transaction độc lập. Pool Hikari vẫn do Hibernate quản lý theo vòng đời factory. Chỉ validate schema, không tạo bảng; test/preview dùng App riêng và không mở database.

Spring Security có hai chain: web dùng session/remember và CSRF, API chỉ nhận Bearer. SecurityFilter nằm trong chain web, chỉ đọc danh tính hiện hành và chuẩn bị form; không tự kiểm tra quyền hoặc CSRF nữa. Không lưu SecurityContext lâu dài: mỗi request đối chiếu user đang hoạt động, không bị khóa và auth_version.

JWT RS256 có issuer/audience, thời hạn, mã user và grant ngẫu nhiên; không chứa ID phiên web. Grant được thu hồi khi đổi đăng nhập, logout, hết phiên hoặc cấp token mới; đổi mật khẩu/khóa user được kiểm tra tại mỗi lần dùng. Cấu hình thiếu một phần khiến khởi động lỗi; chưa cấu hình JWT thì API vẫn từ chối mọi request. API JWT cho giỏ, yêu thích, đã xem và checkout đã có; giao diện vẫn dùng session.

Giới hạn hiện tại: grant ở bộ nhớ một instance, tối đa 10.000, tự loại grant hết hạn khi cấp mới. Restart vô hiệu hóa tất cả JWT cũ; không hỗ trợ nhiều instance hoặc giữ grant qua restart. Đây là bước triển khai có chủ đích khác bản thiết kế lưu grant DB, phải đổi sang kho dùng chung trước khi scale. Khóa tài khoản (`blocked`) tách biệt với chưa kích hoạt (`active=false`), OTP không được gỡ khóa quản trị.

Thymeleaf chỉ nhận dữ liệu cần hiển thị, không truy cập database. Nội dung người dùng được escape; các tên template do controller quyết định.

Vai trò Seller/Manager nằm trong `shop_memberships`, không ghi đè `users.role`. Admin xét duyệt và quản lý toàn hệ thống; Manager chỉ quản lý Seller tại shop được giao. Service kiểm tra quyền từ dữ liệu hiện hành; duyệt shop dùng transaction và khóa yêu cầu, thay đổi thành viên khóa shop. `shop_applications` giữ lịch sử và chỉ cho một yêu cầu đang chờ trên mỗi tài khoản. Các bảng shop nằm trong schema nền sạch; chưa nghiệm thu PostgreSQL. Danh sách cửa hàng/yêu cầu hiện chưa phân trang; cần bổ sung khi mở rộng dữ liệu.

`book_copies` giữ shop/đầu sách/mã cố định; nhập mới ở UNAVAILABLE, phân loại rồi mới AVAILABLE. Form kho không được đặt RESERVED/SOLD hoặc sửa bản đang ở hai trạng thái này. Thay đổi khóa shop trước, khóa bản sau và kiểm tra version của form; lịch sử được ghi trong cùng transaction. Danh sách 20 bản/trang; lịch sử chỉ hiển thị 100 dòng gần nhất nhưng không xóa dòng cũ. Không có API xóa bản sách; chặn xóa đầu sách nếu đã có bản trong kho. Giá và trạng thái công khai chỉ lấy từ `book_copies`.

CatalogService dùng các bản AVAILABLE, đã phân loại, thuộc shop ACTIVE và danh mục đang bật. Danh sách tìm theo tên/tác giả, 20 đầu sách/trang, sắp thời gian tạo và ID giảm dần; tổng số bản và giá thấp nhất được lấy theo nhóm đầu sách, không truy vấn từng dòng. Chi tiết liệt kê 20 bản/trang theo giá rồi ID; hết bản thì không hiển thị giá giả hoặc dùng lại giá cũ. Trang chủ hiện lấy tối đa 20 đầu sách mới có hàng, chưa phải bốn danh sách top 20 cuối kỳ.

Đầu sách không còn cột giá/tồn legacy; giá và tồn chỉ thuộc từng bản sách. Metadata ISBN, nhà xuất bản, năm và ngôn ngữ đã có; hồ sơ/địa chỉ, giỏ và checkout tạo đơn con theo shop trong lớp nghiệp vụ. Chưa nghiệm thu các giao dịch này trên PostgreSQL.

Checkout khóa bản sách bằng pessimistic lock trong một transaction, tính lại tiền từ kho và nhận idempotency key theo tài khoản. `checkout_groups` giữ lần checkout; `orders` là các đơn con theo shop. Bản hiện tại dùng COD ở mức tạo đơn/reservation; VNPAY, carrier, voucher, hoàn tiền và WebSocket thông báo nghiệp vụ vẫn là các mốc sau.

Ảnh production đi qua `CloudinaryImageStorage`; adapter có thể trả `public_id`, loại tài nguyên, URL và kích thước để ghi vào `media_assets`. URL nhập từ ngoài chỉ được chấp nhận khi là HTTPS Cloudinary; local filesystem không còn là runtime storage.
