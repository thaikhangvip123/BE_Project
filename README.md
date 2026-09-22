## Hệ thống PageRank & Personalized PageRank cho dữ liệu Instacart

Dự án này gồm ba phần:

- **Backend Scala + Spark GraphX**: đọc 3 file CSV (`products.csv`, `order_products__prior.csv`, `order_products__train.csv`), xây đồ thị đồng-mua hàng, chạy PageRank/PPR rồi ghi kết quả vào MySQL.
- **Frontend Next.js 14 (App Router)**: hiển thị bảng PageRank, tìm kiếm Personalized PageRank và đồ thị trực quan, sẵn sàng deploy lên Vercel.
- **MySQL**: lưu bảng `nodes`, `pagerank`, `ppr`.

---

## 1. Cấu trúc thư mục

```
project-root/
├─ backend-graphx/        # Scala + Spark GraphX REST API
├─ frontend-nextjs/       # Next.js 14 + Prisma + react-force-graph
├─ db/init.sql            # Tạo bảng nodes/pagerank/ppr
├─ products.csv
├─ order_products__prior.csv
├─ order_products__train.csv
└─ README.md
```

---

## 2. Chuẩn bị MySQL

1. Đảm bảo MySQL đã cài local và tạo user `root` với mật khẩu `******`.
2. Tạo database và bảng:

```bash
mysql -u root -p****** < db/init.sql
```

File `init.sql` chỉ tạo schema, backend sẽ ghi đè toàn bộ bảng `nodes`/`pagerank`/`ppr` mỗi lần chạy.

---

## 3. Backend Scala + Spark GraphX

### Công nghệ
- Scala 2.12
- Spark 3.5.1 (core, sql, graphx)
- http4s + cats-effect để dựng REST server
- MySQL JDBC driver

### Cách hoạt động
1. Đọc `products.csv` làm danh sách node.
2. Đọc `order_products__prior.csv` và `order_products__train.csv`, gom các sản phẩm có trong cùng `order_id`, giới hạn 40 sản phẩm/order để tránh nổ tổ hợp.
3. Tạo cạnh hai chiều giữa các sản phẩm đồng xuất hiện (trọng số = 1).
4. Dựng GraphX `Graph[String, Double]`.
5. API:
   - `GET /health` → thống kê số đỉnh/cạnh trong đồ thị.
   - `GET /pagerank?iterations=20` → chạy `staticPageRank`, ghi bảng `pagerank`.
   - `GET /ppr?nodeId=49683&iterations=20` → chạy `staticPersonalizedPageRank` cho node tương ứng, ghi bảng `ppr` (xóa dữ liệu cũ cho `source_id` đó trước khi ghi).
6. Mọi truy vấn sử dụng JDBC `jdbc:mysql://localhost:3306/graphdb`.

### Biến môi trường (tùy chọn)

| Biến | Mặc định | Mô tả |
|------|----------|-------|
| `DB_HOST` | `localhost` | Host MySQL |
| `DB_PORT` | `3306` | Port MySQL |
| `DB_NAME` | `graphdb` | Database |
| `DB_USER` | `root` | User |
| `DB_PASS` | `******` | Password |
| `DATA_DIR` | thư mục cha của `backend-graphx` | Vị trí chứa 3 file CSV |
| `HTTP_PORT` | `9000` | Cổng server http4s |
| `PAGERANK_ITER` | `20` | Số vòng lặp mặc định cho PR/PPR |
| `RESET_PROB` | `0.15` | Xác suất reset |
| `MAX_PRODUCTS_PER_ORDER` | `40` | Giới hạn số sản phẩm dùng để tạo cạnh trên 1 order |

### Chạy backend

```bash
cd backend-graphx
sbt run
```

Server lắng nghe tại `http://localhost:9000`.

### Gọi thử

```bash
# Kiểm tra dữ liệu đồ thị
curl http://localhost:9000/health

# Viết lại bảng pagerank
curl "http://localhost:9000/pagerank?iterations=25"

# Viết lại bảng ppr cho sản phẩm 49302
curl "http://localhost:9000/ppr?nodeId=49302&iterations=20"
```

Sau khi API trả về `status: "ok"`, bảng MySQL tương ứng sẽ có dữ liệu mới.

---

## 4. Frontend Next.js 14 (deploy Vercel)

### Tính năng chính
- `/`:
  - Bảng PageRank (top 50) lấy trực tiếp từ bảng `pagerank`.
  - Thanh tìm kiếm Personalized PageRank: nhập tên sản phẩm -> truy vấn bảng `ppr` theo `source_id`.
  - Card thể hiện PPR mới nhất + link tới trang node.
  - Biểu đồ force-directed (react-force-graph) dựa trên top PageRank.
- `/node/[id]`: hiển thị toàn bộ PPR của node cụ thể.
- API routes (`/api/rank`, `/api/node/[id]`, `/api/search`) dùng Prisma để đọc MySQL.

### Thiết lập môi trường

Tạo file `frontend-nextjs/.env.local`:

```
DATABASE_URL="mysql://root:Jacuby123@localhost:3306/graphdb"
```

### Cài đặt & chạy

```bash
cd frontend-nextjs
npm install
npx prisma generate
npm run dev
```

Truy cập `http://localhost:3000`.

> Lưu ý: frontend chỉ hiển thị dữ liệu đã có trong MySQL. Hãy chạy backend trước để lấp đầy bảng `nodes`, `pagerank`, `ppr`.

### Deploy lên Vercel
1. Push mã nguồn lên GitHub/GitLab.
2. Tạo project mới trên Vercel, chọn thư mục `frontend-nextjs`.
3. Khai báo biến môi trường:
   - `DATABASE_URL="mysql://root:Jacuby123@<public-host>:3306/graphdb"`
4. Khi build xong, Vercel sẽ gọi trực tiếp MySQL public của bạn. Đảm bảo mở firewall/SSL phù hợp hoặc dùng dịch vụ MySQL managed.

---

## 5. Quy trình sử dụng

1. **Import schema**: chạy `db/init.sql`.
2. **Khởi động backend**: `sbt run`, sau khi log “HTTP server started”, gọi `GET /pagerank` và `GET /ppr?nodeId=...` để sinh dữ liệu.
3. **Khởi động frontend**: `npm run dev` hoặc deploy Vercel.
4. **Sử dụng UI**:
   - Bảng PageRank liệt kê điểm số, có link tới sản phẩm.
   - Thanh tìm kiếm trả về danh sách đề xuất từ bảng `ppr`.
   - `/node/ID` giúp kiểm tra riêng từng nguồn PPR.

---

## 6. Chi tiết thuật toán PR/PPR

1. **Tiền xử lý**:
   - Hợp nhất `order_products__prior` + `order_products__train`.
   - Với mỗi `order_id`, chỉ giữ tối đa 40 sản phẩm đầu tiên để tránh nổ số cạnh.
   - Loại bỏ bản ghi thiếu dữ liệu.
2. **Xây đồ thị**:
   - Node: `product_id`, label lấy từ `products.csv`.
   - Cạnh: mọi cặp sản phẩm trong cùng order (tạo 2 cạnh hai chiều để GraphX đánh giá như đồ thị có hướng).
3. **PageRank**:
   - Sử dụng `staticPageRank(iterations, resetProb)` của GraphX.
   - Ghi đè toàn bộ bảng `pagerank`.
4. **Personalized PageRank**:
   - Dùng `staticPersonalizedPageRank(sourceId, iterations, resetProb)`.
   - Loại bỏ score = 0, xóa dữ liệu cũ của `source_id`, ghi mới vào bảng `ppr`.

---

## 7. Mô tả API frontend

| Endpoint | Mô tả |
|----------|-------|
| `GET /api/rank` | Trả về top PageRank (kết hợp bảng `pagerank` + `nodes`). |
| `GET /api/node/[id]` | Trả về danh sách PPR cho `source_id = id`. |
| `GET /api/search?term=...` | Tìm sản phẩm theo tên, sau đó trả về kết quả PPR tương ứng (nếu đã được backend tính). |

Các API này chỉ đọc MySQL, nên bạn có thể gọi từ Vercel mà không cần chạm vào Scala backend miễn sao DB public.

---

## 8. Gợi ý kiểm thử

1. Gọi `GET /pagerank` → vào MySQL kiểm tra bảng `pagerank` đã có >0 bản ghi.
2. Gọi `GET /ppr?nodeId=49302` → kiểm tra bảng `ppr` có `source_id = 49302`.
3. Mở `http://localhost:3000`:
   - Bảng PageRank hiển thị dữ liệu.
   - Tìm kiếm “Chocolate” → UI hiện danh sách sản phẩm liên quan.
   - Mở `http://localhost:3000/node/49302` xem đầy đủ PPR.

---

## 9. Troubleshooting

- **Backend báo lỗi không tìm thấy file CSV**: set `DATA_DIR` tới thư mục chứa 3 file (`products.csv`, ...). Mặc định là thư mục cha của `backend-graphx`.
- **Lỗi kết nối MySQL**:
  - Kiểm tra user/password (`root / ******`).
  - Bảo đảm MySQL bật `local_infile=ON` nếu dữ liệu lớn.
- **Frontend báo lỗi Prisma**:
  - Chạy `npx prisma generate`.
  - Kiểm tra `DATABASE_URL` trỏ đúng MySQL.
- **Kết quả trống trong UI**:
  - Chắc chắn đã gọi backend `/pagerank` và `/ppr`.
  - Kiểm tra bảng `ppr` xem có `source_id` tương ứng hay chưa.

---

## 10. Nâng cấp trong tương lai

- Cache kết quả GraphX để tránh chạy lại toàn bộ khi cần nhiều node PPR.
- Tạo scheduler (Airflow/Spark job) để chạy PR định kỳ.
- Bổ sung API ghi nhận thời điểm tính toán để frontend hiển thị “last updated”.
- Đưa MySQL + backend lên cloud (AWS RDS + EMR / Databricks) để đủ công suất cho dataset lớn hơn.

