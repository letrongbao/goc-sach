# Kế hoạch hoàn thiện Góc Sách

Cập nhật ngày 17/09/2026, sau khi đọc lại [quy định cuối khóa trên UTEx](https://utexlms.hcmute.edu.vn/mod/page/view.php?id=1452010) và đối chiếu source. Đây là kế hoạch, không phải danh sách tính năng đã hoàn thành.

Mục tiêu: phát triển các chức năng đang có thành website quản lý chuỗi cửa hàng sách cũ theo đề tài 14. Repo bài tập giữ nguyên. Không tạo lại dự án và không đưa submissions trở lại repo đồ án.

## Những điểm đã làm rõ

- Stack cần có: Spring Boot, Thymeleaf, Bootstrap, JPA, một database quan hệ (chọn PostgreSQL), JWT, WebSocket và Cloudinary. Quy định chung còn yêu cầu Spring Security khi dùng Spring Boot.
- Chức năng theo vai trò chiếm 6/10 điểm; có đủ thư viện nhưng chưa có chức năng hoạt động thì chưa đủ.
- Guest, User, Vendor/Seller, Manager và Admin đều nằm trong yêu cầu bán hàng. Shipper được ghi là phần thêm.
- Không được giữ nguyên giới hạn 10 sách mới và 6 sách/trang của bản nền rồi coi là đủ yêu cầu cuối kỳ.
- Đánh giá/bình luận sau mua, yêu thích, đã xem, giảm giá, nhà vận chuyển và doanh thu phải có trong kế hoạch chính.
- Nhóm tối đa 3 sinh viên. Trang quy định chưa điền ngày báo cáo cụ thể.

## Thứ tự thực hiện

Ước lượng dưới đây là ngày công tập trung của một người, gồm code và test; không phải lịch cam kết. Tổng khoảng 40–58 ngày công, nên dành thêm 20% dự phòng. Chỉ lập lịch theo ngày khi biết hạn nộp, thành viên và thời gian học thực tế.

| Mốc | Kết quả cần làm | Điều kiện chốt | Ước lượng |
|---|---|---|---|
| M0 — Chốt phạm vi | Xác nhận mô hình cửa hàng/Seller, cách tính danh sách trang chủ; khảo sát, use case và phân công | Bảng yêu cầu có vai trò, màn hình và ca kiểm tra; không tự bỏ chức năng | 1–2 |
| M1 — Nền tảng và bảo mật | Kiểm tra DB/email thật; đưa service/JPA vào vòng đời Spring; Spring Security + JWT | Luồng tài khoản cũ hoạt động; token sai/hết hạn/thu hồi bị chặn; rollback đúng | 5–7 |
| M2 — Cửa hàng và sách cũ | Đăng ký/duyệt shop, thành viên theo shop, đầu sách và từng bản sách, nhập/điều chỉnh tồn | Dữ liệu hai shop tách biệt; không bán bản sách ngừng bán hoặc chưa duyệt | 5–7 |
| M3 — Cloudinary và tủ sách | Hoàn tất nghiệm thu upload Cloudinary, tìm kiếm/lọc, trang shop, danh sách mới/bán chạy/đánh giá/yêu thích | Ảnh vẫn còn sau khởi động lại; kết quả lọc/phân trang đúng và responsive | 3–4 |
| M4 — Người mua | Hồ sơ, nhiều địa chỉ, yêu thích, đã xem, giỏ hàng DB; API tương tác dùng JWT | Đăng nhập lại còn giỏ; không sửa được dữ liệu người khác; giá tính ở server | 3–4 |
| M5 — Đơn hàng COD | Tách đơn theo shop, vận chuyển, giữ/trừ tồn, lịch sử trạng thái, hủy | Mua hàng end-to-end; hai người tranh cuốn cuối chỉ một người mua được | 6–8 |
| M6 — Thanh toán và vận hành | VNPAY sandbox, trả hàng/hoàn tiền, khuyến mãi, chiết khấu, quản lý người dùng/nhà vận chuyển, doanh thu | Callback trùng không xử lý hai lần; hoàn trả và số liệu đối soát được | 7–10 |
| M7 — Đánh giá và realtime | Đánh giá sau mua, bình luận từ 50 ký tự, ảnh/video; thông báo WebSocket | Người chưa mua không đánh giá được; thông báo đúng người/shop, có phục hồi khi mất kết nối | 4–6 |
| M8 — Nghiệm thu và bảo vệ | Test toàn bộ role, mobile, chạy máy sạch; báo cáo và demo | Các dòng yêu cầu bắt buộc đều có minh chứng; không có secret hoặc dữ liệu cá nhân thật trong demo | 6–10 |

Các mốc theo thứ tự phụ thuộc trên. Khung hiển thị đánh giá/yêu thích ở M3 chỉ được nghiệm thu đầy đủ khi M4/M7 cung cấp dữ liệu thật. Không ghi số liệu giả để lấp màn hình trống.

Tiến độ 17/09: M1 còn chờ nghiệm thu PostgreSQL và gửi OTP thật. M2 có cửa hàng, trang shop công khai, kho từng bản và phân quyền theo shop. M3 đã có bộ lọc đầy đủ và chi tiết các bản đang bán; còn các top-list theo đề và lượt upload thật. M4 đã có hồ sơ, địa chỉ, yêu thích, đã xem và giỏ hàng. M5 đã có checkout đa shop, tổng phí trước khi đặt, snapshot, giữ sách, idempotency, chi tiết/lịch sử và state machine người bán; còn đơn vị vận chuyển, trả/hoàn tiền và kiểm thử đồng thời trên PostgreSQL. SQL không được tự chạy. Theo dõi [review và kiểm thử từng mốc](MILESTONES.md).

Sau M7 có đủ các thành phần stack hoạt động; chỉ sau M8 mới xét hoàn thành đồ án. Mọi mốc có thay đổi schema đều phải dừng để chủ project kiểm tra và cho phép thực thi SQL.

## Phạm vi mặc định để lập kế hoạch

Các lựa chọn này là đề xuất triển khai, chưa thay cho xác nhận của thầy:

1. Góc Sách là một hệ thống nhiều cửa hàng. Người dùng được gửi yêu cầu mở/gia nhập shop, admin duyệt rồi mới có quyền Seller. Giữ chức năng Seller trong kế hoạch; hỏi thầy cách áp dụng với chuỗi nội bộ.
2. Admin quản lý toàn hệ thống; Manager chỉ quản lý cửa hàng được giao; Seller quản lý shop được duyệt của mình. Không cấp quyền quản lý toàn hệ thống từ màn hình đăng ký.
3. Mỗi cuốn sách cũ có mã riêng, tình trạng, ảnh, giá và cửa hàng. Thông tin đầu sách dùng chung. Cách này phù hợp các bản sách khác nhau về độ mới/giá.
4. Làm COD và VNPAY sandbox; không làm đồng thời cả MOMO. Không giao dịch tiền thật trong kiểm thử.
5. Giỏ có thể chứa nhiều shop; checkout tạo nhóm mua và đơn con từng shop, mỗi đơn con có phí vận chuyển, trạng thái và thanh toán riêng.
6. Shipper và luân chuyển kho nâng cao chỉ làm sau các chức năng bắt buộc. Không bổ sung microservices, ứng dụng mobile hoặc gợi ý AI trong phạm vi cơ bản.

## Việc cần hỏi thầy trước khi khóa phạm vi

- Đề tài 14 và thành viên nhóm đã được duyệt chưa, hạn nộp/bảo vệ chính xác?
- Seller đăng ký shop được áp dụng thế nào cho chuỗi sách cũ? Manager được chỉnh chiết khấu/nhà vận chuyển đến mức nào?
- Câu Guest về “sản phẩm bán trên 10 sản phẩm” có nghĩa là các sách bán hơn 10 cuốn, hay số sản phẩm cần hiển thị? Tạm đề xuất hiển thị mục bán chạy có số lượng bán trên 10, sắp giảm dần, cùng mục sách mới riêng; cần xác nhận.
- “20 sản phẩm ... nhất được phân trang” tạm hiểu là từng danh sách top 20 mới/bán chạy/đánh giá/yêu thích có phân trang. Không tự coi đây chỉ là page size 20.
- Mức hoàn tiền qua cổng thanh toán và yêu cầu bắt buộc của ảnh/video bình luận.

File khảo sát/phân tích Word đính kèm UTEx hiện bị Chrome chặn tải trong lần đọc này; chưa xác minh nội dung file. Cần tải lại thủ công để đối chiếu trước M0 hoàn tất.

## Cách tổ chức công việc

Mỗi tính năng làm đủ màn hình, xử lý, dữ liệu và test; không chia kiểu một người chỉ viết toàn bộ giao diện rồi chờ ghép cuối kỳ. Nếu nhóm 3 người, dự kiến chia theo khối người mua; cửa hàng/vận hành; nền tảng/tích hợp và cùng review phần giao nhau. Chỉ điền tên và đóng góp thực tế.

Mỗi mốc có checklist và một buổi demo ngắn. Commit thật theo Conventional Commits, ví dụ `feat(cart): add saved cart`. Không đổi ngày commit và không ghi tính năng kế thừa là đóng góp mới. Chưa tạo nhánh, commit, issue hay push trong lượt lập kế hoạch này.

Chi tiết kỹ thuật: [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md). Bảng đối chiếu thầy yêu cầu: [REQUIREMENTS.md](REQUIREMENTS.md).
