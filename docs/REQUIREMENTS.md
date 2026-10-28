# Yêu cầu và phạm vi bàn giao

Đồ án nhóm27, đề tài14: chuỗi cửa hàng sách cũ. Đối chiếu yêu cầu UTEx và rubric tải về ngày26/10/2026; đây không phải xác nhận có thông báo mới sau mốc đó.

| Nhóm yêu cầu | Hiện trạng |
|---|---|
| Spring Boot, Security, JPA/PostgreSQL, giao diện | Đã triển khai; mô hình controller → service → repository |
| Tài khoản, OTP, JWT | Có đăng ký/kích hoạt/login/quên mật khẩu; Compose thử OTP bằng Mailpit |
| Catalog/tìm kiếm/top20 | Có lọc và bốn bảng xếp hạng, phân trang; trang chủ có sách bán trên10 bản |
| Hồ sơ, địa chỉ, yêu thích, đã xem, giỏ | Có web và API theo tài khoản |
| Đặt hàng và thanh toán | COD, payOS, đơn con theo shop, chống gửi lặp; payOS cần thầy xác nhận thay VNPAY/MOMO |
| Đơn, trả hàng và hoàn tiền | Theo dõi/xử lý đơn, trả trong7 ngày; hoàn tiền ghi nhận bên ngoài, không tự chuyển tiền |
| Review và media | Sau đơn hoàn tất; text50–5000 ký tự, ảnh/MP4 tối đa5MB, kiểm duyệt |
| Shop, kho, quản trị | Duyệt shop, thành viên, từng bản sách, danh mục/đầu sách, khóa user |
| Vận hành | Voucher, bảng phí giao hàng nội bộ, commission, doanh thu |
| WebSocket | Thông báo đơn đúng quyền, native `/ws`, chu kỳ khoảng5 giây |
| Shipper | Chức năng thêm, chưa triển khai vai trò riêng |

## Rubric và hồ sơ

Mẫu local có năm tiêu chí: độ khó/phân công10%, AI/GitHub20%, hình thức/cấu trúc/vai trò20%, ứng dụng/code/cài đặt35%, sáng tạo15%. Nhóm tự chấm lần lượt8,15,15,28,12: tổng78/100 (7,8/10) mỗi thành viên. Giảng viên quyết định điểm chính thức.

[Hồ sơ](reports/README.md) gồm một báo cáo chung, ba báo cáo cá nhân và rubric. Cần video có mặt từng người, tự giải thích phần phụ trách và bằng chứng đóng góp thật. Lịch đã đối chiếu: 29/10/2026,07:00–18:00,A5-203; nhóm27 lượt28, nộp trước lượt30 phút; chưa có giờ riêng được xác nhận.

Giới hạn còn lại: chưa nghiệm thu tiền thật/webhook Internet, upload video Cloudinary thật, tải lớn/nhiều instance, vận đơn và refund/payout tự động. Chi tiết [nghiệm thu](FINAL_ACCEPTANCE.md) và [kiểm thử](VERIFICATION.md).

Nguồn: https://utexlms.hcmute.edu.vn/mod/page/view.php?id=1452010. Rubric gốc được giữ local trong `outputs/final-2026-10-26/originals`; không sửa sheet công khai.
