# Service Auth - Evaluasi Monitoring dan Profiling

README ini dibuat untuk merangkum hasil pengerjaan _task_ terkait implementasi monitoring dan profiling pada modul `service-auth` sesuai dengan instruksi evaluasi.

## 1. Implementasi Monitoring

**Link Commit:**
[\[Monitoring\] Implementasi custom metrics dan justifikasi desain (1f6991f)](https://github.com/advprog-2026-A15-project/yomu-service-auth/commit/1f6991f)

### Justifikasi Desain Monitoring
Pada `service-auth`, saya menggunakan **Micrometer** dan **Spring Boot Actuator** untuk mengekspos metrik ke **Prometheus**, yang kemudian divisualisasikan oleh **Grafana**. Saya fokus memantau peristiwa keamanan (*security events*) seperti *login*, registrasi, dan insiden *rate-limiting*. Hal ini krusial mengingat modul ini bertanggung jawab atas gerbang masuk (autentikasi) seluruh pengguna.

Metrik kustom (*custom metrics*) yang saya buat sengaja menggunakan label (seperti `outcome` dan `provider`) agar kardinalitasnya tetap rendah, sehingga tidak membebani memori Prometheus. Contoh metrik yang saya tambahkan:
- `yomu_auth_login_total` (Counter): Menghitung percobaan *login*.
- `yomu_auth_register_total` (Counter): Menghitung percobaan registrasi.
- `yomu_auth_password_hashing_duration_seconds` (Timer): Mengukur durasi CPU saat melakukan *hashing* BCrypt.

### Potongan Kode Terkait
Berikut adalah potongan kode pada file `src/main/java/id/ac/ui/cs/advprog/yomu/auth/internal/monitoring/AuthMetrics.java` yang saya gunakan untuk mendefinisikan metrik:

```java
@Component
public class AuthMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter rateLimitHitsCounter;

    public AuthMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.rateLimitHitsCounter = Counter.builder("yomu_auth_rate_limit_hits_total")
                .description("Total number of requests blocked by rate limiting")
                .register(meterRegistry);
    }

    public void recordLogin(String outcome, String provider) {
        Counter.builder("yomu_auth_login_total")
                .description("Total number of login attempts")
                .tag("outcome", outcome)
                .tag("provider", provider)
                .register(meterRegistry)
                .increment();
    }
    
    // ... implementasi metrik lainnya ...
}
```

Dan cara saya memanggil metrik tersebut di dalam layanan, contohnya saat pengguna berhasil mendaftar di `AuthServiceImpl.java`:

```java
// AuthServiceImpl.java (Potongan)
@Override
@Transactional
public AuthResponse register(RegisterRequest request) {
    try {
        validateNewUser(request.getUsername(), request.getEmail());

        long start = System.nanoTime();
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        
        // Merekam waktu hashing password
        authMetrics.recordPasswordHashing(System.nanoTime() - start);

        // ... proses pembuatan user & simpan ke repo ...

        // Merekam metrik pendaftaran berhasil
        authMetrics.recordRegister("success");
        return buildAuthResponse(user);
    } catch (Exception e) {
        // Merekam metrik pendaftaran gagal
        authMetrics.recordRegister("failure");
        throw e;
    }
}
```

### Contoh Penggunaan (PromQL)
Jika saya ingin mengetahui tingkat kegagalan *login* selama 5 menit terakhir untuk mendeteksi ancaman *brute-force*, saya bisa menggunakan *query* berikut di Grafana/Prometheus:
```promql
rate(yomu_auth_login_total{outcome="failure"}[5m])
```
Atau untuk mengecek waktu rata-rata *hashing password*:
```promql
rate(yomu_auth_password_hashing_duration_seconds_sum[5m]) / rate(yomu_auth_password_hashing_duration_seconds_count[5m])
```

---

## 2. Implementasi Profiling

**Link Commit Bukti Profiling:**
[\[Profiling\] Tambahkan bukti profiling JFR dan analisis improvement (a7acda2)](https://github.com/advprog-2026-A15-project/yomu-service-auth/commit/a7acda2)

*(Bukti file rekaman asli dan snapshot terdapat di dalam direktori `profiling/` pada commit di atas).*

### Justifikasi Proses Profiling
Saya memilih menggunakan **Java Flight Recorder (JFR)** karena memiliki *overhead* yang sangat rendah (sekitar 1-2%) dan terintegrasi langsung dengan JVM. Untuk layanan autentikasi, saya secara khusus menyoroti performa komputasi CPU (saat melakukan hashing password dengan BCrypt) dan alokasi memori (saat membuat objek token JWT). 

Melalui JFR, saya menyimulasikan ratusan _request_ `POST /api/auth/login` dan `/register` untuk mendeteksi *hotspot* (titik yang memakan banyak waktu eksekusi).

### Analisis Improvement (Perbaikan)

Dari hasil *profiling*, saya menemukan beberapa insight:
1. **BCrypt Mendominasi CPU**: Sebanyak 65.2% sampel CPU tersedot oleh operasi `BCrypt.hashpw`. Karena *cost factor* saya saat ini adalah 10 (standar), latensi *login* mencapai rata-rata 450ms. Ini wajar dan bagus dari sisi keamanan untuk menghambat serangan _brute-force_.
2. **Kelemahan jika di-Scale**: Apabila nanti lalu lintas registrasi/login membludak, CPU akan menjadi *bottleneck* utama saya. 

**Tindakan Perbaikan (Improvement) yang Perlu Saya Lakukan:**
- **Database Indexing**: Metode `findByIdentifier` memakan 11.6% CPU karena melakukan pengecekan `username`, `email`, dan `phone`. Saya perlu memastikan bahwa *database* memiliki *index* spesifik di kolom-kolom tersebut agar pencarian tidak menjadi lambat seiring bertambahnya data.
- **Implementasi Caching (Redis)**: Saat layanan gRPC lain memanggil operasi verifikasi pengguna, itu akan melakukan *query* ke basis data. Jika *traffic* tinggi, saya sebaiknya menerapkan Redis Cache yang menyimpan profil pengguna agar tidak membebani CPU database berulang kali.
- **Pemindahan Rate Limiter**: Saat ini *filter* limitasi batas jumlah *request* masih berbasis *in-memory*. Saat saya meluncurkan *service* ganda, saya wajib memindahkannya ke Redis, agar serangan tidak bisa menjebol batas limitasi antar-server.

---

## 3. Link Deployment

Berikut adalah tautan deployment aplikasi `service-auth` yang telah saya lakukan:
- **Render (PaaS)**: [https://yomu-service-auth.onrender.com](https://yomu-service-auth.onrender.com)
- **AWS EC2 (IaaS)**: [http://34.201.205.93](http://34.201.205.93)
