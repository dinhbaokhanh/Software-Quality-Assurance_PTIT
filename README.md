# SQA Assignments 

## Thông tin chung

- **Giảng viên hướng dẫn:** Đỗ Thị Bích Ngọc
- **Thành viên nhóm:**
  - Đinh Bảo Khánh (B22DCVT281)
  - Lê Tiến Đạt
  - Trương Gia Huy
  - Phạm Thành Hùng

## Tài liệu

- [SQA Plan](https://docs.google.com/document/d/14SzqYvOzSAcVaDdzvud-SpP-ae8LHgBoX4YZF86uvWM/edit?usp=sharing)
- [System Test](https://docs.google.com/spreadsheets/d/1ut3jaWGeHhlKOk9BkE4KvtNY6Wykqodg2k4cBtnXll4/edit?usp=sharing)
- [SQA Review](https://docs.google.com/spreadsheets/d/1TH6qAIwB-VmL7R0Hp8YHST-PbS9FEJ5G5yOmyaBYnf8/edit?usp=sharing)
- [Unit Test](https://docs.google.com/spreadsheets/d/1416zVksrB3UFY4w7VMqRh3puT8Gl2xVSBAPaIYH0ATU/edit?usp=sharing)

## Overview

Phần unit test trong dự án này chủ yếu là các service test chạy trên Spring context thật, dùng `@SpringBootTest` và profile `test`. Các test không chỉ kiểm tra kết quả trả về mà còn kiểm tra dữ liệu trong database sau khi gọi service.

Hiện tại bộ test tập trung vào 4 nhóm chính: `CategoryService`, `EnrollmentService`, `LessonProgressService` và `QuizAttemptService`. Mỗi nhóm đều có các case cho nhánh đúng, nhánh lỗi, và một số case đặc biệt để phát hiện bug hiện có như thiếu validate null, kiểm tra sai quan hệ giữa dữ liệu, hoặc lỗi xử lý JPA lifecycle.

Vì vậy, dù đang được gọi là unit test, thực tế đây là lớp test ở mức service gần với integration test hơn. Mục tiêu của chúng là xác nhận logic nghiệp vụ và trạng thái dữ liệu sau khi chạy từng hàm quan trọng.

## 1. Chạy server để test trong IntelliJ

Mở thư mục [online-learning](online-learning) bằng IntelliJ IDEA, đảm bảo đang dùng JDK 21, rồi tạo một Run/Debug Configuration kiểu Spring Boot với main class là `com.ptit.onlinelearning.OnlineLearningApplication`.

Trong phần Environment variables, cấu hình tối thiểu các biến mà `application-dev.yml` đang dùng, đặc biệt là `DB_NAME`, `DB_PASSWORD`, `AWS_REGION_NAME`, `AWS_S3_BUCKET`, `AWS_ACCESS_KEY`, `AWS_SECRET_KEY`, `CLOUDFRONT_HOST`, `VNPAY_URL`, `VNPAY_RETURN_URL`, `VNPAY_RETURN_URL_PRE_ORDER`, `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET`, `VNPAY_API_URL`, `SENGRID_API_KEY`. Nếu cần test đầy đủ luồng backend, tự thiết lập env và chạy sẵn PostgreSQL, Redis và RabbitMQ theo các giá trị trong file cấu hình. Nếu cần xem Unit Test, đối chiếu với link unit test ở trên.

Thêm program arguments `--spring.profiles.active=dev`, sau đó bấm Run. Server sẽ khởi động trên port `8080` theo cấu hình hiện tại.

## 2. Unit test

Có thể chạy nhanh toàn bộ test bằng Maven:

```shell
mvn test
```

Trong IntelliJ, bạn cũng có thể chạy trực tiếp từng class test như `CategoryServiceUnitTest`, `EnrollmentServiceUnitTest`, `LessonProgressServiceUnitTest`, `QuizAttemptServiceUnitTest` hoặc `OnlineLearningApplicationTests`.

Các test này dùng dữ liệu thật trong database test, nên phù hợp nhất để kiểm tra logic nghiệp vụ và các nhánh xử lý quan trọng của service.

## 3. Quá trình viết test (Cách làm cá nhân)

### 3.1 Kiểu test đang dùng

Dù đặt trong package `unit`, các test này thực chất gần với **service-layer integration test** hơn: chúng khởi động toàn bộ Spring context (`@SpringBootTest`), chạy với profile `test` kết nối database thật, và **không mock** bất kỳ dependency nào. Lý do chọn hướng này là để xác nhận đồng thời cả logic nghiệp vụ lẫn trạng thái dữ liệu trong DB sau mỗi thao tác quan trọng.

### 3.2 Pattern Arrange – Act – Assert + CheckDB

Mỗi test case đều tuân theo cấu trúc 3 bước rõ ràng:

| Bước | Mục đích |
|------|----------|
| **Arrange** | Chuẩn bị dữ liệu đầu vào, ghi nhận trạng thái DB trước khi gọi service (thường là `count()`) |
| **Act** | Gọi đúng một phương thức service cần kiểm tra |
| **Assert & CheckDB** | Kiểm tra giá trị trả về AND đọc lại DB để xác nhận dữ liệu thực sự đã thay đổi (hoặc không đổi) đúng như kỳ vọng |

Bước **CheckDB** đặc biệt quan trọng vì nó phát hiện các lỗi mà `return` đúng nhưng DB lại sai (ví dụ: quên gọi `save()`, hoặc JPA dirty-checking flush nhầm).

### 3.3 Phân tích branch coverage cho `CategoryService`

Mỗi method trong service đều có các nhánh rẽ (branch) tương ứng với điều kiện `if`, `orElseThrow`, hay kiểm tra đầu vào. Bộ test được thiết kế để bao phủ từng nhánh đó:

#### `createCategory` — TC_CA_01

Chỉ có 1 luồng: đầu vào hợp lệ → lưu DB, trả về entity mới có ID. Test kiểm tra các field mapping đúng và `isActive` mặc định là `true`.

#### `getCategoryById` — TC_CA_02, TC_CA_03

| Branch | Điều kiện | Test case | Kỳ vọng |
|--------|-----------|-----------|---------|
| Branch 1 | ID tồn tại | TC_CA_02 | Trả về đúng entity |
| Branch 2 | ID không tồn tại | TC_CA_03 | Throw `DataNotFoundException`, message chứa ID |

#### `getCategories` — TC_CA_04 đến TC_CA_08

Method này nhận nhiều filter tuỳ chọn; mỗi filter là một nhánh độc lập:

| Branch | Điều kiện | Test case | Kỳ vọng |
|--------|-----------|-----------|---------|
| Branch A | Tất cả filter `null` | TC_CA_04 | Trả về toàn bộ record, `totalElements` khớp `count()` |
| Branch B | `search != null` | TC_CA_05 | Chỉ trả về category tên khớp keyword |
| Branch C | `isActive != null` | TC_CA_06 | Chỉ trả về category đúng trạng thái active |
| Branch D | `parentId != null` | TC_CA_07 | Chỉ trả về category con của parentId đó |
| Branch E | `sortOrder` không hợp lệ | TC_CA_08 | Throw `IllegalArgumentException` |

#### `updateCategory` — TC_CA_09 đến TC_CA_14

Method này có nhiều nhánh lồng nhau theo từng field trong `UpdateCategoryRequest`:

| Branch | Điều kiện | Test case | Kỳ vọng |
|--------|-----------|-----------|---------|
| Branch 1 | ID không tồn tại | TC_CA_09 | Throw `DataNotFoundException` |
| Branch 2 | `name != null`, hợp lệ | TC_CA_10 | `name` được ghi đè |
| Branch 2b | `name != null` nhưng blank | TC_CA_11 | `name` **không** được ghi đè *(phát hiện bug: service hiện tại chưa validate)* |
| Branch 3,5,7,9 | Tất cả field `null` | TC_CA_12 | Không có gì thay đổi trong DB |
| Branch 6,8 | `image` và `parentId` không `null` | TC_CA_13 | Cả hai field được ghi đè |
| Branch 8b | `parentId` = chính ID của category | TC_CA_14 | Throw `IllegalArgumentException` *(phát hiện bug: service hiện tại chưa check)* |

> **Lưu ý về bug phát hiện qua test**: TC_CA_11 và TC_CA_14 là các test có thể **fail có chủ ý** — chúng được viết để tài liệu hoá hành vi kỳ vọng mà service chưa implement đúng, đóng vai trò như regression test chờ được fix.

#### `deleteCategory` — TC_CA_15, TC_CA_16, TC_CA_17

| Branch | Điều kiện | Test case | Kỳ vọng |
|--------|-----------|-----------|---------|
| Branch 1 | ID tồn tại, không có category con | TC_CA_15 | Xóa thành công, `count()` giảm 1, entity biến mất khỏi DB |
| Branch 1b | ID tồn tại, còn category con | TC_CA_16 | Throw `IllegalStateException`, DB không đổi *(phát hiện bug: service hiện tại chưa check con)* |
| Branch 2 | ID không tồn tại | TC_CA_17 | Throw `DataNotFoundException`, DB không đổi |

### 3.4 Nguyên tắc cô lập dữ liệu

Tất cả test đều có annotation `@Transactional`: mọi thay đổi dữ liệu được **rollback** sau mỗi test case, đảm bảo các test không ảnh hưởng lẫn nhau và có thể chạy song song hoặc lặp lại mà không cần reset DB thủ công.

Dữ liệu Arrange được tạo trực tiếp qua `categoryRepository.save()` (bypass service), giúp test không phụ thuộc vào việc `createCategory` có đang hoạt động đúng hay không.

